package com.androidcraft.studio.engine

import android.content.Context
import android.graphics.Bitmap
import com.androidcraft.studio.engine.image.ImageGenerator
import com.androidcraft.studio.engine.image.ImageMethod
import com.androidcraft.studio.engine.image.NativeDiffusionGenerator
import com.androidcraft.studio.engine.image.OnnxDiffusionGenerator
import com.androidcraft.studio.engine.image.ProceduralImageGenerator
import java.io.File

/**
 * Coordinates the available on-device image-generation backends and dispatches a request to the
 * one the user selected. Every backend runs on-device; the choice is purely about which engine.
 */
class ImageEngine(context: Context) {

    private val modelsDir = File(context.applicationContext.filesDir, "models").apply { mkdirs() }

    private val generators: List<ImageGenerator> = listOf(
        ProceduralImageGenerator(),
        NativeDiffusionGenerator(modelsDir),
        OnnxDiffusionGenerator(modelsDir),
    )

    fun generators(): List<ImageGenerator> = generators

    fun byMethod(method: ImageMethod): ImageGenerator =
        generators.firstOrNull { it.method == method } ?: generators.first()

    /** Backwards-compatible convenience for the procedural preview. */
    suspend fun render(prompt: String, seed: Long? = null): Bitmap =
        byMethod(ImageMethod.PROCEDURAL).generate(prompt, seed, steps = 1, onProgress = {})

    suspend fun generate(
        method: ImageMethod,
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap = byMethod(method).generate(prompt, seed, steps, onProgress)
}
