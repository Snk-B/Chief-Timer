package com.snkb.chieftimer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun redactPersonalDataRemovesTagsAndPlayerNames() {
        val sample = "2026-11-21 08:30  [widget] ·  Tap on #Q8L9V2Y0: ADD/potion\nNotified (#q8l9v2y0): ChiefSnk builder free"
        val clean = DiagnosticReport.redactPersonalData(sample, playerName = "ChiefSnk", playerTag = "#Q8L9V2Y0")
        assertFalse(clean.contains("Q8L9V2Y0", ignoreCase = true))
        assertFalse(clean.contains("ChiefSnk", ignoreCase = true))
        assertEquals(
            "2026-11-21 08:30  [widget] ·  Tap on #REDACTED: ADD/potion\nNotified (#REDACTED): <player> builder free",
            clean,
        )
    }

    @Test
    fun redactPersonalDataStripsAnyTagEvenWithoutExplicitTagParam() {
        val line = "2026-11-21 08:30  [app] OK  Synced (#2PP8R9QL) OK"
        assertEquals("2026-11-21 08:30  [app] OK  Synced (#REDACTED) OK", DiagnosticReport.redactPersonalData(line))
    }
}
