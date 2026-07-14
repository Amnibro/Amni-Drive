package com.amni.drive.voice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
object VoiceSearch {
private var sr: SpeechRecognizer? = null
fun available(ctx: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)
fun listen(ctx: Context, onState: (String) -> Unit, onResult: (String?) -> Unit) {
stop()
if (!available(ctx)) { onState("Voice recognition unavailable"); onResult(null); return }
val r = SpeechRecognizer.createSpeechRecognizer(ctx)
sr = r
r.setRecognitionListener(object : RecognitionListener {
override fun onReadyForSpeech(params: Bundle?) = onState("Listening…")
override fun onBeginningOfSpeech() = onState("Listening…")
override fun onRmsChanged(rmsdB: Float) {}
override fun onBufferReceived(buffer: ByteArray?) {}
override fun onEndOfSpeech() = onState("Thinking…")
override fun onError(error: Int) { onState("Didn't catch that — tap 🎤 to retry"); onResult(null); stop() }
override fun onResults(results: Bundle?) {
onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
stop()
}
override fun onPartialResults(partialResults: Bundle?) {
partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onState("“$it”") }
}
override fun onEvent(eventType: Int, params: Bundle?) {}
})
r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
})
}
fun stop() {
runCatching { sr?.destroy() }
sr = null
}
}
