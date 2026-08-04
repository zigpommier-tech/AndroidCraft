package com.androidcraft.studio.engine.image

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device text-to-image via a native stable-diffusion.cpp bridge, fully offline. The whole
 * diffusion pipeline — tokenizer, scheduler, UNet, VAE — runs in the bundled C++ library, so this
 * class only bridges to it over JNI.
 *
 * The model is any Stable Diffusion `.gguf` or `.safetensors` file the user downloads into the
 * models directory (from the Models tab). If the native library isn't present or no model has been
 * downloaded, [status] reports the backend as unavailable so the UI can steer the user instead of
 * failing.
 */
class NativeDiffusionGenerator(private val modelsDir: File) : ImageGenerator {

    override val method = ImageMethod.NATIVE

    private fun modelFile(): File? = modelsDir.listFiles()?.firstOrNull { f ->
        f.isFile && (f.name.endsWith(".gguf", true) || f.name.endsWith(".safetensors", true))
    }

    override fun status(): GeneratorStatus = when {
        !libAvailable -> GeneratorStatus(
            available = false,
            hint = "Native diffusion engine isn't included in this build.",
        )
        modelFile() == null -> GeneratorStatus(
            available = false,
            hint = "Download a Stable Diffusion model (.gguf/.safetensors) from the Models tab.",
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
        val model = modelFile() ?: error("No Stable Diffusion model downloaded")
        onProgress(0.02f)
        val effectiveSeed = seed ?: prompt.lowercase().hashCode().toLong()
        val pixels = nativeTxt2Img(
            model.absolutePath,
            prompt,
            steps.coerceIn(1, 50),
            effectiveSeed,
            SIZE,
            SIZE,
        )
        onProgress(0.98f)
        checkNotNull(pixels) { "Diffusion returned no image" }
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        onProgress(1f)
        bitmap
    }

    /** Implemented in the native `sdjni` library. Returns SIZE*SIZE ARGB pixels, or null on failure. */
    private external fun nativeTxt2Img(
        modelPath: String,
        prompt: String,
        steps: Int,
        seed: Long,
        width: Int,
        height: Int,
    ): IntArray?

    companion object {
        private const val SIZE = 512

        private val libAvailable: Boolean = runCatching {
            System.loadLibrary("sdjni")
        }.isSuccess
    }
}
