package com.androidcraft.studio.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.androidcraft.studio.data.ModelSpec
import com.androidcraft.studio.ui.components.SectionCard
import com.androidcraft.studio.ui.theme.CraftError
import com.androidcraft.studio.ui.theme.CraftSuccess

@Composable
fun ModelsScreen(viewModel: ModelsViewModel = viewModel()) {
    val states by viewModel.downloadStates.collectAsStateWithLifecycle()
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()
    val caps = viewModel.deviceCapabilities

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Models", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pick a model and tap download — everything runs on your device. No account, no " +
                    "links to find. Models are matched to your phone's memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item { DeviceCard(caps) }

        grouped.forEach { (modality, specs) ->
            // Order by device fit (best first), largest-quality first; built-ins last.
            val ordered = specs.sortedWith(
                compareBy(
                    { it.isBundled },
                    { caps.canRun(it).ordinal },
                    { -it.approxBytes },
                ),
            )
            // Recommend the highest-quality downloadable model that still runs well.
            val recommendedId = ordered.firstOrNull {
                !it.isBundled && caps.canRun(it) == CapabilityVerdict.GOOD
            }?.id

            item {
                Text(
                    modality.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(ordered.size) { index ->
                val spec = ordered[index]
                ModelCard(
                    spec = spec,
                    state = states[spec.id] ?: DownloadState.NotDownloaded,
                    verdict = caps.canRun(spec),
                    recommended = spec.id == recommendedId,
                    onDownload = { viewModel.download(spec) },
                    onCancel = { viewModel.cancel(spec) },
                    onDelete = { viewModel.delete(spec) },
                )
            }
        }
    }
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
    recommended: Boolean,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (recommended) RecommendedBadge()
                }
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

        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!spec.isBundled) {
                MetaChip(spec.approxSizeLabel())
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
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
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
private fun RecommendedBadge() {
    Surface(color = CraftSuccess.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(
            "Best for your device",
            style = MaterialTheme.typography.labelSmall,
            color = CraftSuccess,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
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
