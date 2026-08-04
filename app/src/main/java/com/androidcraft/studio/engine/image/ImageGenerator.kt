package com.androidcraft.studio.engine.image

import android.graphics.Bitmap

/** The available on-device image-generation backends the user can pick between. */
enum class ImageMethod(val displayName: String, val diffusion: Boolean) {
    PROCEDURAL("Procedural", false),
    NATIVE("Diffusion · SD.cpp", true),
    ONNX("Diffusion · ONNX", true),
}

/** Whether a backend can run right now (e.g. its model is downloaded) and why not if it can't. */
data class GeneratorStatus(val available: Boolean, val hint: String)

/**
 * A single on-device image-generation backend. Implementations range from the instant procedural
 * renderer to real diffusion runtimes. All run fully on-device; none call a network at generate time.
 */
interface ImageGenerator {
    val method: ImageMethod

    fun status(): GeneratorStatus

    /**
     * @param seed optional; when null it is derived from the prompt (deterministic per prompt).
     * @param steps diffusion sampling steps (ignored by non-diffusion backends).
     * @param onProgress 0f..1f progress for long-running backends.
     */
    suspend fun generate(
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap
}
