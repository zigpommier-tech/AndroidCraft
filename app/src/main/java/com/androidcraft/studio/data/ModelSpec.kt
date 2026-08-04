package com.androidcraft.studio.data

/** The generative capabilities the app exposes. */
enum class Modality(val label: String) {
    TEXT("Text / Chat"),
    IMAGE("Image"),
    AUDIO("Audio / Voice"),
    VIDEO("Video"),
}

/** Runtime that knows how to execute a given model file on-device. */
enum class Runtime(val label: String) {
    /** Google AI Edge / MediaPipe GenAI (.task / .litertlm bundles). */
    MEDIAPIPE_LLM("MediaPipe GenAI"),

    /** Android's built-in neural text-to-speech engine (no download needed). */
    ANDROID_TTS("System TTS"),

    /** On-device diffusion weights driven by the image pipeline. */
    DIFFUSION("Diffusion"),

    /** Procedural / experimental renderer bundled in the app. */
    BUILTIN("Built-in"),
}

/**
 * A model the user can browse, download, and run.
 *
 * [approxBytes] is used both for the UI and for a rough RAM-fit check against the device.
 * [minRamBytes] is the recommended device memory to run the model comfortably.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val publisher: String,
    val modality: Modality,
    val runtime: Runtime,
    val description: String,
    val downloadUrl: String?,
    val fileName: String,
    val approxBytes: Long,
    val minRamBytes: Long,
    val quantization: String = "",
    val gated: Boolean = false,
    val custom: Boolean = false,
) {
    val isBundled: Boolean get() = downloadUrl == null

    fun approxSizeLabel(): String = humanBytes(approxBytes)
    fun minRamLabel(): String = humanBytes(minRamBytes)
}

/** Where a model currently stands on this device. */
sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    data object Ready : DownloadState
    data class Failed(val message: String) : DownloadState
}

fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100 || unit == 0) {
        "${value.toInt()} ${units[unit]}"
    } else {
        String.format("%.1f %s", value, units[unit])
    }
}
