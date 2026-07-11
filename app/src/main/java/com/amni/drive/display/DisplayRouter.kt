package com.amni.drive.display
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
class DisplayRouter(context: Context) {
    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    fun displays(): List<DisplayInfo> = dm.displays.map { DisplayInfo(it.displayId, roleFor(it), it.name ?: "display-${it.displayId}") }
    fun rearDisplayId(): Int? = displays().firstOrNull { it.role == DisplayRole.REAR_PASSENGER }?.displayId
    private fun roleFor(d: Display): DisplayRole = when {
        d.displayId == Display.DEFAULT_DISPLAY -> DisplayRole.PRIMARY_DRIVER
        (d.flags and Display.FLAG_PRESENTATION) != 0 -> DisplayRole.REAR_PASSENGER
        else -> DisplayRole.REAR_PASSENGER
    }
}
