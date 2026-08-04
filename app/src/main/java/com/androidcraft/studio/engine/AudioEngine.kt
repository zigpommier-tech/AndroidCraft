package com.androidcraft.studio.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.Locale

/**
 * Voice generation backed by the device's built-in text-to-speech engine.
 *
 * Synthesizes speech straight to a WAV file on disk (no network, no download), which the UI can
 * then play back or share. Works on every Android device that ships a TTS engine.
 */
class AudioEngine(private val appContext: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    suspend fun ensureReady(): Boolean {
        if (ready) return true
        val deferred = CompletableDeferred<Boolean>()
        tts = TextToSpeech(appContext) { status ->
            deferred.complete(status == TextToSpeech.SUCCESS)
        }
        ready = deferred.await()
        return ready
    }

    fun availableVoices(): List<String> =
        tts?.voices?.mapNotNull { it.name }?.sorted() ?: emptyList()

    fun setVoice(name: String) {
        val match: Voice? = tts?.voices?.firstOrNull { it.name == name }
        if (match != null) tts?.setVoice(match)
    }

    fun setSpeed(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /** Synthesizes [text] to a WAV file and returns it, or null on failure. */
    suspend fun synthesizeToFile(text: String, outputName: String): File? {
        val engine = tts ?: return null
        if (text.isBlank()) return null
        engine.setLanguage(Locale.getDefault())

        val outFile = File(appContext.cacheDir, outputName)
        val utteranceId = "craft-${System.nanoTime()}"
        val done = CompletableDeferred<Boolean>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) done.complete(false)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) done.complete(false)
            }
        })

        val params = Bundle()
        val result = engine.synthesizeToFile(text, params, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) return null
        return if (done.await() && outFile.exists()) outFile else null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
