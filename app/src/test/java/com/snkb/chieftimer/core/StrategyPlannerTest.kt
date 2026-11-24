package com.snkb.chieftimer.core

import com.snkb.chieftimer.data.BackupAccount
import com.snkb.chieftimer.data.BackupBundle
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyPlannerTest {

    @Test fun builderScheduleDetectsCollisionsAndSuggestsLargestGap() {
        val now = 1_000_000L
        // Two builders finish 30 min apart (collision <= 2h), one 3 days later -> largest gap ~3 days
        val finishes = listOf(now + 10_000L, now + 11_800L, now + 3 * 86_400L)
        val analysis = StrategyPlanner.analyzeBuilderSchedule(finishes, totalBuilders = 5, now = now)
        assertEquals(2, analysis.freeNow)
        assertEquals(1, analysis.collisions.size)
        assertEquals(1_800L, analysis.collisions.single().gapSec)
        assertNotNull(analysis.idealMinSec)
        assertNotNull(analysis.idealMaxSec)
        assertTrue(analysis.idealMinSec!! >= 3600L)
    }

    @Test fun potionSimulatorSubtractsNineHoursPerPotionOnLongUpgrades() {
        val export = VillageExport(
            tag = "#T",
            timestamp = 1_000L,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000008, 10, 20 * 3600L, false),
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000009, 5, 5 * 3600L, false),
            ),
        )
        val sim = StrategyPlanner.simulatePotions(
            export = export,
            engine = TimerEngine(),
            state = AppState(),
            now = 1_000L,
            slotType = PotionSlotType.BUILDER,
            potionCount = 1,
            names = NameMap(mapOf(1000008 to "Cannon", 1000009 to "Archer Tower")),
        )
        assertEquals(2, sim.size)
        val at = sim.first { it.dataId == 1000009 }
        val cannon = sim.first { it.dataId == 1000008 }
        assertEquals(1800L, at.afterRemainingSec) // 5h at 10x -> 30m
        assertEquals(11 * 3600L, cannon.afterRemainingSec) // 20h - 9h saved = 11h
    }

    @Test fun bookRankingOrdersByLongestRemaining() {
        val export = VillageExport(
            tag = "#T",
            timestamp = 1_000L,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.HERO, 28000000, 90, 5 * 86_400L, false),
                Job(JobCategory.LAB, JobSource.UNIT, 4000000, 11, 10 * 86_400L, false),
                Job(JobCategory.LAB, JobSource.SPELL, 26000000, 9, 2 * 86_400L, false),
            ),
        )
        val ranked = StrategyPlanner.rankBookCandidates(
            export, TimerEngine(), AppState(), 1_000L,
            NameMap(mapOf(28000000 to "King", 4000000 to "Barbarian", 26000000 to "Lightning")),
        )
        assertEquals(listOf(BookKind.FIGHTING, BookKind.HEROES, BookKind.SPELLS), ranked.map { it.bookKind })
    }

    @Test fun cwlConflictFlagsHeroRunningPastWarStart() {
        val now = 1_000L
        val export = VillageExport(
            tag = "#T",
            timestamp = now,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.HERO, 28000001, 90, 5 * 86_400L, false),
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000008, 20, 5 * 86_400L, false), // Cannon isn't offense
            ),
        )
        val events = listOf(
            GameEvent(GameEvents.CWL_WARS, point = false, active = false, startsAt = now + 2 * 86_400L, endsAt = now + 9 * 86_400L),
        )
        val conflicts = StrategyPlanner.findCwlConflicts(
            export, TimerEngine(), AppState(), now, events, NameMap(mapOf(28000001 to "Queen", 1000008 to "Cannon")),
        )
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.single().isHero)
        assertEquals(3 * 86_400L, conflicts.single().overlapSec)
    }

    @Test fun oreEstimateAccountsForDailyBonusAndWars() {
        val est = StrategyPlanner.estimateOreDays(1000, 54, 9, OreLeague.TITAN, warsPerWeek = 2)
        assertTrue(est.totalDays >= est.daysForStarry)
        assertTrue(est.totalDays in 2..20)
    }

    @Test fun helperNoticeFiresWhenCooldownEndsAndFlagIsOn() {
        val export = VillageExport(
            tag = "#T",
            timestamp = 1_000L,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = listOf(Helper(GameIds.HELPER_BUILDERS_APPRENTICE, level = 8, cooldown = 3600L)),
            jobs = emptyList(),
        )
        val state = AppState(exportJson = """{"tag":"#T","timestamp":1000,"buildings":[{"data":1000001,"lvl":16,"cnt":1}],"helpers":[{"data":93000000,"lvl":8,"helper_cooldown":3600}]}""")
        val res = FinishNotices.check(state, AppSettings(), NameMap(emptyMap()), now = 4610L, helperAlerts = true)
        assertEquals(1, res.notices.size)
        assertEquals("helper:4600", res.notices.single().jobKey)
        assertTrue("helper:4600" in res.notified)
        val disabled = FinishNotices.check(state, AppSettings(), NameMap(emptyMap()), now = 4610L, helperAlerts = false)
        assertEquals(0, disabled.notices.size)
    }

    @Test fun villageHistoryTracksProgressDelta() {
        val h = VillageHistory()
            .add(VillageSnapshot(1_000_000L, 16, 40, 20, 2400))
            .add(VillageSnapshot(1_000_000L + 10 * 86_400L, 16, 45, 30, 2160))
        val d = h.delta(30)!!
        assertEquals(5, d.overallPctDelta)
        assertEquals(10L, d.builderDaysDelta)
        assertEquals(10, d.spanDays)
    }

    @Test fun roadToMaxGoldPassScalesBuildersAndLabButNotPetsOrOres() {
        val base = RoadToMax(
            townHall = 16,
            buildings = RoadTotals(seconds = 1000L, gold = 10_000L),
            heroes = RoadTotals(seconds = 2000L, darkElixir = 1000L),
            lab = RoadTotals(seconds = 1000L, elixir = 10_000L),
            pets = RoadTotals(seconds = 1000L, darkElixir = 1000L),
            walls = RoadTotals(goldOrElixir = 10_000L),
            levels = RoadLevels(),
            longestHeroSeconds = 2000L,
            equipment = RoadTotals(shinyOre = 500L, glowyOre = 100L, starryOre = 10L),
        )
        val scaled = base.withGoldPass(GoldPassReduction(builderPercent = 20, researchPercent = 15))
        assertEquals(800L, scaled.buildings.seconds)
        assertEquals(8_000L, scaled.buildings.gold)
        assertEquals(1600L, scaled.heroes.seconds)
        assertEquals(850L, scaled.lab.seconds)
        assertEquals(8_500L, scaled.lab.elixir)
        assertEquals(8_000L, scaled.walls.goldOrElixir)
        assertEquals(1000L, scaled.pets.seconds)
        assertEquals(500L, scaled.equipment.shinyOre)
    }

    @Test fun bobProgressTracksBuilderBaseRequirementsAndUnlock() {
        val data = MaxData(
            maxTownHall = 16,
            items = mapOf(
                1000034 to MaxItem('B', counts = listOf(1), steps = List(10) { MaxStep(0, 0, 0, it + 1) }, category = 'p'),
                4000031 to MaxItem('S', counts = null, steps = List(18) { MaxStep(0, 0, 0, 1) }),
                1000048 to MaxItem('B', counts = null, steps = List(9) { MaxStep(0, 0, 0, 1) }, category = 'd'),
                28000003 to MaxItem('M', counts = null, steps = List(35) { MaxStep(0, 0, 0, 1) }),
                28000005 to MaxItem('M', counts = null, steps = List(35) { MaxStep(0, 0, 0, 1) }),
            ),
        )
        val export = VillageExport(
            tag = "#BB",
            timestamp = 1_000L,
            townHallLevel = 14,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = emptyList(),
            ownedLevels = mapOf(
                1000034 to listOf(9),
                4000031 to listOf(18),
                1000048 to listOf(7),
                28000003 to listOf(22),
                28000005 to listOf(18),
            ),
            hasBobsHut = false,
        )
        val progress = StrategyPlanner.bobProgress(export, data, NameMap(mapOf(4000031 to "Baby Dragon", 1000048 to "Mega Tesla")))!!
        assertFalse(progress.unlocked)
        assertTrue(progress.troop.done)
        assertEquals(7, progress.defense.current)
        assertEquals(40, progress.heroes.current)
        assertFalse(progress.heroes.done)

        val unlocked = StrategyPlanner.bobProgress(export.copy(hasBobsHut = true), data, NameMap(emptyMap()))!!
        assertTrue(unlocked.unlocked)
    }

    @Test fun offenseChecklistSelectsCoreOffenseAndHeroes() {
        val road = RoadToMax(
            townHall = 16,
            buildings = RoadTotals(),
            heroes = RoadTotals(),
            lab = RoadTotals(),
            pets = RoadTotals(),
            walls = RoadTotals(),
            levels = RoadLevels(),
            longestHeroSeconds = 0L,
            items = listOf(
                RoadItem(1000000, 'b', listOf(12, 11), maxLevel = 12, name = "Army Camp"),
                RoadItem(1000008, 'd', listOf(20), maxLevel = 21, name = "Cannon"),
                RoadItem(28000000, 'h', listOf(80), maxLevel = 95, name = "Barbarian King"),
            ),
        )
        val checklist = StrategyPlanner.offenseChecklist(road)
        assertEquals(listOf(28000000, 1000000), checklist.items.map { it.id })
        assertEquals(103L, checklist.share.have)
        assertEquals(119L, checklist.share.max)
    }

    @Test fun helperRoiAnalysisComputesDailySavedAndBestJob() {
        val now = 1_000L
        val export = VillageExport(
            tag = "#H",
            timestamp = now,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = listOf(Helper(GameIds.HELPER_BUILDERS_APPRENTICE, level = 8, cooldown = 0L)),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000008, 10, 6 * 86_400L, false),
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000009, 5, 10 * 3600L, false),
            ),
        )
        val roi = StrategyPlanner.helperRoiAnalysis(
            export, TimerEngine(), AppState(), now, NameMap(mapOf(1000008 to "Cannon", 1000009 to "Archer Tower")),
        )
        assertEquals(1, roi.size)
        val info = roi.single()
        assertEquals(8, info.hoursSavedPerDay)
        assertEquals(56, info.hoursSavedPerWeek)
        assertEquals("Cannon ➛ 11", info.bestJobName)
        assertTrue(info.bestJobSavedHours > 24)
    }

    @Test fun bedtimeReminderFiresOnlyNearSleepWhenSlotAvailable() {
        val sleep = SleepWindow(startMin = 23 * 60, endMin = 7 * 60) // 23:00 UTC
        val nearBedtime = java.time.ZonedDateTime.of(2026, 6, 1, 22, 30, 0, 0, ZoneOffset.UTC).toEpochSecond()
        val morning = java.time.ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC).toEpochSecond()
        val exportJson = """{"tag":"#B","timestamp":$nearBedtime,"buildings":[{"data":1000001,"lvl":16,"cnt":1},{"data":1000015,"lvl":1,"cnt":5}]}"""
        val state = AppState(exportJson = exportJson)
        val freeNearSleep = FinishNotices.checkBedtimeReminder(
            state, AppSettings(), nearBedtime, sleep, ZoneOffset.UTC,
        )
        assertNotNull(freeNearSleep)
        val tooEarly = FinishNotices.checkBedtimeReminder(
            state, AppSettings(), morning, sleep, ZoneOffset.UTC,
        )
        assertEquals(null, tooEarly)
    }

    @Test fun backupBundleRoundTripsAccountsAndGlobalSettings() {
        val bundle = BackupBundle(
            version = 1,
            createdAt = 123456L,
            selectedTag = "#A1",
            globalSettings = GlobalSettings(appTheme = "amoled_dark"),
            accounts = listOf(
                BackupAccount(
                    meta = AccountMeta(tag = "#A1", name = "Chief", townHall = 16),
                    state = AppState(exportJson = "{}"),
                    settings = AccountSettings(playerName = "Chief"),
                    history = VillageHistory(),
                ),
            ),
        )
        val decoded = BackupBundle.decode(bundle.encode())
        assertNotNull(decoded)
        assertEquals("#A1", decoded!!.selectedTag)
        assertEquals(1, decoded.accounts.size)
        assertEquals("amoled_dark", decoded.globalSettings.appTheme)
    }

    @Test fun potionSimulatorFlagsFinishDuringSleepWindow() {
        val noonUtc = java.time.ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC).toEpochSecond()
        // Sleep window 23:00..07:00 UTC -> 1 potion turns a 20h job into 11h left -> finishes at 23:00 UTC (asleep)!
        val sleep = SleepWindow(startMin = 23 * 60, endMin = 7 * 60)
        val export = VillageExport(
            tag = "#S",
            timestamp = noonUtc,
            townHallLevel = 16,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000008, 10, 20 * 3600L, false),
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000009, 5, 11 * 3600L, false), // 11h - 9h = 2h -> 14:00 UTC (awake)
            ),
        )
        val sim = StrategyPlanner.simulatePotions(
            export = export,
            engine = TimerEngine(),
            state = AppState(),
            now = noonUtc,
            slotType = PotionSlotType.BUILDER,
            potionCount = 1,
            names = NameMap(mapOf(1000008 to "Cannon", 1000009 to "Archer Tower")),
            sleepWindow = sleep,
            zone = ZoneOffset.UTC,
        )
        assertTrue(sim.first { it.dataId == 1000008 }.finishesDuringSleep)
        assertFalse(sim.first { it.dataId == 1000009 }.finishesDuringSleep)
    }

    @Test fun roadForecastDetectsLabBottleneckAndWallLootCount() {
        val wallSteps = listOf(
            MaxStep(0, 1_000_000L, 0, 10),
            MaxStep(0, 2_000_000L, 0, 11),
            MaxStep(0, 5_000_000L, 0, 12),
        )
        val data = MaxData(16, mapOf(1000010 to MaxItem('w', listOf(10), wallSteps, 'p')))
        val road = RoadToMax(
            townHall = 12,
            buildings = RoadTotals(seconds = 50 * 86_400L),
            heroes = RoadTotals(),
            lab = RoadTotals(seconds = 40 * 86_400L),
            pets = RoadTotals(),
            walls = RoadTotals(goldOrElixir = 10_000_000L),
            levels = RoadLevels(),
            longestHeroSeconds = 0L,
            items = listOf(RoadItem(1000010, 'w', listOf(0, 0, 1, 1), maxLevel = 3)),
        )
        // With 5 builders: 50d/5 = 10d builders vs 40d lab -> LAB bottleneck (+30d).
        val forecast = StrategyPlanner.roadForecast(road, export = null, builders = 5, now = 1_000L)
        assertEquals(RoadBottleneck.LAB, forecast.bottleneck)
        assertEquals(30L, forecast.bottleneckGapDays)

        // Loot simulation with 20% GP: lv0->1 costs 0.8M (2 walls = 1.6M), lv1->2 costs 1.6M -> 5M budget affords 2 + 2 = 4 walls (total 4.8M)!
        val wallSim = StrategyPlanner.simulateWallsForLoot(road, data, budgetResources = 5_000_000L, builderPercent = 20)
        assertEquals(4, wallSim.upgradedWalls)
        assertEquals(4_800_000L, wallSim.spentCost)
    }

    @Test fun syncDiffCountsStartedFinishedAndWallUpgrades() {
        val prev = VillageExport(
            tag = "#D",
            timestamp = 1_000L,
            townHallLevel = 15,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000008, 10, 3600L, false),
            ),
            ownedLevels = mapOf(1000010 to listOf(13, 13), 1000008 to listOf(10)),
        )
        val curr = VillageExport(
            tag = "#D",
            timestamp = 2_000L,
            townHallLevel = 15,
            builderHutCount = 5,
            helpers = emptyList(),
            jobs = listOf(
                Job(JobCategory.BUILDER, JobSource.BUILDING, 1000009, 12, 7200L, false),
            ),
            ownedLevels = mapOf(1000010 to listOf(14, 14), 1000008 to listOf(11)),
        )
        val diff = SyncLogic.computeDiff(prev, curr)!!
        assertEquals(1, diff.started)
        assertEquals(1, diff.finished)
        assertEquals(1, diff.levelsGained)
        assertEquals(2, diff.wallsUpgraded)
    }
}
