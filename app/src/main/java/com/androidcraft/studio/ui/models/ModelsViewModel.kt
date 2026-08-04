package com.androidcraft.studio.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.core.DeviceCapabilities
import com.androidcraft.studio.data.DownloadState
import com.androidcraft.studio.data.Modality
import com.androidcraft.studio.data.ModelSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update

class ModelsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AndroidCraftApp).container.modelRepository

    val downloadStates: StateFlow<Map<String, DownloadState>> = repo.states

    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.read(app)

    /** Reactive grouping so user-added models appear immediately. */
    val grouped: StateFlow<List<Pair<Modality, List<ModelSpec>>>> = repo.models
        .map { list -> Modality.entries.map { m -> m to list.filter { it.modality == m } }.filter { it.second.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hfToken = MutableStateFlow(repo.hfToken)
    val hfToken: StateFlow<String> = _hfToken.asStateFlow()

    fun onTokenChange(value: String) {
        _hfToken.update { value }
        repo.hfToken = value
    }

    fun download(spec: ModelSpec) = repo.startDownload(spec)
    fun cancel(spec: ModelSpec) = repo.cancelDownload(spec)
    fun delete(spec: ModelSpec) {
        if (spec.custom) repo.removeCustomModel(spec) else repo.deleteModel(spec)
    }

    fun addCustomModel(name: String, url: String, modality: Modality) {
        if (url.isBlank()) return
        repo.addCustomModel(name, url, modality)
    }
}
