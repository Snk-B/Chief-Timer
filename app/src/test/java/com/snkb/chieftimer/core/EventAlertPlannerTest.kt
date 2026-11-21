package com.snkb.chieftimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventAlertPlannerTest {
    private val h = 3600L

    @Test
    fun generatesStartsEndingAndComingAlertsForEnabledEventsOnly() {
        val events = listOf(
            GameEvent(id = GameEvents.CLAN_GAMES, point = false, active = false, startsAt = 100 * h, endsAt = 200 * h),
            GameEvent(id = GameEvents.SEASON_END, point = true, active = false, startsAt = 0, endsAt = 150 * h),
            GameEvent(id = GameEvents.RAID_WEEKEND, point = false, active = false, startsAt = 50 * h, endsAt = 60 * h),
        )
        val alerts = EventAlertPlanner.alerts(events, setOf(GameEvents.CLAN_GAMES, GameEvents.SEASON_END), 6 * h)
        assertEquals(3, alerts.size)
        assertEquals(EventAlertPlanner.Kind.STARTS, alerts[0].kind)
        assertEquals(100 * h, alerts[0].at)
        assertEquals(EventAlertPlanner.Kind.ENDING, alerts[1].kind)
        assertEquals(194 * h, alerts[1].at)
        assertEquals(EventAlertPlanner.Kind.COMING, alerts[2].kind)
        assertEquals(144 * h, alerts[2].at)
    }

    @Test
    fun dueDropsStaleAndFiredAlertsAndNextWakePicksEarliestFutureWake() {
        val ev = GameEvent(id = GameEvents.CWL_SIGNUP, point = false, active = false, startsAt = 10 * h, endsAt = 20 * h)
        val a1 = EventAlertPlanner.Alert(10 * h, "k1", ev.id, null, EventAlertPlanner.Kind.STARTS)
        val a2 = EventAlertPlanner.Alert(14 * h, "k2", ev.id, null, EventAlertPlanner.Kind.ENDING)
        val aOld = EventAlertPlanner.Alert(5 * h, "kOld", ev.id, null, EventAlertPlanner.Kind.STARTS)

        val due = EventAlertPlanner.due(listOf(aOld, a1, a2), setOf("k1"), now = 14 * h)
        assertEquals(listOf(a2), due)

        assertEquals(14 * h, EventAlertPlanner.nextWake(listOf(a1, a2), listOf(ev), setOf(ev.id), setOf("k1"), now = 11 * h))
        assertEquals(20 * h + 60, EventAlertPlanner.nextWake(listOf(a1, a2), listOf(ev), setOf(ev.id), setOf("k1", "k2"), now = 15 * h))
        assertNull(EventAlertPlanner.nextWake(emptyList(), emptyList(), emptySet(), emptySet(), now = 25 * h))
    }
}
