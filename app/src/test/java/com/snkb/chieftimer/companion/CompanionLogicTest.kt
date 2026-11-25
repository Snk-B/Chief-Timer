package com.snkb.chieftimer.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLogicTest {
    private val own = "com.snkb.chieftimer"
    private val keyboards = setOf("com.google.android.inputmethod.latin")

    @Test
    fun entersAndStaysActiveForNeutralPackagesAndKeyboards() {
        assertTrue(CompanionLogic.next(CompanionLogic.GAME_PACKAGE, false, own, keyboards))
        assertTrue(CompanionLogic.next(own, true, own, keyboards))
        assertTrue(CompanionLogic.next(own, true, own, keyboards, "$own.sync.SyncActivity"))
        assertFalse(CompanionLogic.next(own, true, own, keyboards, "$own.MainActivity"))
        assertTrue(CompanionLogic.next("com.android.systemui", true, own, keyboards))
        assertTrue(CompanionLogic.next("com.google.android.inputmethod.latin", true, own, keyboards))
        assertFalse(CompanionLogic.next(own, false, own, keyboards))
    }

    @Test
    fun frameworkOverlaysAndNullPackageKeepCurrentState() {
        assertTrue(CompanionLogic.next("com.samsung.game.booster", true, own, keyboards, "android.widget.FrameLayout"))
        assertTrue(CompanionLogic.next(null, true, own, keyboards))
        assertFalse(CompanionLogic.next(null, false, own, keyboards))
    }

    @Test
    fun otherRealActivitiesLeaveTheGame() {
        assertFalse(CompanionLogic.next("com.android.launcher", true, own, keyboards, "com.android.launcher.MainActivity"))
        assertFalse(CompanionLogic.next("com.whatsapp", true, own, keyboards, null))
    }

    @Test
    fun adaptivePollDelaySavesBatteryWhenIdle() {
        assertEquals(1_500L, CompanionLogic.nextPollDelayMs(active = true, sinceLastGameActivityMs = 120_000L))
        assertEquals(2_000L, CompanionLogic.nextPollDelayMs(active = false, sinceLastGameActivityMs = 15_000L))
        assertEquals(5_000L, CompanionLogic.nextPollDelayMs(active = false, sinceLastGameActivityMs = 120_000L))
        assertEquals(10_000L, CompanionLogic.nextPollDelayMs(active = false, sinceLastGameActivityMs = 600_000L))
    }
}
