package com.androidcraft.studio.ui.video

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.core.MediaSaver
import com.androidcraft.studio.engine.VideoEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VideoUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val frames: List<Bitmap> = emptyList(),
    val currentFrame: Int = 0,
    val isPlaying: Boolean = false,
    val fps: Int = 12,
    val error: String? = null,
    val isExporting: Boolean = false,
    val statusMessage: String? = null,
    val shareUri: Uri? = null,
)

class VideoViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = (app as AndroidCraftApp).container.videoEngine

    private val _state = MutableStateFlow(VideoUiState())
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null

    fun onPromptChange(value: String) = _state.update { it.copy(prompt = value) }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return
        playbackJob?.cancel()
        _state.update {
            it.copy(isGenerating = true, progress = 0f, frames = emptyList(), error = null, isPlaying = false)
        }
        viewModelScope.launch {
            try {
                val clip = engine.render(prompt) { done, total ->
                    _state.update { it.copy(progress = done.toFloat() / total) }
                }
                _state.update {
                    it.copy(isGenerating = false, frames = clip.frames, fps = clip.fps, currentFrame = 0)
                }
                startPlayback()
            } catch (t: Throwable) {
                _state.update { it.copy(isGenerating = false, error = t.message) }
            }
        }
    }

    fun togglePlayback() {
        if (_state.value.isPlaying) {
            playbackJob?.cancel()
            _state.update { it.copy(isPlaying = false) }
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val frames = _state.value.frames
        if (frames.isEmpty()) return
        playbackJob?.cancel()
        _state.update { it.copy(isPlaying = true) }
        val frameDelay = (1000L / _state.value.fps).coerceAtLeast(16L)
        playbackJob = viewModelScope.launch {
            while (isActive) {
                delay(frameDelay)
                _state.update {
                    val next = (it.currentFrame + 1) % it.frames.size
                    it.copy(currentFrame = next)
                }
            }
        }
    }

    private suspend fun exportToFile(): Uri? {
        val frames = _state.value.frames
        if (frames.isEmpty()) return null
        val app = getApplication<Application>()
        val out = File(app.cacheDir, "androidcraft_clip.mp4")
        VideoEncoder.encodeMp4(frames, _state.value.fps, out)
        return MediaSaver.saveVideo(app, out, "androidcraft_${System.currentTimeMillis()}.mp4")
    }

    fun save() {
        if (_state.value.frames.isEmpty() || _state.value.isExporting) return
        _state.update { it.copy(isExporting = true, statusMessage = null, error = null) }
        viewModelScope.launch {
            try {
                val uri = exportToFile()
                _state.update {
                    it.copy(
                        isExporting = false,
                        statusMessage = if (uri != null) "Saved to Movies" else "Export failed",
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isExporting = false, error = "Export failed: ${t.message}") }
            }
        }
    }

    fun share() {
        if (_state.value.frames.isEmpty() || _state.value.isExporting) return
        _state.update { it.copy(isExporting = true, statusMessage = null, error = null) }
        viewModelScope.launch {
            try {
                val uri = exportToFile()
                _state.update {
                    if (uri != null) it.copy(isExporting = false, shareUri = uri)
                    else it.copy(isExporting = false, statusMessage = "Export failed")
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isExporting = false, error = "Export failed: ${t.message}") }
            }
        }
    }

    fun onShareConsumed() = _state.update { it.copy(shareUri = null) }

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
