package com.killersparrow1.clashtrackers.core

import com.killersparrow1.clashtrackers.core.SampleExports.names
import com.killersparrow1.clashtrackers.core.SampleExports.times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Cv19UpdateTest {

    private fun findScratchFile(name: String): File? {
        val f = File("C:/Users/Davidson Braga/.gemini/antigravity/scratch", name)
        return if (f.isFile) f else null
    }

    @Test
    fun cv19NamesExistInEnglishAndRussian() {
        // EN
        assertEquals("Guardian Shrine", names.name(1000105))
        assertEquals("Squad Barracks", names.name(1000106))
        assertEquals("Spiky Tower", names.name(1000107))
        assertEquals("Firecracker Tower", names.name(1000108))
        assertEquals("Alchemist", names.name(93000002))
        assertEquals("Prospector", names.name(93000003))
        assertEquals("Shield Brooch", names.name(90000062))
        assertEquals("Blizzard Axe", names.name(90000063))
        assertEquals("Super Wizard Tower", names.name(1000102))

        // Russian
        val ruJson = SampleExports.file("src/main/assets/names_ru.json", "app/src/main/assets/names_ru.json").readText()
        val ruMap = names.mergedWith(NameMap.fromJson(ruJson))
        assertEquals("Святилище стражей", ruMap.name(1000105))
        assertEquals("Казарма отрядов", ruMap.name(1000106))
        assertEquals("Шипастая башня", ruMap.name(1000107))
        assertEquals("Башня огненной лучницы", ruMap.name(1000108))
        assertEquals("Алхимик", ruMap.name(93000002))
        assertEquals("Старатель", ruMap.name(93000003))
        assertEquals("Брошь-щит", ruMap.name(90000062))
        assertEquals("Топор снежной бури", ruMap.name(90000063))
        assertEquals("Башня суперколдуна", ruMap.name(1000102))
    }

    @Test
    fun cv19UpgradeTimesAreAccurate() {
        assertEquals(1123200L, times.timeTo(1000001, 19)) // TH19 = 13d
        assertEquals(864000L, times.timeTo(1000102, 1))   // Super Wizard Tower lvl 1 = 10d
        assertEquals(950400L, times.timeTo(1000102, 2))   // Super Wizard Tower lvl 2 = 11d
        assertEquals(0L, times.timeTo(1000105, 1))        // Guardian Shrine lvl 1 = 0s
        assertEquals(172800L, times.timeTo(1000106, 1))   // Squad Barracks lvl 1 = 2d
        assertEquals(1382400L, times.timeTo(1000106, 12)) // Squad Barracks lvl 12 = 16d
        assertEquals(1296000L, times.timeTo(1000107, 1))  // Spiky Tower lvl 1 = 15d
        assertEquals(1339200L, times.timeTo(1000107, 2))  // Spiky Tower lvl 2 = 15.5d
        assertEquals(1252800L, times.timeTo(1000108, 1))  // Firecracker Tower lvl 1 = 14.5d
        assertEquals(1296000L, times.timeTo(1000108, 2))  // Firecracker Tower lvl 2 = 15d
    }

    @Test
    fun cv19IconsExist() {
        val iconsDir = SampleExports.file("src/main/assets/icons", "app/src/main/assets/icons")
        val expected = listOf(
            "1000105.webp",
            "1000106.webp",
            "1000107.webp",
            "1000108.webp",
            "93000002.webp",
            "alchemist.webp",
            "93000003.webp",
            "prospector.webp",
            "90000062.webp",
            "90000063.webp",
            "1000102.webp",
        )
        for (name in expected) {
            val file = File(iconsDir, name)
            assertTrue("Icon $name must exist", file.isFile && file.length() > 0)
        }
    }

    @Test
    fun userJsonFromTodayDatamineParsesCleanly() {
        val userFile = findScratchFile("user_json.json") ?: return
        val text = userFile.readText()
        val result = ExportParser.parse(text)
        assertTrue("user_json.json must parse successfully", result is ParseResult.Success)
        val export = (result as ParseResult.Success).export

        assertEquals("#2RYC2UCUJ", export.tag)
        assertEquals(19, export.townHallLevel)
        assertEquals(4, export.helpers.size)

        // Helper 93000003 (Prospector) and 93000002 (Alchemist)
        assertNotNull(export.helper(GameIds.HELPER_PROSPECTOR))
        assertNotNull(export.helper(GameIds.HELPER_ALCHEMIST))

        // Running Spiky Tower and Squad Barracks
        val spiky = export.builderJobs.firstOrNull { it.dataId == 1000107 }
        assertNotNull("Spiky Tower upgrade job must exist", spiky)
        assertTrue("Spiky Tower has apprentice recurrent link", spiky!!.helperLinked)
        assertEquals(1004476L, spiky.remainingAtT0)

        val barracks = export.builderJobs.firstOrNull { it.dataId == 1000106 }
        assertNotNull("Squad Barracks upgrade job must exist", barracks)
        assertEquals(111451L, barracks!!.remainingAtT0)

        // Sync logic handles recurrent apprentice link seamlessly
        val outcome = SyncLogic.apply(AppState(), export, text)
        assertTrue("Sync must succeed", outcome is SyncLogic.Outcome.Synced)
        val state = outcome.state
        assertEquals(spiky.key, state.links.apprenticeJobKey)

        // Timer simulation for Spiky Tower with recurrent apprentice sessions
        val engine = TimerEngine()
        val finish = engine.finishTime(spiky, export, state.links)
        assertTrue("Finish time must be in future", finish > export.timestamp)
        assertTrue("Finish time must be less than unboosted timer", finish < export.timestamp + spiky.remainingAtT0)
    }

    @Test
    fun edgeCasesNegativeTimerAndClockDrift() {
        val raw = """{"tag":"#TEST","timestamp":1000000,"buildings":[{"data":1000106,"lvl":0,"timer":-500}]}"""
        val result = ExportParser.parse(raw)
        assertTrue(result is ParseResult.Success)
        val export = (result as ParseResult.Success).export
        val job = export.builderJobs.single()
        assertEquals(0L, job.remainingAtT0)

        // Clock drift (phone clock slightly behind export timestamp)
        val engine = TimerEngine()
        val remainingBefore = engine.remainingAt(job, export, HelperLinks(), emptyList(), 999900L)
        assertEquals(0L, remainingBefore)
    }
}
