package com.snkb.chieftimer.notify

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifierFormatTest {

    @Test
    fun formatClockProducesCompactStringsForShortCriticalText() {
        assertEquals("00:00", Notifier.formatClock(-5L))
        assertEquals("00:00", Notifier.formatClock(0L))
        assertEquals("02:19", Notifier.formatClock(139L))
        assertEquals("59:59", Notifier.formatClock(3599L))
        assertEquals("1:04:12", Notifier.formatClock(3852L))
    }

    @Test
    fun buildFixedWidthPaddingMatchesDeficitClosely() {
        val fillers = listOf(
            '\u00A0' to 32f,
            '\u202F' to 20f,
            '\u2006' to 18f,
            '\u200A' to 8f,
        )
        assertEquals("", Notifier.buildFixedWidthPadding(0f, fillers))
        assertEquals("", Notifier.buildFixedWidthPadding(-4f, fillers))
        assertEquals("", Notifier.buildFixedWidthPadding(20f, emptyList()))
        assertEquals("\u00A0\u2006", Notifier.buildFixedWidthPadding(50f, fillers))
        assertEquals("\u00A0\u200A", Notifier.buildFixedWidthPadding(38f, fillers))

        val widthByChar = fillers.toMap()
        val smallest = fillers.minOf { it.second }
        for (deficit in 0..100) {
            val pad = Notifier.buildFixedWidthPadding(deficit.toFloat(), fillers)
            val actual = pad.sumOf { (widthByChar[it] ?: 0f).toDouble() }.toFloat()
            assertTrue(
                "Deficit $deficit gave padding width $actual (error > ${smallest / 2f})",
                abs(actual - deficit) <= smallest / 2f + 0.001f,
            )
        }

        val capped = Notifier.buildFixedWidthPadding(500f, fillers, maxChars = 4)
        assertEquals(4, capped.length)
    }
}
