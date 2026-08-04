package com.androidcraft.studio.engine.image

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device text-to-image via a native stable-diffusion.cpp bridge (single-file GGUF model,
 * fully offline). The entire diffusion pipeline — tokenizer, scheduler, UNet, VAE — runs in the
 * bundled C++ library, so this class only bridges to it over JNI.
 *
 * The native library is loaded lazily; if it isn't present in the build, or the model hasn't been
 * downloaded yet, [status] reports the backend as unavailable and the UI steers the user to the
 * Models tab instead of failing.
 */
class NativeDiffusionGenerator(modelsDir: File) : ImageGenerator {

    override val method = ImageMethod.NATIVE

    private val modelFile = File(modelsDir, MODEL_FILE_NAME)

    override fun status(): GeneratorStatus = when {
        !libAvailable -> GeneratorStatus(
            available = false,
            hint = "Native diffusion engine isn't included in this build yet.",
        )
        !modelFile.exists() -> GeneratorStatus(
            available = false,
            hint = "Download a Stable Diffusion GGUF model from the Models tab.",
        )
        else -> GeneratorStatus(available = true, hint = "On-device diffusion · CPU")
    }

    override suspend fun generate(
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        check(libAvailable) { "Native diffusion library not available" }
        check(modelFile.exists()) { "Model not downloaded" }
        onProgress(0.02f)
        val effectiveSeed = seed ?: prompt.lowercase().hashCode().toLong()
        val pixels = nativeTxt2Img(
            modelFile.absolutePath,
            prompt,
            steps.coerceIn(1, 50),
            effectiveSeed,
            SIZE,
            SIZE,
        )
        onProgress(0.98f)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        onProgress(1f)
        bitmap
    }

    /** Implemented in the native `sdjni` library. Returns SIZE*SIZE ARGB pixels. */
    private external fun nativeTxt2Img(
        modelPath: String,
        prompt: String,
        steps: Int,
        seed: Long,
        width: Int,
        height: Int,
    ): IntArray

    companion object {
        const val MODEL_FILE_NAME = "sd15.gguf"
        private const val SIZE = 512

        private val libAvailable: Boolean = runCatching {
            System.loadLibrary("sdjni")
        }.isSuccess
    }
}
