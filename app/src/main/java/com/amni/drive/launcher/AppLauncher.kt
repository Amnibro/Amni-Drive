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
        val options = if (displayId != null && displayId >= 0) {
            ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 26) setLaunchDisplayId(displayId)
            }.toBundle()
        } else null
        return runCatching {
            context.startActivity(intent, options)
            Log.i(TAG, "launch ${entry.packageName} display=$displayId")
            true
        }.getOrElse {
            Log.w(TAG, "launch fail ${entry.packageName} display=$displayId ${it.message}")
            false
        }
    }
    companion object { private const val TAG = "Amni.Launch" }
}
