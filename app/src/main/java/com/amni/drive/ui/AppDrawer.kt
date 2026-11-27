package com.amni.drive.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.amni.drive.config.DriveConfig
import com.amni.drive.display.DisplayRole
import com.amni.drive.gate.AppCategory
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateManager
import com.amni.drive.gate.GateResolver
import com.amni.drive.launcher.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private val FILTERS = listOf("ALL" to null, "NAV" to AppCategory.NAV, "MEDIA" to AppCategory.MEDIA, "VIDEO" to AppCategory.VIDEO, "GAMES" to AppCategory.GAME, "COMMS" to AppCategory.COMMS, "OTHER" to AppCategory.SAFE)
@Composable
fun AppDrawer(apps: List<AppEntry>, cfg: DriveConfig, state: DriveState, passenger: Boolean, tileScale: Int, onLaunch: (AppEntry) -> Unit) {
val p = LocalAmni.current
val ctx = LocalContext.current
var filter by remember { mutableStateOf<AppCategory?>(null) }
var query by remember { mutableStateOf("") }
var showKb by remember { mutableStateOf(false) }
var voiceStatus by remember { mutableStateOf<String?>(null) }
val catShown = if (filter == null) apps else apps.filter { it.category == filter || (filter == AppCategory.SAFE && it.category == AppCategory.INTERACTIVE) }
val shown = if (query.isBlank()) catShown else catShown.filter { it.label.contains(query.trim(), ignoreCase = true) || it.packageName.contains(query.trim(), ignoreCase = true) }
val cell = when (tileScale) { 0 -> 97.dp; 2 -> 137.dp; else -> 114.dp }
Box(Modifier.fillMaxSize()) {
Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
Row(
Modifier.fillMaxWidth().padding(vertical = 5.dp).then(Modifier),
horizontalArrangement = Arrangement.spacedBy(7.dp),
verticalAlignment = Alignment.CenterVertically
) {
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (showKb || query.isNotBlank()) p.acc else p.panel2)
.border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
.clickable { showKb = !showKb }
.padding(horizontal = 12.dp, vertical = 7.dp)
) {
Text(
if (query.isBlank()) "🔎 SEARCH" else "🔎 ${query.take(14)}",
color = if (showKb || query.isNotBlank()) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.acc,
fontFamily = FontFamily.Monospace,
fontSize = 11.sp,
fontWeight = FontWeight.Bold
)
}
if (query.isNotBlank()) {
Box(
Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp))
.clickable { query = ""; showKb = false }.padding(horizontal = 9.dp, vertical = 7.dp)
) {
Text("✕", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}
}
Row(
Modifier.weight(1f).horizontalScroll(rememberScrollState()),
horizontalArrangement = Arrangement.spacedBy(7.dp),
verticalAlignment = Alignment.CenterVertically
) {
FILTERS.forEach { (label, cat) ->
val sel = filter == cat
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (sel) Brush.horizontalGradient(listOf(p.acc.copy(alpha = 0.24f), p.acc.copy(alpha = 0.10f))) else Brush.horizontalGradient(listOf(p.panel.copy(alpha = 0.8f), p.panel.copy(alpha = 0.8f))))
.border(1.dp, if (sel) p.acc.copy(alpha = 0.6f) else p.hairline, RoundedCornerShape(99.dp))
.clickable { filter = cat }
.padding(horizontal = 13.dp, vertical = 7.dp)
) {
Text(label, color = if (sel) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}
}
}
}
LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
items(shown, key = { it.packageName }) { app -> AppCell(app, isGated(app, cfg, state, passenger), cell) { showKb = false; onLaunch(app) } }
}
}
if (showKb) {
Box(Modifier.fillMaxSize().clickable { showKb = false }, contentAlignment = Alignment.BottomCenter) {
CarTouchKeyboard(
preview = query,
voiceStatus = voiceStatus,
placeholder = "Filter installed apps…",
enterLabel = if (shown.size == 1) "OPEN ↵" else "DONE ↵",
onText = { query = (query + it).take(32) },
onBackspace = { query = query.dropLast(1) },
onClear = { query = "" },
onEnter = {
if (shown.size == 1) { val pick = shown.first(); showKb = false; onLaunch(pick) }
else showKb = false
},
onVoice = {
com.amni.drive.voice.VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
if (!spoken.isNullOrBlank()) { query = spoken.trim(); voiceStatus = null; showKb = false }
})
},
onClose = { showKb = false }
)
}
}
}
}
private fun isGated(app: AppEntry, cfg: DriveConfig, state: DriveState, passenger: Boolean): Boolean =
!GateManager.decide(GateResolver.input(app.category, DisplayRole.PRIMARY_DRIVER, state, cfg, passenger)).allow
@Composable
private fun AppCell(app: AppEntry, gated: Boolean, cell: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
val p = LocalAmni.current
val icon = rememberAppIcon(app.packageName)
val shape = RoundedCornerShape(20.dp)
Column(
Modifier.shadow(6.dp, shape, ambientColor = p.acc.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.35f))
.clip(shape)
.background(p.surfaceBrush, shape)
.border(1.dp, p.hairline, shape)
.clickable { onClick() }
.padding(10.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.spacedBy(8.dp)
) {
Box {
Box(
Modifier.size(cell - 38.dp).clip(RoundedCornerShape(18.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(18.dp)),
contentAlignment = Alignment.Center
) {
if (icon != null) Image(icon, app.label, Modifier.size(cell - 52.dp).clip(RoundedCornerShape(14.dp)))
else Text(app.label.take(1).uppercase(), color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}
if (gated) Box(
Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape)
.background(if (p.isLight) Color.White else Color(0xFF0A0B0E))
.border(1.dp, p.err.copy(alpha = 0.65f), CircleShape),
contentAlignment = Alignment.Center
) { Icon(Icons.Rounded.Lock, "gated", Modifier.size(13.dp), tint = p.err) }
}
Text(app.label, color = p.soft, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
}
@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
val ctx = LocalContext.current
return produceState<ImageBitmap?>(null, pkg) {
value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
}.value
}
