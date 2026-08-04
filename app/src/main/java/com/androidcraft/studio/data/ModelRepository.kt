package com.androidcraft.studio.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single source of truth for model download state and on-disk model files.
 *
 * Exposes a [StateFlow] of per-model [DownloadState] so any screen can observe progress.
 * Held as an application-scoped singleton so a download survives screen navigation.
 */
class ModelRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val downloader: ModelDownloader = ModelDownloader(),
) {
    private val appContext = context.applicationContext
    private val modelsDir: File = File(appContext.filesDir, "models").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("androidcraft.prefs", Context.MODE_PRIVATE)

    private val _states = MutableStateFlow(initialStates())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    private fun initialStates(): Map<String, DownloadState> =
        ModelCatalog.models.associate { spec ->
            spec.id to if (spec.isBundled || isReady(spec)) DownloadState.Ready
            else DownloadState.NotDownloaded
        }

    fun fileFor(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    fun isReady(spec: ModelSpec): Boolean =
        spec.isBundled || (spec.fileName.isNotEmpty() && fileFor(spec).exists() && fileFor(spec).length() > 0)

    fun stateFor(id: String): DownloadState = _states.value[id] ?: DownloadState.NotDownloaded

    // ---- Hugging Face token (for gated downloads) ----
    var hfToken: String
        get() = prefs.getString(KEY_HF_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HF_TOKEN, value.trim()).apply()

    fun startDownload(spec: ModelSpec) {
        val url = spec.downloadUrl ?: return
        if (activeJobs.containsKey(spec.id)) return
        setState(spec.id, DownloadState.Downloading(0, spec.approxBytes))

        val job = scope.launch {
            try {
                downloader.download(
                    url = url,
                    target = fileFor(spec),
                    authToken = if (spec.gated) hfToken.ifBlank { null } else null,
                ) { downloaded, total ->
                    val effectiveTotal = if (total > 0) total else spec.approxBytes
                    setState(spec.id, DownloadState.Downloading(downloaded, effectiveTotal))
                }
                setState(spec.id, DownloadState.Ready)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    setState(spec.id, DownloadState.NotDownloaded)
                    throw t
                }
                setState(spec.id, DownloadState.Failed(t.message ?: "Download failed"))
            } finally {
                activeJobs.remove(spec.id)
            }
        }
        activeJobs[spec.id] = job
    }

    fun cancelDownload(spec: ModelSpec) {
        activeJobs.remove(spec.id)?.cancel()
        File(modelsDir, spec.fileName + ".part").delete()
        setState(spec.id, DownloadState.NotDownloaded)
    }

    fun deleteModel(spec: ModelSpec) {
        if (spec.isBundled) return
        cancelDownload(spec)
        fileFor(spec).delete()
        setState(spec.id, DownloadState.NotDownloaded)
    }

    private fun setState(id: String, state: DownloadState) {
        _states.update { it + (id to state) }
    }

    companion object {
        private const val KEY_HF_TOKEN = "hf_token"

        @Volatile
        private var instance: ModelRepository? = null

        fun get(context: Context): ModelRepository =
            instance ?: synchronized(this) {
                instance ?: ModelRepository(context.applicationContext).also { instance = it }
            }
    }
}
