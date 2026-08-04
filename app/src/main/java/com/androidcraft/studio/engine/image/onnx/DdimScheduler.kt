package com.androidcraft.studio.engine.image.onnx

import kotlin.math.sqrt

/**
 * Minimal deterministic DDIM scheduler (eta = 0) with the standard Stable Diffusion 1.x config
 * (scaled-linear betas, 1000 train steps). Enough to drive the ONNX UNet denoising loop.
 */
class DdimScheduler(
    private val numTrain: Int = 1000,
    betaStart: Double = 0.00085,
    betaEnd: Double = 0.012,
) {
    private val alphasCumprod: DoubleArray
    private var stepRatio: Int = 1

    /** DDIM starts from unit-variance noise. */
    val initNoiseSigma: Double = 1.0

    init {
        val betas = DoubleArray(numTrain)
        val start = sqrt(betaStart)
        val end = sqrt(betaEnd)
        for (i in 0 until numTrain) {
            val b = start + (end - start) * i / (numTrain - 1)
            betas[i] = b * b // scaled_linear
        }
        alphasCumprod = DoubleArray(numTrain)
        var cum = 1.0
        for (i in 0 until numTrain) {
            cum *= (1.0 - betas[i])
            alphasCumprod[i] = cum
        }
    }

    /** Returns the descending list of train timesteps to visit. */
    fun timesteps(steps: Int): IntArray {
        stepRatio = numTrain / steps
        return IntArray(steps) { i -> (steps - 1 - i) * stepRatio }
    }

    /** DDIM update: given the predicted noise at [t], return the previous latent. */
    fun step(modelOutput: FloatArray, t: Int, sample: FloatArray): FloatArray {
        val prevT = t - stepRatio
        val aT = alphasCumprod[t]
        val aPrev = if (prevT >= 0) alphasCumprod[prevT] else 1.0
        val sqrtAT = sqrt(aT)
        val sqrtOneMinusAT = sqrt(1.0 - aT)
        val sqrtAPrev = sqrt(aPrev)
        val sqrtOneMinusAPrev = sqrt(1.0 - aPrev)

        val out = FloatArray(sample.size)
        for (i in sample.indices) {
            val eps = modelOutput[i].toDouble()
            val predX0 = (sample[i] - sqrtOneMinusAT * eps) / sqrtAT
            out[i] = (sqrtAPrev * predX0 + sqrtOneMinusAPrev * eps).toFloat()
        }
        return out
    }
}
