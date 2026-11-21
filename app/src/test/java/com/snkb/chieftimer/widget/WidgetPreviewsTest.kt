package com.snkb.chieftimer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetPreviewsTest {

    @Test
    fun previewSignatureTracksThemeLanguageAndPaletteColors() {
        val base = WidgetPreviews.previewSignature(
            theme = "clash",
            lang = "pt",
            panel = 101,
            gold = 0xFFFFD863.toInt(),
            sub = 0xFFE6D8C3.toInt(),
            isMaterial = false,
        )

        assertEquals(
            base,
            WidgetPreviews.previewSignature(
                theme = "clash",
                lang = "pt",
                panel = 101,
                gold = 0xFFFFD863.toInt(),
                sub = 0xFFE6D8C3.toInt(),
                isMaterial = false,
            ),
        )
        assertNotEquals(
            base,
            WidgetPreviews.previewSignature(
                theme = "dark",
                lang = "pt",
                panel = 101,
                gold = 0xFFFFD863.toInt(),
                sub = 0xFFE6D8C3.toInt(),
                isMaterial = false,
            ),
        )
        assertNotEquals(
            base,
            WidgetPreviews.previewSignature(
                theme = "clash",
                lang = "en",
                panel = 101,
                gold = 0xFFFFD863.toInt(),
                sub = 0xFFE6D8C3.toInt(),
                isMaterial = false,
            ),
        )
        assertNotEquals(
            base,
            WidgetPreviews.previewSignature(
                theme = "clash",
                lang = "pt",
                panel = 101,
                gold = 0xFFA8D8FF.toInt(),
                sub = 0xFFE6D8C3.toInt(),
                isMaterial = true,
            ),
        )
    }

    @Test
    fun formatDaysUsesLocalizedUnit() {
        assertEquals("42 d", WidgetPreviews.formatDays(42, "d"))
        assertEquals("6 д", WidgetPreviews.formatDays(6, "д"))
    }

    @Test
    fun formatDaysHoursUsesLocalizedUnits() {
        assertEquals("1d 4h", WidgetPreviews.formatDaysHours(1, 4, "d", "h"))
        assertEquals("3д 9ч", WidgetPreviews.formatDaysHours(3, 9, "д", "ч"))
    }
}
