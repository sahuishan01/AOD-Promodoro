package com.algosculptor.pomodoro.data.background

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCatalogTest {

    @Test
    fun allBundledBackgroundsGenerateValidNonThrowingBrushes() {
        assertTrue(BackgroundCatalog.all.isNotEmpty())
        for (bg in BackgroundCatalog.all) {
            val brush = bg.toBrush()
            assertNotNull(brush)
        }
    }

    @Test
    fun singleColorBackgroundToBrushDoesNotThrow() {
        val singleColor = BundledBackground("test", "Test", listOf(0xFF0B0E14), 0xFFFFFFFF)
        val brush = singleColor.toBrush()
        assertNotNull(brush)
    }

    @Test
    fun multiColorBackgroundToBrushDoesNotThrow() {
        val multiColor = BundledBackground("test2", "Test2", listOf(0xFF14101F, 0xFF241A38), 0xFFFFFFFF)
        val brush = multiColor.toBrush()
        assertNotNull(brush)
    }
}
