package com.amni.drive.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Main-thread-safe car voice recognizer with automatic Android 13+ on-device fallback.
 * Requires <queries> for android.speech.RecognitionService in AndroidManifest.xml on Android 11+.
 */
object VoiceSearch {
    private const val TAG = "Amni.Voice"
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null

    fun hasMicPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun available(ctx: Context): Boolean {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            return true
        }
        return false
    }

    fun listen(ctx: Context, onState: (String) -> Unit, onResult: (String?) -> Unit) {
        val appCtx = ctx.applicationContext
        main.post {
            stopOnMain()
            if (!hasMicPermission(appCtx)) {
                onState("Mic permission needed — grant in Amni-Drive Settings → WIRE")
                onResult(null)
                return@post
            }
            val useOnDeviceFirst = !SpeechRecognizer.isRecognitionAvailable(appCtx) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appCtx)
            if (!SpeechRecognizer.isRecognitionAvailable(appCtx) && !useOnDeviceFirst) {
                onState("No speech service installed on phone")
                onResult(null)
                return@post
            }
            startRecognizerOnMain(appCtx, onDevice = useOnDeviceFirst, allowFallback = !useOnDeviceFirst, onState = onState, onResult = onResult)
        }
    }

    private fun startRecognizerOnMain(
        appCtx: Context,
        onDevice: Boolean,
        allowFallback: Boolean,
        onState: (String) -> Unit,
        onResult: (String?) -> Unit
    ) {
        stopOnMain()
        val r = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appCtx)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appCtx)
            }
        } catch (e: Exception) {
            Log.w(TAG, "createSpeechRecognizer(onDevice=$onDevice) failed: ${e.message}")
            if (allowFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(appCtx)) {
                startRecognizerOnMain(appCtx, onDevice = true, allowFallback = false, onState = onState, onResult = onResult)
                return
            }
            onState("Voice init failed: ${e.message}")
            onResult(null)
            return
        }
        sr = r
        onState(if (onDevice) "Listening (on-device)…" else "Listening…")
        var delivered = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onState("Listening… speak now")
            override fun onBeginningOfSpeech() = onState("Hearing you…")
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onState("Processing…")
            override fun onError(error: Int) {
                if (delivered) return
                Log.w(TAG, "speech error=$error onDevice=$onDevice")
                val canRetryOnDevice = allowFallback &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(appCtx) &&
                    (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_CLIENT)
                if (canRetryOnDevice) {
                    startRecognizerOnMain(appCtx, onDevice = true, allowFallback = false, onState = onState, onResult = onResult)
                    return
                }
                delivered = true
                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied — enable in Phone Settings"
                    SpeechRecognizer.ERROR_AUDIO -> "Mic busy (car call or BT active)"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — tap 🎤 to retry"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice network timeout — tap 🎤 to retry"
                    else -> "Voice error ($error) — tap 🎤 to retry"
                }
                onState(msg)
                onResult(null)
                stopOnMain()
            }
            override fun onResults(results: Bundle?) {
                if (delivered) return
                delivered = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                onResult(text?.takeIf { it.isNotEmpty() })
                stopOnMain()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                    onState("“$it”")
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appCtx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2_500L)
        }
        runCatching { r.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed: ${it.message}")
            onState("Voice start failed: ${it.message}")
            onResult(null)
            stopOnMain()
        }
    }

    fun stop() {
        main.post { stopOnMain() }
    }

    private fun stopOnMain() {
        runCatching { sr?.cancel() }
        runCatching { sr?.destroy() }
        sr = null
    }
}
