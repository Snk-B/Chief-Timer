package com.killersparrow1.clashtrackers.core

import com.killersparrow1.clashtrackers.core.SampleExports.export1
import com.killersparrow1.clashtrackers.core.SampleExports.export2
import com.killersparrow1.clashtrackers.core.SampleExports.export3
import com.killersparrow1.clashtrackers.core.SampleExports.times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeTimesTest {

    @Test fun assetLoads() {
        assertTrue("got ${times.size}", times.size > 140)
        assertEquals(1166400L, times.timeTo(1000027, 12)) // Inferno Tower → 12: 13d 12h
        assertEquals(1123200L, times.timeTo(107000008, 5)) // Logger → 5: 13d (guardian)
        assertEquals(288000L, times.timeTo(102000040, 8)) // Cake-A-Pult Damage module → 8
        assertEquals(1296000L, times.timeTo(4000008, 13)) // Dragon → 13: 15d
        // Supercharge levels restart at 1 in the data; only the normal track is kept.
        assertEquals(43200L, times.timeTo(1000027, 1))
        assertNull(times.timeTo(1000027, 99))

        // CV19 new buildings and upgrades
        assertEquals(1123200L, times.timeTo(1000001, 19)) // Town Hall 19
        assertEquals(864000L, times.timeTo(1000102, 1))   // Super Wizard Tower lvl 1
        assertEquals(950400L, times.timeTo(1000102, 2))   // Super Wizard Tower lvl 2
        assertEquals(0L, times.timeTo(1000105, 1))        // Guardian Shrine
        assertEquals(172800L, times.timeTo(1000106, 1))   // Squad Barracks lvl 1
        assertEquals(1382400L, times.timeTo(1000106, 12)) // Squad Barracks lvl 12
        assertEquals(1296000L, times.timeTo(1000107, 1))  // Spiky Tower lvl 1
        assertEquals(1339200L, times.timeTo(1000107, 2))  // Spiky Tower lvl 2
        assertEquals(1252800L, times.timeTo(1000108, 1))  // Firecracker Tower lvl 1
        assertEquals(1296000L, times.timeTo(1000108, 2))  // Firecracker Tower lvl 2
    }

    /** Every running upgrade in the real exports has a full time, and it is never shorter than what was left. */
    @Test fun everySampleJobHasAPlausibleFullTime() {
        for (e in listOf(export1, export2, export3)) for (job in e.jobs) {
            val full = times.fullTime(job)
            assertTrue("no time for ${job.key}", full != null)
            assertTrue("${job.key}: full $full < remaining ${job.remainingAtT0}", full!! >= job.remainingAtT0)
        }
    }

    @Test fun parsesFlatMapToo() {
        val t = UpgradeTimes.fromJson("""{"5":{"1":10,"2":20,"x":3},"y":{}}""")
        assertEquals(1, t.size)
        assertEquals(20L, t.timeTo(5, 2))
        assertNull(t.fullTime(Job(JobCategory.BUILDER, JobSource.BUILDING, 5, 2, 9, false))) // level 3 unknown
    }
}
