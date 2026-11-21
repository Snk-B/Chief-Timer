package com.snkb.chieftimer.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSessionTest {
    @Test
    fun waitsStartDelayThenShowsAndStaysShown() {
        val s = CompanionSession(delayMs = 6000L, freshGapMs = 60_000L)
        assertEquals(6000L, s.waitMs(0L))
        s.onGameEntered(1_000L)
        assertTrue(s.pending())
        assertEquals(6000L, s.waitMs(1_000L))
        assertFalse(s.canShow(5_000L))
        assertEquals(2000L, s.waitMs(5_000L))
        assertTrue(s.canShow(7_000L))
    }

    @Test
    fun shortLeaveKeepsSessionAndDismissalWhileLongGapResets() {
        val s = CompanionSession(delayMs = 5000L, freshGapMs = 60_000L)
        s.onGameEntered(0L)
        assertTrue(s.canShow(5_000L))
        s.dismiss()
        assertFalse(s.canShow(5_000L))
        assertFalse(s.pending())

        // Short leave (20 s < 60 s): stays dismissed, does not restart delay.
        s.onGameLeft(10_000L)
        assertFalse(s.active)
        s.onGameEntered(30_000L)
        assertFalse(s.canShow(30_000L))

        // Long leave (60 s): starts a fresh session.
        s.onGameLeft(40_000L)
        s.onGameEntered(100_000L)
        assertEquals(5000L, s.waitMs(100_000L))
        assertFalse(s.dismissed)
        assertTrue(s.canShow(105_000L))
    }

    @Test
    fun forceShowAppearsImmediatelyEvenAfterDismissal() {
        val s = CompanionSession(delayMs = 6_250L, freshGapMs = 60_000L)
        s.onGameEntered(1_000L)
        s.dismiss()
        assertFalse(s.canShow(2_000L))

        s.forceShow(2_000L)
        assertTrue(s.active)
        assertFalse(s.dismissed)
        assertEquals(0L, s.waitMs(2_000L))
        assertTrue(s.canShow(2_000L))
    }
}
