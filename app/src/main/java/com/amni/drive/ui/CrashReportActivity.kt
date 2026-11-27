package com.amni.drive.ui
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.MainActivity
import com.amni.drive.diag.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
class CrashReportActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "report_path"
        const val EXTRA_CRASH = "is_crash"
        fun openBugReporter(ctx: Context) {
            val i = Intent(ctx.applicationContext, CrashReportActivity::class.java)
                .putExtra(EXTRA_CRASH, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.applicationContext.startActivity(i) }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isCrash = intent.getBooleanExtra(EXTRA_CRASH, false)
        val existingPath = intent.getStringExtra(EXTRA_PATH)
        setContent {
            AmniDriveTheme(paletteFor(AmniPreset.AMNI_DARK, null)) {
                val p = LocalAmni.current
                val scope = rememberCoroutineScope()
                var note by remember { mutableStateOf("") }
                var reportFile by remember { mutableStateOf(existingPath?.let(::File)?.takeIf { it.exists() }) }
                var preview by remember { mutableStateOf("") }
                var uploaded by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(reportFile == null) }
                LaunchedEffect(Unit) {
                    val f = withContext(Dispatchers.IO) {
                        reportFile ?: CrashReporter.captureReport(applicationContext, if (isCrash) "CRASH" else "BUG_REPORT")
                    }
                    reportFile = f
                    preview = withContext(Dispatchers.IO) { runCatching { f.readText() }.getOrDefault("") }
                    busy = false
                    if (isCrash && !CrashReporter.isUploaded(applicationContext, f)) {
                        val ok = withContext(Dispatchers.IO) {
                            CrashReporter.uploadGatewaySummary(CrashReporter.buildGatewaySummary("CRASH", null, f))
                        }
                        if (ok) {
                            CrashReporter.markUploaded(applicationContext, f)
                            uploaded = true
                        }
                    } else if (isCrash) {
                        uploaded = true
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(p.bg)
                        .padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (isCrash) "⚠ AMNI-DRIVE CRASH CAPTURED" else "🐞 REPORT BUG · SCRUBBED SESSION LOG",
                        color = if (isCrash) p.gold else p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Personal data (GPS coordinates, destinations, search queries, emails, phone numbers, MACs, SSIDs & tokens) is stripped before saving or sending to ${CrashReporter.SUPPORT_EMAIL}.",
                        color = p.soft,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    if (uploaded) {
                        Text(
                            "✓ Scrubbed crash summary uploaded automatically · tap below to email full log attachment to ${CrashReporter.SUPPORT_EMAIL}",
                            color = p.acc,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(700) },
                        textStyle = TextStyle(color = p.ink, fontSize = 13.sp),
                        cursorBrush = SolidColor(p.acc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.panel2)
                            .border(1.dp, p.line, RoundedCornerShape(12.dp))
                            .padding(11.dp),
                        decorationBox = { inner ->
                            Box {
                                if (note.isEmpty()) Text("Optional: what happened? (e.g. plugged into Honda USB, tapped Spotify…)", color = p.dim, fontSize = 12.sp)
                                inner()
                            }
                        }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (busy) p.panel2 else p.acc)
                                .clickable(enabled = !busy) {
                                    scope.launch {
                                        val finalFile = withContext(Dispatchers.IO) {
                                            val cur = reportFile
                                            if (!isCrash && note.isNotBlank()) CrashReporter.captureReport(applicationContext, "BUG_REPORT", note)
                                            else cur ?: CrashReporter.captureReport(applicationContext, "BUG_REPORT", note)
                                        }
                                        withContext(Dispatchers.IO) {
                                            CrashReporter.uploadGatewaySummary(CrashReporter.buildGatewaySummary(if (isCrash) "CRASH" else "BUG", null, finalFile, note))
                                        }
                                        CrashReporter.emailReport(this@CrashReportActivity, finalFile, isCrash, note)
                                        finish()
                                    }
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✉ SEND TO ANTHONY@AMNI-SCIENT.COM",
                                color = Color(0xFF05070A),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isCrash) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.panel2)
                                    .border(1.dp, p.acc, RoundedCornerShape(12.dp))
                                    .clickable {
                                        CrashReporter.dismissUnsent(applicationContext)
                                        startActivity(Intent(this@CrashReportActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                                        finish()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("REOPEN", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.panel2)
                                    .border(1.dp, p.line, RoundedCornerShape(12.dp))
                                    .clickable { finish() }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CLOSE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("SCRUBBED LOG PREVIEW", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.panel)
                            .border(1.dp, p.line, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            if (busy) "Collecting & scrubbing session logs…" else preview.take(9000),
                            color = p.soft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
