package com.androidcraft.studio.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanBytesTest {

    @Test
    fun zeroOrNegativeRendersDash() {
        assertEquals("—", humanBytes(0))
        assertEquals("—", humanBytes(-5))
    }

    @Test
    fun smallValuesUseBytes() {
        assertEquals("512 B", humanBytes(512))
    }

    @Test
    fun kilobytesGetOneDecimal() {
        assertEquals("1.5 KB", humanBytes(1536))
    }

    @Test
    fun largeValuesRoundToWholeUnits() {
        assertEquals("555 MB", humanBytes(555L * 1024 * 1024))
    }

    @Test
    fun downloadFractionIsClamped() {
        val state = DownloadState.Downloading(downloadedBytes = 200, totalBytes = 100)
        assertEquals(1f, state.fraction)
    }
}
