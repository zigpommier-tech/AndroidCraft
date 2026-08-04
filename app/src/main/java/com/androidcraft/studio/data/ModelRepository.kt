package com.androidcraft.studio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Single source of truth for the model list, download state, and on-disk model files.
 *
 * The model list is reactive: it is the built-in [ModelCatalog] plus any models the user adds
 * by URL (persisted on-device). Downloads report progress through [states]. A Hugging Face token
 * is entirely optional — it is sent as a bearer token only when the user has provided one, so
 * ungated models need no token at all.
 */
class ModelRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val downloader: ModelDownloader = ModelDownloader(),
) {
    private val appContext = context.applicationContext
    private val modelsDir: File = File(appContext.filesDir, "models").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("androidcraft.prefs", Context.MODE_PRIVATE)

    private val _models = MutableStateFlow(ModelCatalog.models + loadCustomModels())
    val models: StateFlow<List<ModelSpec>> = _models.asStateFlow()

    private val _states = MutableStateFlow(initialStates(_models.value))
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    private fun initialStates(specs: List<ModelSpec>): Map<String, DownloadState> =
        specs.associate { spec ->
            spec.id to if (spec.isBundled || isReady(spec)) DownloadState.Ready
            else DownloadState.NotDownloaded
        }

    fun fileFor(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    fun extractDirFor(spec: ModelSpec): File = File(modelsDir, spec.extractDir)

    fun isReady(spec: ModelSpec): Boolean = when {
        spec.isBundled -> true
        spec.archive -> extractDirFor(spec).let { it.isDirectory && (it.list()?.isNotEmpty() == true) }
        else -> spec.fileName.isNotEmpty() && fileFor(spec).exists() && fileFor(spec).length() > 0
    }

    fun stateFor(id: String): DownloadState = _states.value[id] ?: DownloadState.NotDownloaded

    fun modelsFor(modality: Modality): List<ModelSpec> = _models.value.filter { it.modality == modality }

    // ---- Hugging Face token (optional; only used for gated downloads) ----
    var hfToken: String
        get() = prefs.getString(KEY_HF_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HF_TOKEN, value.trim()).apply()

    // ---- Downloads ----
    fun startDownload(spec: ModelSpec) {
        val url = spec.downloadUrl ?: return
        if (activeJobs.containsKey(spec.id)) return
        setState(spec.id, DownloadState.Downloading(0, spec.approxBytes))

        val job = scope.launch {
            try {
                downloader.download(
                    url = url,
                    target = fileFor(spec),
                    // Token is optional: sent only when the user supplied one.
                    authToken = hfToken.ifBlank { null },
                ) { downloaded, total ->
                    val effectiveTotal = if (total > 0) total else spec.approxBytes
                    setState(spec.id, DownloadState.Downloading(downloaded, effectiveTotal))
                }
                if (spec.archive) {
                    extractZip(fileFor(spec), extractDirFor(spec))
                    fileFor(spec).delete()
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
        if (spec.archive) extractDirFor(spec).deleteRecursively()
        setState(spec.id, DownloadState.NotDownloaded)
    }

    private fun extractZip(zip: File, destDir: File) {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Guard against zip path traversal.
                if (!outFile.canonicalPath.startsWith(destCanonical)) {
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ---- Custom (user-added) models ----
    fun addCustomModel(displayName: String, url: String, modality: Modality): ModelSpec {
        val cleanName = displayName.trim().ifBlank { "Custom model" }
        val id = "custom-" + System.nanoTime().toString(36)
        val safe = cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val ext = if (modality == Modality.TEXT) ".task" else ".gguf"
        val spec = ModelSpec(
            id = id,
            displayName = cleanName,
            publisher = "Added by you",
            modality = modality,
            runtime = if (modality == Modality.TEXT) Runtime.MEDIAPIPE_LLM else Runtime.DIFFUSION,
            description = "Custom model added from a URL. Runs on-device once downloaded.",
            downloadUrl = url.trim(),
            fileName = "$safe-$id$ext",
            approxBytes = 0,
            minRamBytes = 0,
            custom = true,
        )
        _models.update { it + spec }
        setState(spec.id, DownloadState.NotDownloaded)
        persistCustomModels()
        return spec
    }

    fun removeCustomModel(spec: ModelSpec) {
        if (!spec.custom) return
        cancelDownload(spec)
        fileFor(spec).delete()
        _models.update { list -> list.filterNot { it.id == spec.id } }
        _states.update { it - spec.id }
        persistCustomModels()
    }

    private fun persistCustomModels() {
        val array = JSONArray()
        _models.value.filter { it.custom }.forEach { spec ->
            array.put(
                JSONObject()
                    .put("id", spec.id)
                    .put("name", spec.displayName)
                    .put("url", spec.downloadUrl)
                    .put("fileName", spec.fileName)
                    .put("modality", spec.modality.name),
            )
        }
        prefs.edit().putString(KEY_CUSTOM_MODELS, array.toString()).apply()
    }

    private fun loadCustomModels(): List<ModelSpec> {
        val raw = prefs.getString(KEY_CUSTOM_MODELS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val modality = runCatching { Modality.valueOf(o.getString("modality")) }
                    .getOrDefault(Modality.TEXT)
                ModelSpec(
                    id = o.getString("id"),
                    displayName = o.getString("name"),
                    publisher = "Added by you",
                    modality = modality,
                    runtime = if (modality == Modality.TEXT) Runtime.MEDIAPIPE_LLM else Runtime.DIFFUSION,
                    description = "Custom model added from a URL. Runs on-device once downloaded.",
                    downloadUrl = o.getString("url"),
                    fileName = o.getString("fileName"),
                    approxBytes = 0,
                    minRamBytes = 0,
                    custom = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun setState(id: String, state: DownloadState) {
        _states.update { it + (id to state) }
    }

    companion object {
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_CUSTOM_MODELS = "custom_models"

        @Volatile
        private var instance: ModelRepository? = null

        fun get(context: Context): ModelRepository =
            instance ?: synchronized(this) {
                instance ?: ModelRepository(context.applicationContext).also { instance = it }
            }
    }
}
