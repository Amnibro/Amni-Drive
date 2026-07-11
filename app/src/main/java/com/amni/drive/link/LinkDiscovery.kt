package com.amni.drive.link
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
class LinkDiscovery(context: Context) {
private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
private var reg: NsdManager.RegistrationListener? = null
private var disc: NsdManager.DiscoveryListener? = null
data class Peer(val name: String, val host: String, val port: Int)
fun advertise(onStatus: (String) -> Unit = {}) {
val info = NsdServiceInfo().apply {
serviceName = "${LinkProto.SERVICE_NAME}-${Build.MODEL}".take(40)
serviceType = LinkProto.SERVICE_TYPE
port = LinkProto.PORT
}
val listener = object : NsdManager.RegistrationListener {
override fun onServiceRegistered(s: NsdServiceInfo) { onStatus("NSD · advertising ${s.serviceName}") }
override fun onRegistrationFailed(s: NsdServiceInfo, c: Int) { onStatus("NSD · advertise fail $c") }
override fun onServiceUnregistered(s: NsdServiceInfo) { onStatus("NSD · unregistered") }
override fun onUnregistrationFailed(s: NsdServiceInfo, c: Int) {}
}
reg = listener
try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) } catch (e: Exception) { onStatus("NSD · ${e.message}") }
}
fun browse(onPeer: (Peer) -> Unit, onStatus: (String) -> Unit = {}) {
val listener = object : NsdManager.DiscoveryListener {
override fun onDiscoveryStarted(r: String) { onStatus("NSD · browsing") }
override fun onServiceFound(service: NsdServiceInfo) {
if (!service.serviceType.contains("amni-link")) return
nsd.resolveService(service, object : NsdManager.ResolveListener {
override fun onResolveFailed(s: NsdServiceInfo, c: Int) { onStatus("NSD · resolve fail $c") }
override fun onServiceResolved(s: NsdServiceInfo) {
val host = s.host?.hostAddress ?: return
onPeer(Peer(s.serviceName, host, s.port))
onStatus("NSD · found ${s.serviceName} @ $host:${s.port}")
}
})
}
override fun onServiceLost(s: NsdServiceInfo) { onStatus("NSD · lost ${s.serviceName}") }
override fun onDiscoveryStopped(r: String) {}
override fun onStartDiscoveryFailed(r: String, c: Int) { onStatus("NSD · browse fail $c") }
override fun onStopDiscoveryFailed(r: String, c: Int) {}
}
disc = listener
try { nsd.discoverServices(LinkProto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) } catch (e: Exception) { onStatus("NSD · ${e.message}") }
}
fun stop() {
reg?.let { try { nsd.unregisterService(it) } catch (_: Exception) {} }
disc?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
reg = null; disc = null
}
}
