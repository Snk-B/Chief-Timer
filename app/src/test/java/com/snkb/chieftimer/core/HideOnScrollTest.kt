package com.snkb.chieftimer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideOnScrollTest {
    private fun bar() = HideOnScroll(hideAfter = 12f, showAfter = 6f, nearTop = 24f)

    @Test fun startsVisibleAndStaysVisibleNearTheTop() {
        val b = bar()
        assertTrue(b.visible)
        assertTrue(b.onScroll(100f, distanceFromTop = 10f))
    }

    @Test fun hidesOnlyAfterTheThresholdOfDownwardScroll() {
        val b = bar()
        assertTrue(b.onScroll(5f, 500f))
        assertTrue(b.onScroll(5f, 505f))
        assertFalse(b.onScroll(5f, 510f))
    }

    @Test fun anyRealUpwardScrollRevealsIt() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        assertTrue(b.onScroll(-8f, 492f))
    }

    @Test fun smallJitterDoesNotFlicker() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        repeat(5) {
            assertFalse(b.onScroll(-3f, 497f))
            assertFalse(b.onScroll(3f, 500f))
        }
    }

    @Test fun reversingDirectionRestartsTheCount() {
        val b = bar()
        b.onScroll(10f, 500f)
        b.onScroll(-1f, 499f)
        assertTrue(b.onScroll(10f, 509f))
    }

    @Test fun scrollingBackToTheTopShowsItAndTapsForceItVisible() {
        val b = bar()
        b.onScroll(40f, 500f)
        assertFalse(b.visible)
        assertTrue(b.onScroll(-1f, 10f))
        b.onScroll(40f, 500f)
        b.show()
        assertTrue(b.visible)
        assertTrue(b.onScroll(5f, 500f))
    }
}
