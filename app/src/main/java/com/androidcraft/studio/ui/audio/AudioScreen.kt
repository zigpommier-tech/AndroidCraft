package com.androidcraft.studio.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidcraft.studio.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(viewModel: AudioViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadVoices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Voice Generation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Text-to-speech powered by your device's on-device neural voices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text to speak") },
            placeholder = { Text("Hello from AndroidCraft, running fully on-device.") },
            shape = RoundedCornerShape(16.dp),
            minLines = 3,
        )

        SectionCard {
            Text("Speed  ${"%.1f".format(state.speed)}x", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.speed,
                onValueChange = viewModel::onSpeedChange,
                valueRange = 0.5f..2.0f,
            )
            Text("Pitch  ${"%.1f".format(state.pitch)}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.pitch,
                onValueChange = viewModel::onPitchChange,
                valueRange = 0.5f..2.0f,
            )
        }

        if (state.voices.isNotEmpty()) {
            Text("Voice", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.voices.take(6).forEach { voice ->
                    FilterChip(
                        selected = state.selectedVoice == voice,
                        onClick = { viewModel.selectVoice(voice) },
                        label = { Text(voice, maxLines = 1) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::synthesize,
                enabled = state.text.isNotBlank() && !state.isSynthesizing,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isSynthesizing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                } else {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Text("  Synthesize")
                }
            }
            OutlinedButton(
                onClick = viewModel::togglePlayback,
                enabled = state.hasAudio,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                )
            }
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
