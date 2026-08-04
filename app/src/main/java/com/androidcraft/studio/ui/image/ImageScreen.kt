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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidcraft.studio.core.MediaSaver
import com.androidcraft.studio.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(viewModel: ImageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshMethods() }

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            context.startActivity(
                android.content.Intent.createChooser(MediaSaver.shareIntent(uri, "image/png"), "Share image"),
            )
            viewModel.onShareConsumed()
        }
    }

    val selectedInfo = state.methods.firstOrNull { it.method == state.selected }
    val diffusion = state.selected.diffusion

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
            "Choose a generation method — all run fully on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- Method selector ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.methods.forEach { info ->
                FilterChip(
                    selected = state.selected == info.method,
                    onClick = { viewModel.selectMethod(info.method) },
                    label = { Text(info.method.displayName) },
                )
            }
        }
        selectedInfo?.let {
            Text(
                it.hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (it.available) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val bmp = state.bitmap
                when {
                    state.isGenerating -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                if (diffusion) "Diffusing… ${(state.progress * 100).toInt()}%" else "Rendering…",
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (diffusion) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                )
                            }
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

        if (diffusion) {
            SectionCard {
                Text("Sampling steps  ${state.steps}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.steps.toFloat(),
                    onValueChange = { viewModel.onStepsChange(it.toInt()) },
                    valueRange = 4f..40f,
                )
                Text(
                    "More steps = higher quality, slower. On-device diffusion is CPU-bound.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.generate() },
                enabled = state.prompt.isNotBlank() && !state.isGenerating,
                modifier = Modifier.weight(1f),
            ) {
                Text("Generate")
            }
            OutlinedButton(
                onClick = { viewModel.generate(newSeed = true) },
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
            OutlinedButton(
                onClick = viewModel::share,
                enabled = state.bitmap != null && !state.isGenerating,
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
        }

        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        SectionCard {
            Text("On-device engines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Procedural is instant and always available. The diffusion engines (stable-diffusion.cpp " +
                    "and ONNX Runtime) produce real text-to-image results from a model you download in the " +
                    "Models tab, and run fully offline on the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
