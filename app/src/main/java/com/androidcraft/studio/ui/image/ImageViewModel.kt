package com.androidcraft.studio.ui.image

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.core.MediaSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val bitmap: Bitmap? = null,
    val statusMessage: String? = null,
    val error: String? = null,
    val shareUri: Uri? = null,
)

class ImageViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as AndroidCraftApp).container
    private val engine = container.imageEngine

    private val _state = MutableStateFlow(ImageUiState())
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

    fun onPromptChange(value: String) = _state.update { it.copy(prompt = value) }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return
        _state.update { it.copy(isGenerating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            try {
                val bmp = engine.render(prompt)
                _state.update { it.copy(bitmap = bmp, isGenerating = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isGenerating = false, error = t.message) }
            }
        }
    }

    fun regenerateVariation() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return
        _state.update { it.copy(isGenerating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            try {
                val bmp = engine.render(prompt, seed = System.nanoTime())
                _state.update { it.copy(bitmap = bmp, isGenerating = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isGenerating = false, error = t.message) }
            }
        }
    }

    private suspend fun export(): Uri? {
        val bmp = _state.value.bitmap ?: return null
        return MediaSaver.saveImage(
            getApplication(),
            bmp,
            "androidcraft_${System.currentTimeMillis()}.png",
        )
    }

    fun save() {
        if (_state.value.bitmap == null) return
        viewModelScope.launch {
            val uri = export()
            _state.update {
                it.copy(statusMessage = if (uri != null) "Saved to gallery" else "Save failed")
            }
        }
    }

    fun share() {
        if (_state.value.bitmap == null) return
        viewModelScope.launch {
            val uri = export()
            if (uri != null) {
                _state.update { it.copy(shareUri = uri) }
            } else {
                _state.update { it.copy(statusMessage = "Save failed") }
            }
        }
    }

    fun onShareConsumed() = _state.update { it.copy(shareUri = null) }
}
