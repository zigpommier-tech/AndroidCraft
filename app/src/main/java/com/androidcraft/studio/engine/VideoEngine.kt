package com.androidcraft.studio.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Experimental on-device "text-to-video": generates a short sequence of animated frames seeded by
 * the prompt. True diffusion-based video on phones is not yet practical, so this renders a moving
 * abstract composition to scaffold the pipeline and UI. Frames can later be encoded to MP4 via
 * MediaCodec/MediaMuxer, or replaced by a real on-device video model.
 */
class VideoEngine {

    data class Clip(val frames: List<Bitmap>, val fps: Int)

    suspend fun render(
        prompt: String,
        frameCount: Int = 24,
        size: Int = 384,
        fps: Int = 12,
        onFrame: suspend (Int, Int) -> Unit = { _, _ -> },
    ): Clip = withContext(Dispatchers.Default) {
        val seed = prompt.lowercase().hashCode().toLong()
        val palette = paletteFor(prompt)
        val frames = ArrayList<Bitmap>(frameCount)
        for (f in 0 until frameCount) {
            val t = f / frameCount.toFloat()
            frames.add(renderFrame(size, palette, seed, t))
            onFrame(f + 1, frameCount)
        }
        Clip(frames, fps)
    }

    private fun renderFrame(size: Int, palette: IntArray, seed: Long, t: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val phase = (t * 2 * PI).toFloat()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                palette[0], palette[1], Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)

        val rng = Random(seed)
        val orbs = 6
        repeat(orbs) { i ->
            val baseX = rng.nextFloat()
            val baseY = rng.nextFloat()
            val driftX = 0.18f * sin(phase + i)
            val driftY = 0.18f * sin(phase * 0.7f + i * 1.3f)
            val cx = ((baseX + driftX) % 1f).coerceIn(0f, 1f) * size
            val cy = ((baseY + driftY) % 1f).coerceIn(0f, 1f) * size
            val radius = size * (0.14f + 0.06f * sin(phase + i))
            val color = palette[(i + 2) % palette.size]
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius.coerceAtLeast(1f),
                    intArrayOf(withAlpha(color, 220), withAlpha(color, 0)),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(cx, cy, radius.coerceAtLeast(1f), glow)
        }
        return bitmap
    }

    private fun paletteFor(prompt: String): IntArray {
        val p = prompt.lowercase()
        return when {
            listOf("night", "space", "galaxy", "dark").any { p.contains(it) } ->
                intArrayOf(0xFF0B1026.toInt(), 0xFF3A1C71.toInt(), 0xFF00C2FF.toInt(), 0xFF8B7CF6.toInt())
            listOf("sunset", "fire", "warm", "gold").any { p.contains(it) } ->
                intArrayOf(0xFF2A0A1A.toInt(), 0xFF7A1F3D.toInt(), 0xFFE8552A.toInt(), 0xFFF7C948.toInt())
            listOf("ocean", "water", "sea", "blue").any { p.contains(it) } ->
                intArrayOf(0xFF03203A.toInt(), 0xFF1B6CA8.toInt(), 0xFF22D3EE.toInt(), 0xFF7DE2D1.toInt())
            else ->
                intArrayOf(0xFF141A22.toInt(), 0xFF6D5AE6.toInt(), 0xFF8B7CF6.toInt(), 0xFF22D3EE.toInt())
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
