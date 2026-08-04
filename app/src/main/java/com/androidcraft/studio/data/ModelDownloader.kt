package com.androidcraft.studio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Streams a model file to disk with progress reporting and cooperative cancellation.
 *
 * Downloads to a `.part` file first and atomically renames on success so a half-written
 * file is never mistaken for a ready model.
 */
class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    /**
     * @param onProgress invoked with (downloadedBytes, totalBytes); totalBytes is -1 if unknown.
     * @throws IOException on any network / IO failure.
     */
    suspend fun download(
        url: String,
        target: File,
        authToken: String?,
        onProgress: suspend (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val partFile = File(target.parentFile, target.name + ".part")
        if (partFile.exists()) partFile.delete()

        val requestBuilder = Request.Builder().url(url)
        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()

            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var downloaded = 0L
                    var lastReported = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        // Throttle UI updates to ~every 512 KB.
                        if (downloaded - lastReported >= 512 * 1024) {
                            onProgress(downloaded, total)
                            lastReported = downloaded
                        }
                    }
                    output.flush()
                    onProgress(downloaded, total)
                }
            }
        }

        if (target.exists()) target.delete()
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        target
    }
}
