package com.amni.drive.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
var filter by remember { mutableStateOf<AppCategory?>(null) }
val shown = if (filter == null) apps else apps.filter { it.category == filter || (filter == AppCategory.SAFE && it.category == AppCategory.INTERACTIVE) }
val cell = when (tileScale) { 0 -> 100.dp; 2 -> 140.dp; else -> 118.dp }
Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
FILTERS.forEach { (label, cat) ->
val sel = filter == cat
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (sel) Brush.horizontalGradient(listOf(p.acc.copy(alpha = 0.22f), p.acc.copy(alpha = 0.08f))) else Brush.horizontalGradient(listOf(p.panel.copy(alpha = 0.7f), p.panel.copy(alpha = 0.7f))))
.border(1.dp, if (sel) p.acc.copy(alpha = 0.5f) else p.hairline, RoundedCornerShape(99.dp))
.clickable { filter = cat }
.padding(horizontal = 16.dp, vertical = 10.dp)
) {
Text(label, color = if (sel) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
}
Box(Modifier.weight(1f))
val gatedCount = shown.count { isGated(it, cfg, state, passenger) }
if (gatedCount > 0) Text("$gatedCount gated — tap to send rear", color = p.dim, fontSize = 11.sp)
}
LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
items(shown, key = { it.packageName }) { app -> AppCell(app, isGated(app, cfg, state, passenger), cell) { onLaunch(app) } }
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
