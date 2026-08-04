package com.androidcraft.studio.engine.image

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.androidcraft.studio.engine.image.onnx.ClipTokenizer
import com.androidcraft.studio.engine.image.onnx.DdimScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.Random

/**
 * Real on-device text-to-image via ONNX Runtime executing a Stable Diffusion v1.5 export
 * (CLIP text encoder + UNet + VAE decoder), fully offline. Expects an Optimum-style ONNX bundle
 * extracted into [bundleDir]:
 *
 * ```
 * sd15-onnx/
 *   text_encoder/model.onnx
 *   unet/model.onnx
 *   vae_decoder/model.onnx
 *   tokenizer/vocab.json
 *   tokenizer/merges.txt
 * ```
 *
 * Reported unavailable until that bundle is present. Latent size is fixed to 512×512 (64×64×4).
 */
class OnnxDiffusionGenerator(modelsDir: File) : ImageGenerator {

    override val method = ImageMethod.ONNX

    private val bundleDir = File(modelsDir, "sd15-onnx")
    private val textEncoderFile = File(bundleDir, "text_encoder/model.onnx")
    private val unetFile = File(bundleDir, "unet/model.onnx")
    private val vaeFile = File(bundleDir, "vae_decoder/model.onnx")
    private val vocabFile = File(bundleDir, "tokenizer/vocab.json")
    private val mergesFile = File(bundleDir, "tokenizer/merges.txt")

    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var textEncoder: OrtSession? = null
    @Volatile private var unet: OrtSession? = null
    @Volatile private var vae: OrtSession? = null
    @Volatile private var tokenizer: ClipTokenizer? = null

    private fun bundleReady(): Boolean =
        textEncoderFile.exists() && unetFile.exists() && vaeFile.exists() &&
            vocabFile.exists() && mergesFile.exists()

    override fun status(): GeneratorStatus = if (bundleReady()) {
        GeneratorStatus(available = true, hint = "ONNX Runtime · on-device · CPU")
    } else {
        GeneratorStatus(
            available = false,
            hint = "Add an SD 1.5 ONNX bundle (sd15-onnx/) — download it in the Models tab.",
        )
    }

    override suspend fun generate(
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        check(bundleReady()) { "ONNX SD bundle not present" }
        ensureLoaded()
        val ortEnv = env!!
        val tok = tokenizer!!

        val guidance = 7.5f
        val height = 512
        val width = 512
        val latentH = height / 8
        val latentW = width / 8
        val latentSize = 4 * latentH * latentW

        // 1) Text embeddings for prompt (cond) and empty prompt (uncond).
        val condEmb = encodeText(ortEnv, tok, prompt)
        val uncondEmb = encodeText(ortEnv, tok, "")
        onProgress(0.05f)

        // 2) Initial latents ~ N(0,1).
        val rng = Random(seed ?: prompt.lowercase().hashCode().toLong())
        val scheduler = DdimScheduler()
        var latents = FloatArray(latentSize) { (rng.nextGaussian() * scheduler.initNoiseSigma).toFloat() }

        // 3) Denoising loop.
        val timesteps = scheduler.timesteps(steps.coerceIn(1, 50))
        val latentShape = longArrayOf(1, 4, latentH.toLong(), latentW.toLong())
        for ((index, t) in timesteps.withIndex()) {
            val epsUncond = runUnet(ortEnv, latents, latentShape, t, uncondEmb)
            val epsCond = runUnet(ortEnv, latents, latentShape, t, condEmb)
            val eps = FloatArray(latentSize) { i ->
                epsUncond[i] + guidance * (epsCond[i] - epsUncond[i])
            }
            latents = scheduler.step(eps, t, latents)
            onProgress(0.05f + 0.85f * (index + 1) / timesteps.size)
        }

        // 4) VAE decode (latents are scaled by 1/0.18215 first).
        val scaled = FloatArray(latentSize) { latents[it] / 0.18215f }
        val decoded = runVae(ortEnv, scaled, latentShape)
        onProgress(0.98f)
        val bitmap = toBitmap(decoded, width, height)
        onProgress(1f)
        bitmap
    }

    private fun encodeText(ortEnv: OrtEnvironment, tok: ClipTokenizer, text: String): FloatArray {
        val ids = tok.encode(text)
        val input = OnnxTensor.createTensor(
            ortEnv, IntBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()),
        )
        input.use {
            textEncoder!!.run(mapOf(textEncoder!!.inputNames.first() to it)).use { res ->
                return readFloats(res.get(0) as OnnxTensor)
            }
        }
    }

    private fun runUnet(
        ortEnv: OrtEnvironment,
        latents: FloatArray,
        latentShape: LongArray,
        t: Int,
        emb: FloatArray,
    ): FloatArray {
        val sample = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(latents), latentShape)
        val timestep = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(longArrayOf(t.toLong())), longArrayOf(1))
        val hidden = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(emb), longArrayOf(1, 77, 768))
        val inputs = HashMap<String, OnnxTensor>()
        val names = unet!!.inputNames.toList()
        // Optimum SD UNet order: sample, timestep, encoder_hidden_states.
        inputs[names.getOrElse(0) { "sample" }] = sample
        inputs[names.getOrElse(1) { "timestep" }] = timestep
        inputs[names.getOrElse(2) { "encoder_hidden_states" }] = hidden
        try {
            unet!!.run(inputs).use { res -> return readFloats(res.get(0) as OnnxTensor) }
        } finally {
            sample.close(); timestep.close(); hidden.close()
        }
    }

    private fun runVae(ortEnv: OrtEnvironment, latents: FloatArray, latentShape: LongArray): FloatArray {
        val input = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(latents), latentShape)
        input.use {
            vae!!.run(mapOf(vae!!.inputNames.first() to it)).use { res ->
                return readFloats(res.get(0) as OnnxTensor)
            }
        }
    }

    private fun readFloats(tensor: OnnxTensor): FloatArray {
        val fb = tensor.floatBuffer
        val arr = FloatArray(fb.remaining())
        fb.get(arr)
        return arr
    }

    /** Decoded VAE output is [1,3,H,W] in [-1,1], channel-major. */
    private fun toBitmap(decoded: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val plane = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val r = channelToByte(decoded[0 * plane + idx])
                val g = channelToByte(decoded[1 * plane + idx])
                val b = channelToByte(decoded[2 * plane + idx])
                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun channelToByte(v: Float): Int =
        (((v / 2f) + 0.5f) * 255f).toInt().coerceIn(0, 255)

    @Synchronized
    private fun ensureLoaded() {
        if (unet != null) return
        val ortEnv = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        env = ortEnv
        textEncoder = ortEnv.createSession(textEncoderFile.absolutePath, opts)
        unet = ortEnv.createSession(unetFile.absolutePath, opts)
        vae = ortEnv.createSession(vaeFile.absolutePath, opts)
        tokenizer = ClipTokenizer(vocabFile, mergesFile)
    }

    fun close() {
        runCatching { textEncoder?.close() }
        runCatching { unet?.close() }
        runCatching { vae?.close() }
        textEncoder = null; unet = null; vae = null; tokenizer = null
    }
}
