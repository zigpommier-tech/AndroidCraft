package com.androidcraft.studio.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.androidcraft.studio.data.ModelSpec
import com.androidcraft.studio.data.humanBytes

/** Snapshot of what this device can realistically run. */
data class DeviceCapabilities(
    val model: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val abis: List<String>,
    val supportsArm64: Boolean,
    val sdkInt: Int,
) {
    val totalRamLabel: String get() = humanBytes(totalRamBytes)
    val availableRamLabel: String get() = humanBytes(availableRamBytes)

    /** Rough "can I run this comfortably?" check against a model's stated needs. */
    fun canRun(spec: ModelSpec): CapabilityVerdict {
        if (spec.minRamBytes <= 0) return CapabilityVerdict.GOOD
        return when {
            totalRamBytes >= spec.minRamBytes + GB -> CapabilityVerdict.GOOD
            totalRamBytes >= spec.minRamBytes -> CapabilityVerdict.TIGHT
            else -> CapabilityVerdict.INSUFFICIENT
        }
    }

    companion object {
        private const val GB = 1024L * 1024L * 1024L

        fun read(context: Context): DeviceCapabilities {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
            return DeviceCapabilities(
                model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                totalRamBytes = memInfo.totalMem,
                availableRamBytes = memInfo.availMem,
                abis = abis,
                supportsArm64 = abis.any { it.contains("arm64") },
                sdkInt = Build.VERSION.SDK_INT,
            )
        }
    }
}

enum class CapabilityVerdict(val label: String) {
    GOOD("Runs well"),
    TIGHT("Tight fit"),
    INSUFFICIENT("Not enough RAM"),
}
