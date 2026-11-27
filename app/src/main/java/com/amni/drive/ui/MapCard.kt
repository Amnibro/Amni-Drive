package com.amni.drive.ui
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.aa.AaNavState
import com.amni.drive.map.AmniMap
import com.amni.drive.map.Geocoder
import com.amni.drive.map.MapCamera
import com.amni.drive.map.Place
import com.amni.drive.map.Report
import com.amni.drive.map.ReportStore
import com.amni.drive.map.RoutePoint
import com.amni.drive.map.RouteResult
import com.amni.drive.map.TileStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
@Composable
fun MapCard(
    modifier: Modifier,
    camera: MapCamera,
    tiles: TileStore,
    route: RouteResult?,
    reports: List<Report>,
    stops: List<RoutePoint>,
    location: Location?,
    routing: Boolean,
    fullNav: Boolean,
    netOk: Boolean,
    showLayers: Boolean,
    showReport: Boolean,
    onShowLayers: (Boolean) -> Unit,
    onShowReport: (Boolean) -> Unit,
    reportStore: ReportStore,
    onLongPress: (Double, Double) -> Unit,
    onGo: (Place) -> Unit,
    onAddStop: (Place) -> Unit,
    onEndRoute: () -> Unit,
    dense: Boolean = false,
    hostKeyboard: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    circular: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    val p = LocalAmni.current
    var searchOpen by remember { mutableStateOf(false) }
    val searchPulse by AaNavState.searchPulse.collectAsState()
    LaunchedEffect(searchPulse) {
        if (searchPulse > 0L) {
            searchOpen = true
            onShowLayers(false)
            onShowReport(false)
        }
    }
    val chromeBg = if (p.isLight) Color(0xF5FFFFFF) else Color(0xF012151C)
    val chromeLine = p.hairline
    val corner = if (dense) 12.dp else 20.dp
    val btnPad = if (dense) 6.dp else 10.dp
    val btnGap = if (dense) 4.dp else 8.dp
    GlassCard(modifier) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
            AmniMap(camera, tiles, route, reports, stops, stops.lastOrNull(), location, p.acc, p.panel2, onLongPress, Modifier.fillMaxSize())
            if (circular) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(chromeBg)
                        .border(1.dp, chromeLine, RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapBtn(Icons.Rounded.Search, "", searchOpen, p.acc, true) { searchOpen = !searchOpen; onShowLayers(false); onShowReport(false) }
                    onVoice?.let { v -> MapBtn(Icons.Rounded.Mic, "", false, p.acc, true) { searchOpen = false; v() } }
                    MapBtn(Icons.Rounded.MyLocation, "", camera.follow, p.acc, true) { camera.follow = true }
                    MapBtn(Icons.Rounded.Layers, "", showLayers, p.acc, true) { onShowLayers(!showLayers); onShowReport(false); searchOpen = false }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(chromeBg)
                        .border(1.dp, chromeLine, RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapBtn(Icons.Rounded.Remove, "", false, p.acc, true) { camera.zoom = (camera.zoom - 1).coerceAtLeast(11) }
                    MapBtn(Icons.Rounded.Add, "", false, p.acc, true) { camera.zoom = (camera.zoom + 1).coerceAtMost(17) }
                }
            } else if (dense) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(btnPad),
                    horizontalArrangement = Arrangement.spacedBy(btnGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapBtn(Icons.Rounded.Search, "", searchOpen, p.acc, true) { searchOpen = !searchOpen; onShowLayers(false); onShowReport(false) }
                    onVoice?.let { v -> MapBtn(Icons.Rounded.Mic, "", false, p.acc, true) { searchOpen = false; v() } }
                    MapBtn(Icons.Rounded.MyLocation, "", camera.follow, p.acc, true) { camera.follow = true }
                    MapBtn(Icons.Rounded.Layers, "", showLayers, p.acc, true) { onShowLayers(!showLayers); onShowReport(false); searchOpen = false }
                    if (onToggleExpand != null) {
                        Box(
                            Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isExpanded) p.acc else chromeBg)
                                .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(12.dp))
                                .clickable { onToggleExpand() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isExpanded) "⤡" else "⤢",
                                color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = btnPad),
                    verticalArrangement = Arrangement.spacedBy(btnGap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MapBtn(Icons.Rounded.Add, "", false, p.acc, true) { camera.zoom = (camera.zoom + 1).coerceAtMost(17) }
                    MapBtn(Icons.Rounded.Remove, "", false, p.acc, true) { camera.zoom = (camera.zoom - 1).coerceAtLeast(11) }
                }
            } else {
                Column(Modifier.align(Alignment.TopEnd).padding(btnPad), verticalArrangement = Arrangement.spacedBy(btnGap), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (onToggleExpand != null) {
                        Box(
                            Modifier
                                .size(58.dp, 38.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isExpanded) p.acc else chromeBg)
                                .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(14.dp))
                                .clickable { onToggleExpand() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isExpanded) "⤡ EXIT" else "⤢ FULL",
                                color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    MapBtn(Icons.Rounded.Search, "FIND", searchOpen, p.acc, false) { searchOpen = !searchOpen; onShowLayers(false); onShowReport(false) }
                    onVoice?.let { v -> MapBtn(Icons.Rounded.Mic, "VOICE", false, p.acc, false) { searchOpen = false; v() } }
                    MapBtn(Icons.Rounded.Warning, "REPORT", showReport, p.err, false) { onShowReport(!showReport); onShowLayers(false); searchOpen = false }
                    MapBtn(Icons.Rounded.MyLocation, "FOLLOW", camera.follow, p.acc, false) { camera.follow = true }
                    MapBtn(Icons.Rounded.Layers, "MAP", showLayers, p.acc, false) { onShowLayers(!showLayers); onShowReport(false); searchOpen = false }
                    MapBtn(Icons.Rounded.Add, "", false, p.acc, false) { camera.zoom = (camera.zoom + 1).coerceAtMost(17) }
                    MapBtn(Icons.Rounded.Remove, "", false, p.acc, false) { camera.zoom = (camera.zoom - 1).coerceAtLeast(11) }
                }
            }
            route?.let { r ->
                Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(16.dp)).background(chromeBg).border(1.dp, p.acc.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(p.accDim), contentAlignment = Alignment.Center) { Text("↱", color = p.acc, fontSize = 16.sp) }
                    Column {
                        Text("${(r.durationS / 60).roundToInt()} min · " + "%.1f mi".format(r.distanceM / 1609.34), color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (stops.size > 1) "Via ${stops.size - 1} stop${if (stops.size > 2) "s" else ""}" else "Traffic on route", color = p.soft, fontSize = 11.sp)
                    }
                    Box(Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, p.err.copy(alpha = 0.7f), RoundedCornerShape(99.dp)).clickable { onEndRoute() }.padding(horizontal = 10.dp, vertical = 5.dp)) { Text("END", color = p.err, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (routing) Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 7.dp)) { Text("Routing…", color = p.acc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            if (location == null && !searchOpen) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xEE3A2808)).border(1.dp, Color(0xFFFFB454).copy(alpha = 0.6f), RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text("📍 Waiting for GPS — grant Location in Amni-Drive Settings → WIRE", color = Color(0xFFFFB454), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) }
            else if (route == null && !routing && !searchOpen && (!dense || fullNav)) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(99.dp)).background(chromeBg).border(1.dp, chromeLine, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text("Search or long-press for destination", color = p.soft, fontSize = 11.sp) }
            if (!netOk) Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xEE3A2808)).border(1.dp, Color(0xFFFFB454).copy(alpha = 0.55f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { Text("No network — map, search & routing need Wi‑Fi/hotspot", color = Color(0xFFFFB454), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            if (searchOpen) SearchPanel(location, route != null, dense, Modifier.align(Alignment.TopCenter).fillMaxWidth(if (dense) 0.94f else 0.72f).padding(top = 6.dp, start = 6.dp, end = if (dense) 48.dp else 8.dp), { searchOpen = false; onGo(it) }, { searchOpen = false; onAddStop(it) }, { searchOpen = false }, onVoice = onVoice)
            if (showLayers) LayersPanel(camera, tiles, Modifier.align(Alignment.TopEnd).padding(end = 62.dp, top = if (dense) 48.dp else 8.dp))
            if (showReport) ReportPanel(location, reportStore, Modifier.align(if (fullNav) Alignment.BottomCenter else Alignment.BottomEnd).padding(8.dp)) { onShowReport(false) }
            if (!dense || fullNav) Text("© OSM · OpenFreeMap", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}
@Composable
private fun SearchPanel(
    location: Location?,
    hasRoute: Boolean,
    dense: Boolean,
    modifier: Modifier,
    onGo: (Place) -> Unit,
    onAddStop: (Place) -> Unit,
    onClose: () -> Unit,
    onVoice: (() -> Unit)? = null
) {
    val p = LocalAmni.current
    var tfv by remember { mutableStateOf(TextFieldValue(AaNavState.lastQuery, TextRange(AaNavState.lastQuery.length))) }
    val query = tfv.text
    fun setQueryEnd(next: String) {
        val capped = next.take(90)
        tfv = TextFieldValue(capped, TextRange(capped.length))
    }
    var symMode by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        AaNavState.lastQuery = query
        if (query.trim().length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(370)
        results = Geocoder.search(query.trim(), location?.latitude, location?.longitude)
        searching = false
    }
    GlassCard(modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(if (dense) 8.dp else 10.dp)) {
            val splitCols = dense && maxWidth >= 430.dp
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("MAP SEARCH", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    if (onVoice != null) {
                        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp)).clickable { onClose(); onVoice() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("🎤 VOICE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp)).clickable { onClose() }.padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text("✕", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.bg).border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔎", fontSize = 14.sp)
                    BasicTextField(tfv, { tfv = it }, Modifier.weight(1f), textStyle = TextStyle(color = p.ink, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Bold), cursorBrush = SolidColor(p.acc), singleLine = true, decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search place, city, or street…", color = p.soft, fontSize = 12.sp); inner() } })
                    if (query.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(p.panel2).clickable { setQueryEnd("") }.padding(horizontal = 7.dp, vertical = 2.dp)) { Text("CLEAR", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                }
                val resultsPane: @Composable (Modifier) -> Unit = { rMod ->
                    Column(rMod.then(if (dense) Modifier.height(if (splitCols) 137.dp else 84.dp) else Modifier).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (searching) Text("SEARCHING…", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        results.take(7).forEach { place ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(p.panel2.copy(alpha = 0.92f)).border(1.dp, p.hairline, RoundedCornerShape(9.dp)).clickable { onGo(place) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(place.name, color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    if (place.detail.isNotEmpty()) Text(place.detail, color = p.soft, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(p.acc).clickable { onGo(place) }.padding(horizontal = 9.dp, vertical = 5.dp)) { Text("GO", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                if (hasRoute) Box(Modifier.clip(RoundedCornerShape(7.dp)).border(1.dp, p.acc, RoundedCornerShape(7.dp)).clickable { onAddStop(place) }.padding(horizontal = 7.dp, vertical = 5.dp)) { Text("+STOP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        if (!searching && query.trim().length >= 2 && results.isEmpty()) Text("NO MATCHES — TRY CITY OR FULLER ADDRESS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        if (!searching && query.trim().length < 2) Text("Type 2+ letters or tap 🎤 Voice", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
                val kbPane: @Composable (Modifier) -> Unit = { kMod ->
                    val keyH = 29.dp
                    val keyRows = if (symMode) listOf("1234567890", "-/:;()&@.,", "#+!?%'\"") else listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                    Column(kMod, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        keyRows.forEachIndexed { idx, keys ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                keys.forEach { ch ->
                                    Box(Modifier.weight(1f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(6.dp)).clickable { setQueryEnd(query + ch.lowercaseChar()) }, contentAlignment = Alignment.Center) {
                                        Text(ch.toString(), color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (idx == 2) {
                                    Box(Modifier.weight(1.35f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.panel2).border(1.dp, p.err.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable { setQueryEnd(query.dropLast(1)) }, contentAlignment = Alignment.Center) {
                                        Text("⌫", color = p.err, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.weight(1.2f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(6.dp)).clickable { symMode = !symMode }, contentAlignment = Alignment.Center) {
                                Text(if (symMode) "ABC" else "123", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            if (onVoice != null) {
                                Box(Modifier.weight(1.0f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).clickable { onClose(); onVoice() }, contentAlignment = Alignment.Center) {
                                    Text("🎤", fontSize = 13.sp)
                                }
                            }
                            Box(Modifier.weight(2.7f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(6.dp)).clickable { setQueryEnd("$query ") }, contentAlignment = Alignment.Center) {
                                Text("SPACE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            if (results.isNotEmpty()) {
                                Box(Modifier.weight(1.5f).height(keyH).clip(RoundedCornerShape(6.dp)).background(p.acc).clickable { onGo(results.first()) }, contentAlignment = Alignment.Center) {
                                    Text("GO ↵", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (splitCols) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        resultsPane(Modifier.weight(0.95f))
                        kbPane(Modifier.weight(1.05f))
                    }
                } else {
                    if (query.trim().length >= 2 || results.isNotEmpty() || searching) resultsPane(Modifier.fillMaxWidth())
                    if (dense) kbPane(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
@Composable
private fun MapBtn(icon: ImageVector, label: String, active: Boolean, tint: Color, dense: Boolean = false, onClick: () -> Unit) {
    val p = LocalAmni.current
    val sz = if (dense) 40.dp else 58.dp
    val r = if (dense) 12.dp else 16.dp
    val ic = if (dense) 18.dp else 22.dp
    Column(
        Modifier.size(sz).clip(RoundedCornerShape(r))
            .background(if (active) tint.copy(alpha = 0.18f) else if (p.isLight) Color(0xF0FFFFFF) else Color(0xE012151C))
            .border(1.dp, if (active) tint.copy(alpha = 0.5f) else p.hairline, RoundedCornerShape(r))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, Modifier.size(ic), tint = if (active) tint else p.soft)
        if (label.isNotEmpty()) Text(label, color = if (active) tint else p.dim, fontFamily = FontFamily.Monospace, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
@Composable
private fun LayersPanel(camera: MapCamera, tiles: TileStore, modifier: Modifier) {
    val p = LocalAmni.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    GlassCard(modifier.width(250.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("MAP MENU", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LayerChip("DARK", camera.basemap == 0) { camera.basemap = 0 }
                LayerChip("LIGHT", camera.basemap == 1) { camera.basemap = 1 }
                LayerChip("SAT", camera.basemap == 2) { camera.basemap = 2 }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PERSPECTIVE TOP-DEAD-CENTER", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Switch(!camera.northUp, { camera.northUp = !it }, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = if (p.isLight) Color.White else Color(0xFF05070A)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("3D TILT", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Switch(camera.tilt, { camera.tilt = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = if (p.isLight) Color.White else Color(0xFF05070A)))
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (saving) p.panel2 else p.accDim).border(1.dp, p.acc.copy(alpha = 0.4f), RoundedCornerShape(11.dp)).clickable {
                if (!saving) { saving = true; progress = ""; scope.launch { tiles.prefetch(camera.basemap, camera.lat, camera.lon) { d, t -> progress = "$d/$t" }; saving = false; progress = "SAVED ✓" } }
            }.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (saving) "SAVING… $progress" else if (progress == "SAVED ✓") "AREA SAVED ✓ — TAP TO REFRESH" else "⬇ SAVE THIS AREA OFFLINE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Text("OFFLINE = ~10 MI AROUND HERE, ZOOMS 11–16 · TRAFFIC = ROUTE COLORS", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, lineHeight = 10.sp)
        }
    }
}
@Composable
private fun LayerChip(label: String, sel: Boolean, onClick: () -> Unit) {
    val p = LocalAmni.current
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (sel) p.acc else p.panel2).border(1.dp, if (sel) p.acc else p.line2, RoundedCornerShape(9.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(label, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun ReportPanel(location: Location?, store: ReportStore, modifier: Modifier, onDone: () -> Unit) {
    val p = LocalAmni.current
    GlassCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REPORT — KEEP THE ROAD CREW SHARP", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("CRASH", "💥", "crash"), Triple("POLICE", "👮", "police"), Triple("HAZARD", "⚠️", "hazard"), Triple("SLOW", "🐢", "slowdown"), Triple("CAMERA", "📷", "camera"), Triple("CLOSED", "🚧", "closure")).forEach { (label, emoji, type) ->
                    Column(
                        Modifier.size(58.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.line2, RoundedCornerShape(12.dp))
                            .clickable { location?.let { store.add(type, emoji, it.latitude, it.longitude) }; onDone() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Text(emoji, fontSize = 17.sp)
                        Text(label, color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 6.5.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}
