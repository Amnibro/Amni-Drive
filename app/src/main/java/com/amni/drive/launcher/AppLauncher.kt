package com.amni.drive.launcher
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
class AppLauncher(private val context: Context) {
    fun launch(entry: AppEntry, displayId: Int? = null): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val targetDisplayId = displayId?.takeIf { id ->
            if (id < 0) return@takeIf false
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            val d = dm?.getDisplay(id)
            d != null && (d.flags and android.view.Display.FLAG_PRIVATE) == 0
        }
        val options = if (targetDisplayId != null) {
            ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 26) setLaunchDisplayId(targetDisplayId)
            }.toBundle()
        } else null
        return runCatching {
            context.startActivity(intent, options)
            Log.i(TAG, "launch ${entry.packageName} display=$targetDisplayId (req=$displayId)")
            true
        }.getOrElse {
            Log.w(TAG, "launch fail ${entry.packageName} display=$targetDisplayId ${it.message}")
            false
        }
    }
    companion object { private const val TAG = "Amni.Launch" }
}
