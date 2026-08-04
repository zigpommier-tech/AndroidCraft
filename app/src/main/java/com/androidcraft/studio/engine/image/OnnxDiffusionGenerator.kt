package com.androidcraft.studio.engine.image

import android.graphics.Bitmap
import java.io.File

/**
 * Real on-device text-to-image via ONNX Runtime executing a Stable Diffusion v1.5 export
 * (CLIP text encoder + UNet + VAE decoder), fully offline. The model is a multi-file bundle
 * downloaded into [bundleDir].
 *
 * Reported unavailable until the bundle is present; the sampling pipeline is added in a follow-up
 * so the app keeps building while the runtime is integrated.
 */
class OnnxDiffusionGenerator(modelsDir: File) : ImageGenerator {

    override val method = ImageMethod.ONNX

    private val bundleDir = File(modelsDir, BUNDLE_DIR_NAME)

    override fun status(): GeneratorStatus = if (bundleReady()) {
        GeneratorStatus(available = false, hint = "ONNX runner integration in progress.")
    } else {
        GeneratorStatus(available = false, hint = "ONNX diffusion engine isn't included in this build yet.")
    }

    private fun bundleReady(): Boolean =
        File(bundleDir, "unet.onnx").exists() &&
            File(bundleDir, "text_encoder.onnx").exists() &&
            File(bundleDir, "vae_decoder.onnx").exists()

    override suspend fun generate(
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap {
        error("ONNX diffusion runner not yet available on this build")
    }

    companion object {
        const val BUNDLE_DIR_NAME = "sd15-onnx"
    }
}
