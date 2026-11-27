package com.amni.drive.launcher
import android.content.Context
import android.content.Intent
import com.amni.drive.gate.AppCategory
import com.amni.drive.media.MediaBrowserHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class AppInventory(private val context: Context) {
    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val audioPkgs = MediaBrowserHub.discoverInstalledAudioPackages(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launched = pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            val pkg = ai.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val system = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(-1)
            val cat = if (pkg in audioPkgs) AppCategory.MEDIA else AppClassifier.classify(pkg, system)
            AppEntry(pkg, ai.name, ri.loadLabel(pm).toString(), cat)
        }
        val knownPkgs = launched.map { it.packageName }.toSet()
        val extraAudio = audioPkgs.filter { it !in knownPkgs && it != context.packageName }.mapNotNull { pkg ->
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg.substringAfterLast('.'))
            val actName = pm.getLaunchIntentForPackage(pkg)?.component?.className.orEmpty()
            AppEntry(pkg, actName, label, AppCategory.MEDIA)
        }
        (launched + extraAudio).distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
