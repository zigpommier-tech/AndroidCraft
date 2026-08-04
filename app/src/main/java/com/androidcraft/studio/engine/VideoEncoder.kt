package com.androidcraft.studio.engine

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes a list of frames into a real H.264 MP4 file entirely on-device, using [MediaCodec] with
 * a YUV420-flexible input surface-less buffer and [MediaMuxer] for the container. Uses
 * [MediaCodec.getInputImage] so per-device plane strides / layout are handled portably.
 */
object VideoEncoder {

    private const val MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L

    suspend fun encodeMp4(frames: List<Bitmap>, fps: Int, outFile: File): File =
        withContext(Dispatchers.Default) {
            require(frames.isNotEmpty()) { "No frames to encode" }
            // H.264 wants even dimensions; the generator uses multiples of 16 already.
            val width = frames[0].width and 1.inv()
            val height = frames[0].height and 1.inv()

            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, (width * height * fps * 0.2f).toInt().coerceAtLeast(2_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            if (outFile.exists()) outFile.delete()
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            fun drain(endOfStream: Boolean) {
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                        }

                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        outIndex >= 0 -> {
                            val encoded: ByteBuffer = codec.getOutputBuffer(outIndex) ?: continue
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            try {
                frames.forEachIndexed { index, frame ->
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val image = codec.getInputImage(inIndex)
                        if (image != null) {
                            fillYuv420(frame, image, width, height)
                        }
                        val ptsUs = index.toLong() * 1_000_000L / fps
                        codec.queueInputBuffer(inIndex, 0, width * height * 3 / 2, ptsUs, 0)
                    }
                    drain(false)
                }
                // Signal end of stream.
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val ptsUs = frames.size.toLong() * 1_000_000L / fps
                    codec.queueInputBuffer(
                        inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                }
                drain(true)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            outFile
        }

    private fun fillYuv420(frame: Bitmap, image: android.media.Image, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        frame.getPixels(pixels, 0, width, 0, 0, width, height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride

        for (row in 0 until height) {
            for (col in 0 until width) {
                val argb = pixels[row * width + col]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(row * yRowStride + col * yPixStride, y.coerceIn(0, 255).toByte())

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvIndex = (row / 2) * uvRowStride + (col / 2) * uvPixStride
                    uBuf.put(uvIndex, u.coerceIn(0, 255).toByte())
                    vBuf.put(uvIndex, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }
}
