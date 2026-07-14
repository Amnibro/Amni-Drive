package com.amni.drive.wire
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
object CarLink {
private val typeFlow = MutableStateFlow(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
val type: StateFlow<Int> = typeFlow
val projecting: Boolean get() = typeFlow.value == CarConnection.CONNECTION_TYPE_PROJECTION
val connected: Boolean get() = typeFlow.value != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
private var started = false
fun start(ctx: Context) {
if (started) return
started = true
val app = ctx.applicationContext
Handler(Looper.getMainLooper()).post {
runCatching { CarConnection(app).type.observeForever { typeFlow.value = it ?: CarConnection.CONNECTION_TYPE_NOT_CONNECTED } }
}
}
}
