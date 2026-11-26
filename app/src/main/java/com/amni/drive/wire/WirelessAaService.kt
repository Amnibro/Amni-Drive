package com.amni.drive.wire

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Wireless Android Auto host service:
 *  1. Listens on the standard AA Wireless Bluetooth RFCOMM UUID
 *     (`4de17a00-52cb-11e6-bdf4-0800200c9a66`), handles WifiVersion / WifiInfo / WifiStart,
 *     and auto-joins the car's 5 GHz Wi-Fi AP via Android 10+ [WifiNetworkSpecifier] (leaving
 *     mobile data active for Amni-Map, Spotify, YouTube, etc.).
 *  2. Listens on TCP :5277 for head units / wireless dongles (AAWireless, MA1, Carsifi) that
 *     connect back to the phone.
 *  3. Supports direct TCP client connect (`ACTION_CONNECT_TCP`) to any car/dongle/DHU IP:5277.
 */
class WirelessAaService : Service() {
    data class WirelessState(
        val running: Boolean = false,
        val phase: String = "Stopped",
        val peer: String = "",
        val ssid: String = "",
        val lastLog: String = ""
    )

    private val running = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)
    private val activeIo = AtomicReference<TcpAaIo?>(null)
    private val rfcommServer = AtomicReference<BluetoothServerSocket?>(null)
    private val tcpServer = AtomicReference<ServerSocket?>(null)
    private val wifiCallback = AtomicReference<ConnectivityManager.NetworkCallback?>(null)
    private val boundNetwork = AtomicReference<Network?>(null)

    private var rfcommThread: Thread? = null
    private var tcpAcceptThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotif("Wireless AA ready (:5277 + BT)"))
        running.set(true)
        updateState("Listening (BT RFCOMM + TCP :5277)")
        startRfcommListener()
        startTcpServerListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT_TCP -> {
                val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
                val port = intent.getIntExtra(EXTRA_PORT, WirelessAaProto.DEFAULT_TCP_PORT)
                if (host.isNotEmpty()) {
                    Thread({ connectTcpAndRun(host, port, boundNetwork.get()) }, "waa-direct-tcp").also {
                        it.isDaemon = true
                        it.start()
                    }
                }
            }
            else -> {
                if (rfcommThread == null || rfcommThread?.isAlive != true) startRfcommListener()
                if (tcpAcceptThread == null || tcpAcceptThread?.isAlive != true) startTcpServerListener()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        runCatching { activeIo.getAndSet(null)?.close() }
        runCatching { rfcommServer.getAndSet(null)?.close() }
        runCatching { tcpServer.getAndSet(null)?.close() }
        releaseWifiNetwork()
        rfcommThread?.interrupt()
        tcpAcceptThread?.interrupt()
        _state.value = WirelessState(running = false, phase = "Stopped")
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startRfcommListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            log("BT RFCOMM skipped (BLUETOOTH_CONNECT not granted yet — TCP :5277 still active)")
            return
        }
        rfcommThread = Thread({
            val btMgr = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = btMgr?.adapter
            if (adapter == null || !adapter.isEnabled) {
                log("Bluetooth adapter off — waiting on TCP :5277")
                return@Thread
            }
            while (running.get()) {
                var srv: BluetoothServerSocket? = null
                try {
                    srv = adapter.listenUsingRfcommWithServiceRecord("Android Auto", WirelessAaProto.AA_WIRELESS_UUID)
                    rfcommServer.set(srv)
                    log("RFCOMM listening on AA Wireless UUID")
                    val sock = srv.accept()
                    runCatching { srv.close() }
                    rfcommServer.set(null)
                    if (sock != null && running.get()) {
                        handleRfcommClient(sock)
                    }
                } catch (e: Exception) {
                    runCatching { srv?.close() }
                    rfcommServer.set(null)
                    if (!running.get()) break
                    log("RFCOMM listen err: ${e.message}")
                    try { Thread.sleep(3_000L) } catch (_: InterruptedException) { break }
                }
            }
        }, "waa-rfcomm").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun handleRfcommClient(sock: BluetoothSocket) {
        val peerName = runCatching { sock.remoteDevice?.address ?: "car-bt" }.getOrDefault("car-bt")
        updateState("BT connected ($peerName)", peer = peerName)
        log("RFCOMM peer connected: $peerName")
        try {
            val inp = sock.inputStream
            val out = sock.outputStream
            var targetIp = ""
            var targetPort = WirelessAaProto.DEFAULT_TCP_PORT

            // Request HU Wi-Fi AP credentials proactively right after peer attaches
            WirelessAaProto.writeFrame(out, WirelessAaProto.MSG_WIFI_INFO_REQUEST)
            log("RFCOMM TX WifiInfoRequest")

            val deadline = System.currentTimeMillis() + 25_000L
            while (running.get() && System.currentTimeMillis() < deadline) {
                val frame = WirelessAaProto.readFrame(inp) ?: break
                log("RFCOMM RX msg=${frame.msgId} len=${frame.payload.size}")
                when (frame.msgId) {
                    WirelessAaProto.MSG_WIFI_VERSION_REQUEST -> {
                        val ver = WirelessAaProto.parseWifiVersion(frame.payload)
                        WirelessAaProto.writeFrame(
                            out,
                            WirelessAaProto.MSG_WIFI_VERSION_RESPONSE,
                            WirelessAaProto.encodeWifiVersionResponse(ver.major, ver.minor, 0)
                        )
                        log("RFCOMM TX WifiVersionResponse ${ver.major}.${ver.minor}")
                    }
                    WirelessAaProto.MSG_WIFI_START_REQUEST -> {
                        val req = WirelessAaProto.parseWifiStartRequest(frame.payload)
                        targetIp = req.ipAddress
                        targetPort = req.port
                        val localIp = findLocalWifiIpv4()
                        WirelessAaProto.writeFrame(
                            out,
                            WirelessAaProto.MSG_WIFI_START_RESPONSE,
                            WirelessAaProto.encodeWifiStartResponse(localIp.ifEmpty { targetIp }, targetPort, 0)
                        )
                        log("RFCOMM RX WifiStartRequest ip=$targetIp port=$targetPort")
                        if (targetIp.isNotEmpty() && boundNetwork.get() != null) {
                            launchTcpClient(targetIp, targetPort, boundNetwork.get())
                        }
                    }
                    WirelessAaProto.MSG_WIFI_INFO_RESPONSE -> {
                        val info = WirelessAaProto.parseWifiInfoResponse(frame.payload)
                        log("RFCOMM RX WifiInfoResponse ssid=${info.ssid} sec=${info.securityMode} bssid=${info.bssid}")
                        updateState("Joining car Wi-Fi: ${info.ssid}", peer = peerName, ssid = info.ssid)
                        val net = joinCarWifi(info)
                        val status = if (net != null) 0 else -1
                        WirelessAaProto.writeFrame(out, WirelessAaProto.MSG_WIFI_CONNECT_STATUS, WirelessAaProto.encodeWifiConnectStatus(status))
                        log("RFCOMM TX WifiConnectStatus=$status")
                        if (net != null) {
                            val localIp = findLocalWifiIpv4()
                            if (localIp.isNotEmpty()) {
                                WirelessAaProto.writeFrame(
                                    out,
                                    WirelessAaProto.MSG_WIFI_START_REQUEST,
                                    WirelessAaProto.encodeWifiStartRequest(localIp, WirelessAaProto.DEFAULT_TCP_PORT)
                                )
                                log("RFCOMM TX WifiStartRequest phoneIp=$localIp:5277")
                            }
                            if (targetIp.isNotEmpty()) {
                                launchTcpClient(targetIp, targetPort, net)
                            }
                            break
                        }
                    }
                    WirelessAaProto.MSG_WIFI_START_RESPONSE -> {
                        val req = WirelessAaProto.parseWifiStartRequest(frame.payload)
                        if (req.ipAddress.isNotEmpty()) {
                            launchTcpClient(req.ipAddress, req.port, boundNetwork.get())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log("RFCOMM session err: ${e.message}")
        } finally {
            runCatching { sock.close() }
        }
    }

    private fun joinCarWifi(info: WirelessAaProto.WifiInfoResponse): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.ssid.isBlank()) return null
        releaseWifiNetwork()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = WifiNetworkSpecifier.Builder().setSsid(info.ssid)
        if (info.key.isNotEmpty()) {
            if (info.securityMode == WirelessAaProto.SEC_WPA3_PERSONAL) {
                builder.setWpa3Passphrase(info.key)
            } else {
                builder.setWpa2Passphrase(info.key)
            }
        }
        if (info.bssid.isNotBlank()) {
            runCatching { builder.setBssid(MacAddress.fromString(info.bssid)) }
        }
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(builder.build())
            .build()

        val latch = CountDownLatch(1)
        val result = AtomicReference<Network?>(null)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                result.set(network)
                boundNetwork.set(network)
                latch.countDown()
            }
            override fun onUnavailable() {
                latch.countDown()
            }
            override fun onLost(network: Network) {
                if (boundNetwork.compareAndSet(network, null)) {
                    log("Car Wi-Fi AP lost")
                }
            }
        }
        wifiCallback.set(cb)
        return try {
            cm.requestNetwork(req, cb)
            latch.await(15, TimeUnit.SECONDS)
            result.get()
        } catch (e: Exception) {
            log("WifiNetworkSpecifier join err: ${e.message}")
            null
        }
    }

    private fun releaseWifiNetwork() {
        val cb = wifiCallback.getAndSet(null) ?: return
        boundNetwork.set(null)
        runCatching {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        }
    }

    private fun startTcpServerListener() {
        tcpAcceptThread = Thread({
            while (running.get()) {
                var srv: ServerSocket? = null
                try {
                    srv = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(WirelessAaProto.DEFAULT_TCP_PORT))
                    }
                    tcpServer.set(srv)
                    log("Wireless AA TCP server listening on :${WirelessAaProto.DEFAULT_TCP_PORT}")
                    while (running.get() && !srv.isClosed) {
                        val sock = srv.accept()
                        val remote = sock.remoteSocketAddress?.toString().orEmpty()
                        log("Wireless AA TCP inbound from $remote")
                        if (!sessionActive.get()) {
                            Thread({ runTcpSession(sock, "WAA-IN($remote)") }, "waa-session-in").also {
                                it.isDaemon = true
                                it.start()
                            }
                        } else {
                            log("Wireless AA already active — closing duplicate inbound $remote")
                            runCatching { sock.close() }
                        }
                    }
                } catch (e: Exception) {
                    if (!running.get()) break
                    log("TCP :5277 server err: ${e.message}")
                    try { Thread.sleep(2_000L) } catch (_: InterruptedException) { break }
                } finally {
                    runCatching { srv?.close() }
                    tcpServer.set(null)
                }
            }
        }, "waa-tcp-accept").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun launchTcpClient(host: String, port: Int, net: Network?) {
        Thread({ connectTcpAndRun(host, port, net) }, "waa-tcp-client").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun connectTcpAndRun(host: String, port: Int, net: Network?) {
        if (sessionActive.get()) return
        updateState("Connecting TCP $host:$port…", peer = "$host:$port")
        log("Wireless AA TCP connect -> $host:$port (boundNet=${net != null})")
        var sock: Socket? = null
        try {
            sock = net?.socketFactory?.createSocket() ?: Socket()
            sock.connect(InetSocketAddress(host, port), 8_000)
            runTcpSession(sock, "WAA-OUT($host:$port)")
        } catch (e: Exception) {
            runCatching { sock?.close() }
            log("TCP connect $host:$port failed: ${e.message}")
            updateState("Connect failed: ${e.message}")
        }
    }

    private fun runTcpSession(sock: Socket, tag: String) {
        if (!sessionActive.compareAndSet(false, true)) {
            runCatching { sock.close() }
            return
        }
        val io = TcpAaIo(sock) { log(it) }
        activeIo.set(io)
        try {
            val summary = AaWireRunner.runSession(
                ctx = this,
                io = io,
                label = tag,
                onStatus = { st ->
                    updateState(st, peer = tag)
                    updateNotif(st)
                },
                log = { log(it) }
            )
            updateState("Session ended (rx=${summary.rxFrames}) · listening", peer = "")
            updateNotif("Wireless AA listening (:5277 + BT)")
        } finally {
            activeIo.compareAndSet(io, null)
            io.close()
            sessionActive.set(false)
        }
    }

    private fun findLocalWifiIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("p2p")) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress.orEmpty()
    }.getOrDefault("")

    private fun log(msg: String) {
        Log.i(TAG, msg)
        _state.value = _state.value.copy(lastLog = msg)
    }

    private fun updateState(phase: String, peer: String = _state.value.peer, ssid: String = _state.value.ssid) {
        _state.value = _state.value.copy(running = running.get(), phase = phase, peer = peer, ssid = ssid)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Amni Wire Wireless AA", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotif(text: String): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Amni-Drive Wireless AA")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotif(text))
        }
    }

    companion object {
        const val TAG = "Amni.WirelessAA"
        const val CHANNEL = "amni_wire_wireless"
        const val NOTIF_ID = 7735
        const val ACTION_START = "com.amni.drive.wire.WAA_START"
        const val ACTION_STOP = "com.amni.drive.wire.WAA_STOP"
        const val ACTION_CONNECT_TCP = "com.amni.drive.wire.WAA_CONNECT_TCP"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"

        private val _state = MutableStateFlow(WirelessState())
        val state: StateFlow<WirelessState> = _state

        fun start(ctx: Context) {
            val i = Intent(ctx, WirelessAaService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, WirelessAaService::class.java).setAction(ACTION_STOP)) }
        }

        fun connectTcp(ctx: Context, host: String, port: Int = WirelessAaProto.DEFAULT_TCP_PORT) {
            val i = Intent(ctx, WirelessAaService::class.java).apply {
                action = ACTION_CONNECT_TCP
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
