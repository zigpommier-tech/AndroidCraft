package com.androidcraft.studio.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device large language model inference via the Google AI Edge / MediaPipe GenAI runtime.
 *
 * A single [LlmInference] handle is created per model file and reused across turns. Loading is
 * expensive (weights are mapped into memory) so callers should keep the engine alive while a
 * model is selected and [close] it when switching models.
 */
class TextEngine(private val appContext: Context) {

    private var engine: LlmInference? = null
    private var loadedModelPath: String? = null

    val isLoaded: Boolean get() = engine != null

    /** Loads [modelFile] if it isn't already the active model. Safe to call repeatedly. */
    suspend fun ensureLoaded(modelFile: File, maxTokens: Int = 1024) = withContext(Dispatchers.IO) {
        if (loadedModelPath == modelFile.absolutePath && engine != null) return@withContext
        close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(maxTokens)
            .build()
        engine = LlmInference.createFromOptions(appContext, options)
        loadedModelPath = modelFile.absolutePath
    }

    /** Runs a single-shot generation. [ensureLoaded] must have succeeded first. */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val active = engine ?: error("No model loaded")
        active.generateResponse(prompt).orEmpty()
    }

    fun close() {
        engine?.let {
            runCatching { it.close() }
        }
        engine = null
        loadedModelPath = null
    }
}
