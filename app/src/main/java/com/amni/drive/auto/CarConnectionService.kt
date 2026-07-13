package com.amni.drive.auto
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.UiModeManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import com.amni.drive.AmniDriveApp
import com.amni.drive.MainActivity
import com.amni.drive.R
import com.amni.drive.config.DriveConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
class CarConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var cfg = DriveConfig()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> if (cfg.autoStartOnPower) launchDrive("power")
                Intent.ACTION_POWER_DISCONNECTED -> onDisconnect()
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val mac = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.address.orEmpty()
                    if (cfg.autoStartOnBluetooth && (cfg.carBtMac.isBlank() || mac.equals(cfg.carBtMac, true))) launchDrive("bt")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val mac = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.address.orEmpty()
                    if (cfg.autoStartOnBluetooth && (cfg.carBtMac.isBlank() || mac.equals(cfg.carBtMac, true))) onDisconnect()
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        startForeground(NOTE_IDLE, idleNote())
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, f)
        scope.launch { (application as AmniDriveApp).configStore.config.collectLatest { cfg = it } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { runCatching { unregisterReceiver(receiver) }; scope.cancel(); super.onDestroy() }
    private fun launchDrive(via: String) {
        if (cfg.autoEnterCarMode) runCatching { (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).enableCarMode(0) }
        val nav = MainActivity.openNavIntent(this)
        runCatching { startActivity(nav) }
        val pi = PendingIntent.getActivity(this, 42, nav, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CH_LAUNCH)
            .setSmallIcon(R.drawable.ic_amni_drive)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Entering drive mode ($via)")
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_LAUNCH, note)
    }
    private fun onDisconnect() {
        if (cfg.autoEnterCarMode) runCatching { (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).disableCarMode(0) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTE_LAUNCH)
    }
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_IDLE, "Car connect watch", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_LAUNCH, "Drive mode launch", NotificationManager.IMPORTANCE_HIGH))
    }
    private fun idleNote(): Notification = Notification.Builder(this, CH_IDLE)
        .setSmallIcon(R.drawable.ic_amni_drive)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Ready — starts on car connect")
        .setOngoing(true)
        .build()
    companion object {
        private const val CH_IDLE = "amni_car_idle"
        private const val CH_LAUNCH = "amni_car_launch"
        private const val NOTE_IDLE = 4201
        private const val NOTE_LAUNCH = 4202
        fun start(c: Context) = runCatching {
            val i = Intent(c, CarConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }
    }
}
