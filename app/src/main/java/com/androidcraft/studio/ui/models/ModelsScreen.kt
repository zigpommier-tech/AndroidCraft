package com.androidcraft.studio.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidcraft.studio.core.CapabilityVerdict
import com.androidcraft.studio.core.DeviceCapabilities
import com.androidcraft.studio.data.DownloadState
import com.androidcraft.studio.data.Modality
import com.androidcraft.studio.data.ModelSpec
import com.androidcraft.studio.ui.components.SectionCard
import com.androidcraft.studio.ui.theme.CraftError
import com.androidcraft.studio.ui.theme.CraftSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: ModelsViewModel = viewModel()) {
    val states by viewModel.downloadStates.collectAsStateWithLifecycle()
    val token by viewModel.hfToken.collectAsStateWithLifecycle()
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddModelDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, modality ->
                viewModel.addCustomModel(name, url, modality)
                showAddDialog = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Models",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Add by URL")
                }
            }
        }

        item { DeviceCard(viewModel.deviceCapabilities) }

        item {
            SectionCard {
                Text("Hugging Face token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Optional. Most models download with no token. A read token is only needed for " +
                        "license-gated repositories. Stored only on this device and sent solely to the " +
                        "model host when downloading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = viewModel::onTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("hf_… (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }

        grouped.forEach { (modality, specs) ->
            item {
                Text(
                    modality.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(specs.size) { index ->
                val spec = specs[index]
                ModelCard(
                    spec = spec,
                    state = states[spec.id] ?: DownloadState.NotDownloaded,
                    verdict = viewModel.deviceCapabilities.canRun(spec),
                    onDownload = { viewModel.download(spec) },
                    onCancel = { viewModel.cancel(spec) },
                    onDelete = { viewModel.delete(spec) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, modality: Modality) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var modality by remember { mutableStateOf(Modality.TEXT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a model by URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = modality == Modality.TEXT,
                        onClick = { modality = Modality.TEXT },
                        label = { Text("Chat (.task)") },
                    )
                    FilterChip(
                        selected = modality == Modality.IMAGE,
                        onClick = { modality = Modality.IMAGE },
                        label = { Text("Image (SD .gguf)") },
                    )
                }
                Text(
                    if (modality == Modality.TEXT) {
                        "Direct link to a LiteRT .task chat model (e.g. a Hugging Face resolve URL)."
                    } else {
                        "Direct link to a Stable Diffusion .gguf/.safetensors model for the on-device " +
                            "diffusion engine."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Model URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "Custom model" }, url, modality) },
                enabled = url.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeviceCard(caps: DeviceCapabilities) {
    SectionCard {
        Text("This device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(caps.model, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
        Text(
            "RAM ${caps.totalRamLabel} · ${caps.availableRamLabel} free · " +
                "${caps.abis.firstOrNull() ?: "unknown ABI"} · API ${caps.sdkInt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    state: DownloadState,
    verdict: CapabilityVerdict,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    spec.publisher,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionSlot(spec, state, onDownload, onCancel, onDelete)
        }

        Text(
            spec.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!spec.isBundled) {
                MetaChip("${spec.approxSizeLabel()}")
                if (spec.quantization.isNotEmpty()) MetaChip(spec.quantization)
                MetaChip(spec.runtime.label)
                if (spec.minRamBytes > 0) VerdictChip(verdict)
            } else {
                MetaChip("Built-in")
                MetaChip("No download")
            }
        }

        if (state is DownloadState.Downloading) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${(state.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state is DownloadState.Failed) {
            Text(
                state.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActionSlot(
    spec: ModelSpec,
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    if (spec.isBundled) {
        Icon(Icons.Filled.CheckCircle, contentDescription = "Ready", tint = CraftSuccess)
        return
    }
    when (state) {
        is DownloadState.Downloading -> IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel")
        }

        is DownloadState.Ready -> IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = CraftError)
        }

        else -> IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Download")
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun VerdictChip(verdict: CapabilityVerdict) {
    val color = when (verdict) {
        CapabilityVerdict.GOOD -> CraftSuccess
        CapabilityVerdict.TIGHT -> Color(0xFFF59E0B)
        CapabilityVerdict.INSUFFICIENT -> CraftError
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            verdict.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
