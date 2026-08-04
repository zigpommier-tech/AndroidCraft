package com.androidcraft.studio.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic procedural renderer: turns a prompt into abstract art entirely on-device with no
 * model download. Always available; a good instant preview and a zero-dependency fallback.
 */
class ProceduralImageGenerator : ImageGenerator {

    override val method = ImageMethod.PROCEDURAL

    override fun status() = GeneratorStatus(available = true, hint = "Instant · no download")

    override suspend fun generate(
        prompt: String,
        seed: Long?,
        steps: Int,
        onProgress: suspend (Float) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        val size = 768
        val effectiveSeed = seed ?: prompt.lowercase().hashCode().toLong()
        val rng = Random(effectiveSeed)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = paletteFor(prompt, rng)

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(palette[0], palette[1], palette[2]),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), basePaint)

        val orbCount = 5 + rng.nextInt(6)
        repeat(orbCount) { i ->
            val cx = rng.nextFloat() * size
            val cy = rng.nextFloat() * size
            val radius = size * (0.12f + rng.nextFloat() * 0.28f)
            val color = palette[rng.nextInt(palette.size)]
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(withAlpha(color, 200), withAlpha(color, 0)),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(cx, cy, radius, glow)
            if (i % 2 == 0) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.004f
                    this.color = withAlpha(palette[rng.nextInt(palette.size)], 90)
                }
                val steps2 = 60
                var prevX = 0f
                var prevY = size * rng.nextFloat()
                val amp = size * (0.05f + rng.nextFloat() * 0.15f)
                val freq = 2 + rng.nextInt(4)
                for (s in 1..steps2) {
                    val x = size * (s / steps2.toFloat())
                    val y = prevY + amp * sin(freq * (s / steps2.toFloat()) * 6.283f + i)
                    canvas.drawLine(prevX, prevY, x, y, stroke)
                    prevX = x
                    prevY = y
                }
            }
        }

        applyGrain(bitmap, rng, strength = 14)
        onProgress(1f)
        bitmap
    }

    private fun paletteFor(prompt: String, rng: Random): IntArray {
        val p = prompt.lowercase()
        val base = when {
            listOf("night", "dark", "space", "galaxy", "noir").any { p.contains(it) } ->
                intArrayOf(0xFF0B1026.toInt(), 0xFF3A1C71.toInt(), 0xFF00C2FF.toInt())
            listOf("sunset", "warm", "fire", "gold", "desert").any { p.contains(it) } ->
                intArrayOf(0xFF7A1F3D.toInt(), 0xFFE8552A.toInt(), 0xFFF7C948.toInt())
            listOf("nature", "forest", "green", "jungle", "leaf").any { p.contains(it) } ->
                intArrayOf(0xFF08301E.toInt(), 0xFF1F7A4D.toInt(), 0xFFB4E197.toInt())
            listOf("ocean", "sea", "water", "ice", "blue").any { p.contains(it) } ->
                intArrayOf(0xFF03203A.toInt(), 0xFF1B6CA8.toInt(), 0xFF7DE2D1.toInt())
            else -> {
                val hue = (abs(p.hashCode()) % 360).toFloat()
                intArrayOf(
                    hsv(hue, 0.7f, 0.25f),
                    hsv((hue + 40) % 360, 0.8f, 0.55f),
                    hsv((hue + 90) % 360, 0.6f, 0.9f),
                )
            }
        }
        return if (rng.nextBoolean()) base else intArrayOf(base[2], base[0], base[1])
    }

    private fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(h, s, v))

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun applyGrain(bitmap: Bitmap, rng: Random, strength: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            if (rng.nextInt(3) != 0) continue
            val n = rng.nextInt(strength * 2) - strength
            val c = pixels[i]
            val r = (Color.red(c) + n).coerceIn(0, 255)
            val g = (Color.green(c) + n).coerceIn(0, 255)
            val b = (Color.blue(c) + n).coerceIn(0, 255)
            pixels[i] = Color.argb(255, r, g, b)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
