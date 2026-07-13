package com.amni.drive.map
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
object TurnGuidance {
data class Cue(val idx: Int, val step: RouteStep, val distM: Double, val text: String, val arrow: String, val spoken: String)
const val ADVANCE_M = 40.0
fun arrowFor(type: String, modifier: String): String = when {
type == "arrive" -> "⚑"
type == "roundabout" || type == "rotary" || type == "roundabout turn" -> "↻"
type == "merge" -> if (modifier.contains("left")) "↰" else "↱"
type == "off ramp" || type == "on ramp" -> if (modifier.contains("left")) "↖" else "↗"
modifier.contains("uturn") -> "⤸"
modifier == "sharp left" -> "↰"
modifier == "sharp right" -> "↱"
modifier == "slight left" -> "↖"
modifier == "slight right" -> "↗"
modifier == "left" -> "←"
modifier == "right" -> "→"
else -> "↑"
}
fun phrase(type: String, modifier: String, name: String): String {
val road = if (name.isBlank()) "" else " onto $name"
return when (type) {
"arrive" -> "You have arrived"
"depart" -> "Head ${modifier.ifBlank { "out" }}$road"
"roundabout", "rotary", "roundabout turn" -> "Take the roundabout$road"
"merge" -> "Merge ${modifier.ifBlank { "ahead" }}$road"
"off ramp" -> "Take the ramp ${modifier.ifBlank { "ahead" }}$road"
"on ramp" -> "Take the ramp ${modifier.ifBlank { "ahead" }}$road"
"fork" -> "Keep ${modifier.ifBlank { "ahead" }} at the fork$road"
"end of road" -> "Turn ${modifier.ifBlank { "ahead" }}$road"
"continue" -> if (modifier == "straight" || modifier.isBlank()) "Continue straight$road" else "Turn $modifier$road"
"new name" -> "Continue$road"
else -> when {
modifier.contains("uturn") -> "Make a U-turn"
modifier.isBlank() -> "Continue$road"
else -> "Turn $modifier$road"
}
}
}
fun distText(m: Double): String {
val ft = m * 3.28084
return if (ft < 900) "${((ft / 50).roundToInt() * 50).coerceAtLeast(50)} ft" else "%.1f mi".format(m / 1609.34)
}
fun distSpoken(m: Double): String {
val ft = m * 3.28084
return when {
ft < 900 -> "In ${((ft / 50).roundToInt() * 50).coerceAtLeast(50)} feet"
m < 2100 -> "In a quarter mile"
m < 3300 -> "In one mile"
else -> "In %.0f miles".format(m / 1609.34)
}
}
fun cue(route: RouteResult, lat: Double, lon: Double, fromIdx: Int): Cue? {
val steps = route.steps
if (steps.isEmpty()) return null
var idx = fromIdx.coerceIn(0, steps.lastIndex)
while (idx < steps.lastIndex && (steps[idx].type == "depart" || haversineM(lat, lon, steps[idx].lat, steps[idx].lon) < ADVANCE_M)) idx++
val cur = steps[idx]
val dCur = haversineM(lat, lon, cur.lat, cur.lon)
if (idx < steps.lastIndex) {
val nxt = steps[idx + 1]
if (haversineM(lat, lon, nxt.lat, nxt.lon) + 15.0 < dCur) return cue(route, lat, lon, idx + 1)
}
val text = phrase(cur.type, cur.modifier, cur.name)
return Cue(idx, cur, dCur, "${distText(dCur)} · $text", arrowFor(cur.type, cur.modifier), "${distSpoken(dCur)}, ${text.lowercase()}")
}
fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
val r = 6371000.0
val dLat = Math.toRadians(bLat - aLat)
val dLon = Math.toRadians(bLon - aLon)
val x = sin(dLat / 2).let { it * it } + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).let { it * it }
return 2 * r * atan2(sqrt(x), sqrt(1 - x))
}
fun stageFor(distM: Double): Int = when {
distM < 70 -> 2
distM < 320 -> 1
else -> 0
}
}
class VoiceGuide(ctx: Context) {
private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
private var ready = false
private var lastIdx = -1
private var lastStage = -1
private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
private val tts = TextToSpeech(ctx) { ready = it == TextToSpeech.SUCCESS }.apply {
setAudioAttributes(attrs)
setOnUtteranceProgressListener(object : UtteranceProgressListener() {
override fun onStart(id: String?) {}
override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
@Deprecated("api") override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
})
}
fun maybeSpeak(cue: TurnGuidance.Cue?, enabled: Boolean) {
if (cue == null || !enabled || !ready) return
val stage = TurnGuidance.stageFor(cue.distM)
if (cue.idx == lastIdx && stage <= lastStage) return
lastIdx = cue.idx
lastStage = stage
val line = if (stage == 2) TurnGuidance.phrase(cue.step.type, cue.step.modifier, cue.step.name) else cue.spoken
am.requestAudioFocus(focus)
tts.speak(line, TextToSpeech.QUEUE_FLUSH, null, "amni_nav_${cue.idx}_$stage")
}
fun reset() { lastIdx = -1; lastStage = -1 }
fun shutdown() { runCatching { tts.shutdown() }; runCatching { am.abandonAudioFocusRequest(focus) } }
}
