package com.amni.drive.launcher
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class AppInventory(private val context: Context) {
    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            val pkg = ai.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val system = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(-1)
            AppEntry(pkg, ai.name, ri.loadLabel(pm).toString(), AppClassifier.classify(pkg, system))
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
