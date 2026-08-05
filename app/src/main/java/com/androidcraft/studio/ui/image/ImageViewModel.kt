package com.androidcraft.studio.ui.image

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.core.MediaSaver
import com.androidcraft.studio.engine.image.ImageMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageMethodInfo(val method: ImageMethod, val available: Boolean, val hint: String)

data class ImageUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val bitmap: Bitmap? = null,
    val statusMessage: String? = null,
    val error: String? = null,
    val shareUri: Uri? = null,
    val methods: List<ImageMethodInfo> = emptyList(),
    val selected: ImageMethod = ImageMethod.PROCEDURAL,
    val steps: Int = 20,
    val progress: Float = 0f,
)

class ImageViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = (app as AndroidCraftApp).container.imageEngine

    private val _state = MutableStateFlow(ImageUiState())
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

    init {
        refreshMethods()
    }

    fun refreshMethods() {
        val methods = engine.generators()
            .map { gen ->
                val status = gen.status()
                ImageMethodInfo(gen.method, status.available, status.hint)
            }
            // Only surface the ONNX backend once a bundle is actually present, to keep the
            // picker to engines the user can use right now.
            .filter { it.method != ImageMethod.ONNX || it.available }
        _state.update { current ->
            val stillValid = methods.any { it.method == current.selected }
            current.copy(
                methods = methods,
                selected = if (stillValid) current.selected else ImageMethod.PROCEDURAL,
            )
        }
    }

    fun onPromptChange(value: String) = _state.update { it.copy(prompt = value) }
    fun onStepsChange(value: Int) = _state.update { it.copy(steps = value) }
    fun selectMethod(method: ImageMethod) = _state.update { it.copy(selected = method, error = null) }

    fun generate(newSeed: Boolean = false) {
        val current = _state.value
        val prompt = current.prompt.trim()
        if (prompt.isEmpty() || current.isGenerating) return

        val info = current.methods.firstOrNull { it.method == current.selected }
        if (info != null && !info.available) {
            _state.update { it.copy(error = info.hint) }
            return
        }

        _state.update { it.copy(isGenerating = true, error = null, statusMessage = null, progress = 0f) }
        viewModelScope.launch {
            try {
                val seed = if (newSeed) System.nanoTime() else null
                val bmp = engine.generate(current.selected, prompt, seed, current.steps) { p ->
                    _state.update { it.copy(progress = p) }
                }
                _state.update { it.copy(bitmap = bmp, isGenerating = false, progress = 1f) }
            } catch (t: Throwable) {
                _state.update { it.copy(isGenerating = false, error = t.message ?: "Generation failed") }
            }
        }
    }

    private suspend fun export(): Uri? {
        val bmp = _state.value.bitmap ?: return null
        return MediaSaver.saveImage(getApplication(), bmp, "androidcraft_${System.currentTimeMillis()}.png")
    }

    fun save() {
        if (_state.value.bitmap == null) return
        viewModelScope.launch {
            val uri = export()
            _state.update { it.copy(statusMessage = if (uri != null) "Saved to gallery" else "Save failed") }
        }
    }

    fun share() {
        if (_state.value.bitmap == null) return
        viewModelScope.launch {
            val uri = export()
            if (uri != null) _state.update { it.copy(shareUri = uri) }
            else _state.update { it.copy(statusMessage = "Save failed") }
        }
    }

    fun onShareConsumed() = _state.update { it.copy(shareUri = null) }
}
