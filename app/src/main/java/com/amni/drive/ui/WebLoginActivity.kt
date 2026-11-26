package com.amni.drive.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amni.drive.AmniDriveApp

/**
 * Phone-side WebView sign-in activity sharing the exact same CookieManager and WebView storage
 * as the car display's EmbeddedWebMediaTile. Sign in once on the phone using full keyboard &
 * password autofill, and the car display stays logged in automatically.
 */
class WebLoginActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_LABEL = "label"

        fun cleanChromeUserAgent(raw: String): String =
            raw.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")

        fun open(ctx: Context, url: String, label: String): Boolean {
            val appCtx = ctx.applicationContext
            val intent = Intent(appCtx, WebLoginActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val opts = android.app.ActivityOptions.makeBasic()
                        .setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY)
                        .toBundle()
                    appCtx.startActivity(intent, opts)
                } else {
                    appCtx.startActivity(intent)
                }
                true
            }.onFailure { err ->
                android.util.Log.w("Amni.WebLogin", "open on phone failed: ${err.message}")
            }.getOrDefault(false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra(EXTRA_URL) ?: "https://m.youtube.com"
        val startLabel = intent.getStringExtra(EXTRA_LABEL) ?: "Car Web Sign-In"
        val app = application as AmniDriveApp
        setContent {
            val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
            AmniDriveTheme(rememberPalette(theme)) {
                val p = LocalAmni.current
                var webRef by remember { mutableStateOf<WebView?>(null) }
                var title by remember { mutableStateOf(startLabel) }
                Column(Modifier.fillMaxSize().background(p.bg)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(p.panel)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.panel2)
                                .border(1.dp, p.hairline, RoundedCornerShape(10.dp))
                                .clickable { if (webRef?.canGoBack() == true) webRef?.goBack() else finish() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("◀", color = p.ink, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) {
                            Text(title, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Shared with Car Display · sign in here once", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.acc)
                                .clickable {
                                    runCatching { CookieManager.getInstance().flush() }
                                    finish()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "SAVE & SYNC ✓",
                                color = if (p.isLight) Color.White else Color(0xFF05070A),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.userAgentString = cleanChromeUserAgent(settings.userAgentString)
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        runCatching { CookieManager.getInstance().flush() }
                                        view?.title?.takeIf { it.isNotBlank() }?.let { title = it }
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl(startUrl)
                                webRef = this
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        runCatching { CookieManager.getInstance().flush() }
        super.onPause()
    }
}
