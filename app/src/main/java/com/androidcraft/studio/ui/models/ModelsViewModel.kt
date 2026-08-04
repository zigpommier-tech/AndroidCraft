package com.androidcraft.studio.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.core.DeviceCapabilities
import com.androidcraft.studio.data.DownloadState
import com.androidcraft.studio.data.Modality
import com.androidcraft.studio.data.ModelCatalog
import com.androidcraft.studio.data.ModelSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModelsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AndroidCraftApp).container.modelRepository

    val downloadStates: StateFlow<Map<String, DownloadState>> = repo.states

    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.read(app)

    private val _hfToken = MutableStateFlow(repo.hfToken)
    val hfToken: StateFlow<String> = _hfToken.asStateFlow()

    val grouped: List<Pair<Modality, List<ModelSpec>>> =
        Modality.entries.map { it to ModelCatalog.forModality(it) }.filter { it.second.isNotEmpty() }

    fun onTokenChange(value: String) {
        _hfToken.update { value }
        repo.hfToken = value
    }

    fun download(spec: ModelSpec) = repo.startDownload(spec)
    fun cancel(spec: ModelSpec) = repo.cancelDownload(spec)
    fun delete(spec: ModelSpec) = repo.deleteModel(spec)
}
