package com.androidcraft.studio.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves generated media into the shared gallery / media collections using MediaStore
 * (no storage permission needed on any supported API level), and builds share intents.
 */
object MediaSaver {

    suspend fun saveImage(context: Context, bitmap: Bitmap, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            val collection = imagesCollection()
            val values = baseValues(displayName, "image/png", Environment.DIRECTORY_PICTURES)
            val uri = context.contentResolver.insert(collection, values) ?: return@withContext null
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }.onFailure { return@withContext null }
            finalizePending(context, uri)
            uri
        }

    /** Copies a synthesized WAV into the Music collection. */
    suspend fun saveAudio(context: Context, wavFile: File, displayName: String): Uri? =
        copyFile(
            context = context,
            src = wavFile,
            collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            },
            values = baseValues(displayName, "audio/x-wav", Environment.DIRECTORY_MUSIC),
        )

    /** Copies a generated MP4 clip into the Movies collection. */
    suspend fun saveVideo(context: Context, mp4File: File, displayName: String): Uri? =
        copyFile(
            context = context,
            src = mp4File,
            collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            },
            values = baseValues(displayName, "video/mp4", Environment.DIRECTORY_MOVIES),
        )

    fun shareIntent(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    // ---- internals ----

    private fun imagesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun baseValues(displayName: String, mime: String, dir: String): ContentValues =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/AndroidCraft")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

    private fun finalizePending(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
    }

    private suspend fun copyFile(
        context: Context,
        src: File,
        collection: Uri,
        values: ContentValues,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!src.exists()) return@withContext null
        val uri = context.contentResolver.insert(collection, values) ?: return@withContext null
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { input -> input.copyTo(out) }
            }
        }.onFailure { return@withContext null }
        finalizePending(context, uri)
        uri
    }
}
