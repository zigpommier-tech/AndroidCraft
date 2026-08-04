package com.androidcraft.studio.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidcraft.studio.ui.components.SectionCard

@Composable
fun ImageScreen(viewModel: ImageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Image Generation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Runs on-device with the bundled renderer — no download required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val bmp = state.bitmap
                when {
                    state.isGenerating -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "Rendering…",
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    bmp != null -> {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                        )
                    }

                    else -> {
                        Text(
                            "Your image will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt") },
            placeholder = { Text("a neon galaxy over a calm ocean") },
            shape = RoundedCornerShape(16.dp),
            minLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::generate,
                enabled = state.prompt.isNotBlank() && !state.isGenerating,
                modifier = Modifier.weight(1f),
            ) {
                Text("Generate")
            }
            OutlinedButton(
                onClick = viewModel::regenerateVariation,
                enabled = state.prompt.isNotBlank() && !state.isGenerating,
            ) {
                Icon(Icons.Filled.Casino, contentDescription = "Variation")
            }
            OutlinedButton(
                onClick = viewModel::save,
                enabled = state.bitmap != null && !state.isGenerating,
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Save")
            }
        }

        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        SectionCard {
            Text("Upgrade path", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "The renderer is a drop-in stand-in. Add Stable Diffusion LiteRT weights and point " +
                    "the image engine at them to produce photoreal results from the same prompt box.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
