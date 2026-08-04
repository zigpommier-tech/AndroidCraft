package com.androidcraft.studio.ui.audio

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidcraft.studio.AndroidCraftApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AudioUiState(
    val text: String = "",
    val isSynthesizing: Boolean = false,
    val hasAudio: Boolean = false,
    val isPlaying: Boolean = false,
    val voices: List<String> = emptyList(),
    val selectedVoice: String? = null,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val error: String? = null,
)

class AudioViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = (app as AndroidCraftApp).container.audioEngine
    private var player: MediaPlayer? = null
    private var lastFile: File? = null

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    fun onTextChange(value: String) = _state.update { it.copy(text = value) }
    fun onSpeedChange(value: Float) = _state.update { it.copy(speed = value) }
    fun onPitchChange(value: Float) = _state.update { it.copy(pitch = value) }
    fun selectVoice(name: String) = _state.update { it.copy(selectedVoice = name) }

    fun loadVoices() {
        viewModelScope.launch {
            if (engine.ensureReady()) {
                val voices = engine.availableVoices()
                _state.update {
                    it.copy(
                        voices = voices,
                        selectedVoice = it.selectedVoice ?: voices.firstOrNull(),
                    )
                }
            } else {
                _state.update { it.copy(error = "No text-to-speech engine available on this device.") }
            }
        }
    }

    fun synthesize() {
        val text = _state.value.text.trim()
        if (text.isEmpty() || _state.value.isSynthesizing) return
        _state.update { it.copy(isSynthesizing = true, error = null) }
        viewModelScope.launch {
            try {
                if (!engine.ensureReady()) {
                    _state.update { it.copy(isSynthesizing = false, error = "TTS engine unavailable.") }
                    return@launch
                }
                _state.value.selectedVoice?.let(engine::setVoice)
                engine.setSpeed(_state.value.speed)
                engine.setPitch(_state.value.pitch)
                val file = engine.synthesizeToFile(text, "androidcraft_tts.wav")
                if (file != null) {
                    lastFile = file
                    _state.update { it.copy(isSynthesizing = false, hasAudio = true) }
                } else {
                    _state.update { it.copy(isSynthesizing = false, error = "Synthesis failed.") }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isSynthesizing = false, error = t.message) }
            }
        }
    }

    fun togglePlayback() {
        val file = lastFile ?: return
        val current = player
        if (current != null && current.isPlaying) {
            current.pause()
            _state.update { it.copy(isPlaying = false) }
            return
        }
        try {
            val mp = current ?: MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
            player = mp
            mp.start()
            _state.update { it.copy(isPlaying = true) }
        } catch (t: Throwable) {
            _state.update { it.copy(error = "Playback failed: ${t.message}") }
        }
    }

    override fun onCleared() {
        player?.release()
        player = null
        engine.shutdown()
        super.onCleared()
    }
}
