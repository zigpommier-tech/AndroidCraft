package com.androidcraft.studio.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidcraft.studio.AndroidCraftApp
import com.androidcraft.studio.data.DownloadState
import com.androidcraft.studio.data.Modality
import com.androidcraft.studio.data.ModelCatalog
import com.androidcraft.studio.data.ModelSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val activeModel: ModelSpec? = null,
    val readyModels: List<ModelSpec> = emptyList(),
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as AndroidCraftApp).container
    private val repo = container.modelRepository
    private val engine = container.textEngine

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        refreshModels()
    }

    fun refreshModels() {
        val ready = ModelCatalog.forModality(Modality.TEXT)
            .filter { repo.states.value[it.id] is DownloadState.Ready || repo.isReady(it) }
        _state.update { current ->
            current.copy(
                readyModels = ready,
                activeModel = current.activeModel?.takeIf { it in ready } ?: ready.firstOrNull(),
            )
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun selectModel(spec: ModelSpec) = _state.update { it.copy(activeModel = spec) }

    fun clearConversation() = _state.update { it.copy(messages = emptyList(), error = null) }

    fun send() {
        val current = _state.value
        val prompt = current.input.trim()
        if (prompt.isEmpty() || current.isGenerating) return

        val model = current.activeModel
        if (model == null) {
            _state.update {
                it.copy(error = "No text model is ready. Download one from the Models tab first.")
            }
            return
        }

        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(fromUser = true, text = prompt),
                input = "",
                isGenerating = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                engine.ensureLoaded(repo.fileFor(model))
                val reply = engine.generate(buildPrompt(model, prompt))
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(fromUser = false, text = reply.trim()),
                        isGenerating = false,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error = "Generation failed: ${t.message}",
                    )
                }
            }
        }
    }

    /** Apply a light chat template for Gemma-family models; pass through otherwise. */
    private fun buildPrompt(model: ModelSpec, userText: String): String =
        if (model.id.contains("gemma")) {
            "<start_of_turn>user\n$userText<end_of_turn>\n<start_of_turn>model\n"
        } else {
            userText
        }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
