package com.snkb.chieftimer.core

import kotlin.math.ceil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class RoadItemSort { WORK_LEFT, LOWEST_LEVEL, FASTEST }

data class RoadGridFilter(
    val hideMaxed: Boolean = false,
    val sort: RoadItemSort = RoadItemSort.WORK_LEFT,
)

/** A concrete available next upgrade suggestion for "Where to spend now". */
data class SpendRecommendation(
    val id: Int,
    val name: String,
    val nextLevel: Int,
    val cost: Long,
    val resource: Int,
    val seconds: Long,
)

data class BuilderCollision(
    val firstAt: Long,
    val secondAt: Long,
    val gapSec: Long,
)

data class BuilderScheduleAnalysis(
    val busyCount: Int,
    val totalBuilders: Int,
    val freeNow: Int,
    val sortedFinishes: List<Long>,
    val collisions: List<BuilderCollision>,
    val idealMinSec: Long?,
    val idealMaxSec: Long?,
)

enum class PotionSlotType { BUILDER, LAB, PET }

data class SimulatedJob(
    val jobKey: String,
    val dataId: Int,
    val name: String,
    val levelLabel: String,
    val beforeRemainingSec: Long,
    val afterRemainingSec: Long,
    val savedSec: Long,
    val finishesDuringSleep: Boolean = false,
)

enum class RoadBottleneck { BUILDERS, LAB, HEROES, PETS, BALANCED }

data class RoadForecast(
    val builderDaysWithHelpers: Long,
    val builderFinishEpoch: Long,
    val labDaysWithHelper: Long,
    val labFinishEpoch: Long,
    val petDays: Long,
    val bottleneck: RoadBottleneck,
    val bottleneckGapDays: Long,
)

data class WallSimResult(
    val upgradedWalls: Int,
    val spentCost: Long,
    val wallsLeftAfter: Int,
    val unupgradedTotal: Int,
)

enum class BookKind { HEROES, FIGHTING, SPELLS, BUILDING }

data class BookCandidate(
    val jobKey: String,
    val dataId: Int,
    val name: String,
    val targetLevel: Int,
    val remainingSec: Long,
    val bookKind: BookKind,
)

data class CwlConflict(
    val jobKey: String,
    val dataId: Int,
    val name: String,
    val targetLevel: Int,
    val finishAt: Long,
    val overlapSec: Long,
    val isHero: Boolean,
    val cwlActiveNow: Boolean,
)

/** Clash of Clans Star Bonus leagues (daily ore income) for offline equipment forecasting. */
enum class OreLeague(val shinyPerDay: Int, val glowyPerDay: Int, val starryPerDay: Int) {
    GOLD(375, 22, 0),
    CRYSTAL(500, 31, 0),
    MASTER(625, 40, 0),
    CHAMPION(750, 48, 0),
    TITAN(925, 54, 0),
    LEGEND(1000, 54, 0),
}

data class OreEstimate(
    val daysForShiny: Int,
    val daysForGlowy: Int,
    val daysForStarry: Int,
    val totalDays: Int,
)

@Serializable
data class VillageSnapshot(
    val at: Long,
    val townHall: Int,
    val overallPct: Int,
    val wallsPct: Int,
    val builderHoursLeft: Long,
)

data class ProgressDelta(
    val overallPctDelta: Int,
    val wallsPctDelta: Int,
    val builderDaysDelta: Long,
    val spanDays: Int,
)

@Serializable
data class VillageHistory(val entries: List<VillageSnapshot> = emptyList()) {
    /** Records [snapshot] while keeping compact spacing (~6h minimum unless Town Hall or % changed). */
    fun add(snapshot: VillageSnapshot): VillageHistory {
        val last = entries.lastOrNull()
        val deduped = if (last != null &&
            (snapshot.at - last.at) < 6 * 3600L &&
            snapshot.townHall == last.townHall &&
            snapshot.overallPct == last.overallPct &&
            snapshot.wallsPct == last.wallsPct
        ) {
            entries.dropLast(1) + snapshot
        } else {
            (entries + snapshot).takeLast(60)
        }
        return VillageHistory(deduped)
    }

    fun delta(daysBack: Int = 30, now: Long = entries.lastOrNull()?.at ?: 0L): ProgressDelta? {
        if (entries.size < 2) return null
        val latest = entries.last()
        val cutoff = now - daysBack * 86_400L
        val baseline = entries.firstOrNull { it.at >= cutoff } ?: entries.first()
        if (baseline === latest || baseline.at >= latest.at) return null
        val spanDays = (((latest.at - baseline.at) / 86_400L).toInt()).coerceAtLeast(1)
        val hoursSaved = baseline.builderHoursLeft - latest.builderHoursLeft
        return ProgressDelta(
            overallPctDelta = latest.overallPct - baseline.overallPct,
            wallsPctDelta = latest.wallsPct - baseline.wallsPct,
            builderDaysDelta = hoursSaved / 24L,
            spanDays = spanDays,
        )
    }

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(text: String?): VillageHistory =
            text?.let { runCatching { JSON.decodeFromString(serializer(), it) }.getOrNull() } ?: VillageHistory()
    }
}

enum class HelperRole { APPRENTICE, LAB_ASSISTANT, ALCHEMIST }

data class HelperStatus(
    val dataId: Int,
    val role: HelperRole,
    val level: Int,
    val readyAt: Long,
    val remainingCooldownSec: Long,
) {
    val isReady: Boolean get() = remainingCooldownSec <= 0L
}

data class BobRequirementProgress(
    val current: Int,
    val target: Int,
    val itemName: String? = null,
) {
    val done: Boolean get() = current >= target
}

data class BobProgress(
    val unlocked: Boolean,
    val troop: BobRequirementProgress,
    val defense: BobRequirementProgress,
    val heroes: BobRequirementProgress,
)

data class OffenseChecklist(
    val items: List<RoadItem>,
    val share: LevelShare,
)

data class HelperRoiInfo(
    val role: HelperRole,
    val level: Int,
    val hoursSavedPerDay: Int,
    val hoursSavedPerWeek: Int,
    val bestJobName: String?,
    val bestJobSavedHours: Int,
)

object StrategyPlanner {
    private val OFFENSE_BUILDING_IDS = setOf(
        1000006, // Barracks
        1000007, // Laboratory
        1000020, // Spell Factory
        1000026, // Dark Barracks
        1000029, // Dark Spell Factory
        1000059, // Workshop
        1000068, // Pet House
        1000070, // Blacksmith
        1000071, // Hero Hall
    )

    private val OFFENSE_CHECKLIST_IDS = OFFENSE_BUILDING_IDS + setOf(
        1000000, // Army Camp
        1000014, // Clan Castle
    )

    fun filterAndSortRoadItems(
        items: List<RoadItem>,
        data: MaxData,
        filter: RoadGridFilter,
    ): List<RoadItem> {
        val visible = if (filter.hideMaxed) items.filter { !it.maxed } else items
        return when (filter.sort) {
            RoadItemSort.WORK_LEFT -> visible.sortedWith(compareByDescending<RoadItem> { it.levelsLeft }.thenBy { it.id })
            RoadItemSort.LOWEST_LEVEL -> visible.sortedWith(compareBy<RoadItem> { it.maxed }.thenBy { it.lowest }.thenBy { it.id })
            RoadItemSort.FASTEST -> visible.sortedWith(
                compareBy<RoadItem> { it.maxed }
                    .thenBy { item -> data.items[item.id]?.steps?.getOrNull(item.lowest)?.seconds ?: Long.MAX_VALUE }
                    .thenBy { it.id },
            )
        }
    }

    /** Picks up to [perResource] highest-cost available next upgrades for Gold (0), Elixir (1), and Dark Elixir (2). */
    fun topSpendRecommendations(
        road: RoadToMax,
        data: MaxData,
        names: NameMap,
        perResource: Int = 3,
    ): Map<Int, List<SpendRecommendation>> {
        val all = mutableListOf<SpendRecommendation>()
        for (item in road.items) {
            if (item.maxed || item.group == 'w' || item.group == 'e') continue
            val currentMin = item.lowest.coerceAtLeast(0)
            if (currentMin >= item.maxLevel) continue
            val step = data.items[item.id]?.steps?.getOrNull(currentMin) ?: continue
            if (step.resource !in 0..2 || step.cost <= 0L) continue
            val title = item.name?.takeIf { it.isNotBlank() } ?: names.name(item.id)
            all += SpendRecommendation(
                id = item.id,
                name = title,
                nextLevel = currentMin + 1,
                cost = step.cost,
                resource = step.resource,
                seconds = step.seconds,
            )
        }
        return all.groupBy { it.resource }
            .mapValues { (_, list) ->
                list.sortedWith(compareByDescending<SpendRecommendation> { it.cost }.thenBy { it.seconds })
                    .take(perResource)
            }
    }

    fun analyzeBuilderSchedule(
        busyFinishTimes: List<Long>,
        totalBuilders: Int,
        now: Long,
        collisionWindowSec: Long = 2 * 3600L,
    ): BuilderScheduleAnalysis {
        val sorted = busyFinishTimes.filter { it > now }.sorted()
        val collisions = mutableListOf<BuilderCollision>()
        for (i in 0 until sorted.size - 1) {
            val gap = sorted[i + 1] - sorted[i]
            if (gap <= collisionWindowSec) {
                collisions += BuilderCollision(sorted[i], sorted[i + 1], gap)
            }
        }
        var bestMid: Long? = null
        var bestSpan = 0L
        if (sorted.isNotEmpty()) {
            val points = listOf(now) + sorted + listOf(sorted.last() + 4 * 86_400L)
            for (i in 0 until points.size - 1) {
                val span = points[i + 1] - points[i]
                if (span > bestSpan && span >= 8 * 3600L) {
                    bestSpan = span
                    bestMid = points[i] + span / 2L
                }
            }
        }
        val targetOffset = bestMid?.let { (it - now).coerceAtLeast(3600L) }
        val minIdeal = targetOffset?.let { (it - maxOf(2 * 3600L, bestSpan / 6L)).coerceAtLeast(3600L) }
        val maxIdeal = targetOffset?.let { it + maxOf(2 * 3600L, bestSpan / 6L) }
        return BuilderScheduleAnalysis(
            busyCount = sorted.size,
            totalBuilders = totalBuilders,
            freeNow = (totalBuilders - sorted.size).coerceAtLeast(0),
            sortedFinishes = sorted,
            collisions = collisions,
            idealMinSec = minIdeal,
            idealMaxSec = maxIdeal,
        )
    }

    /**
     * Simulates applying [potionCount] 10x/1h potions starting now across active home-village jobs
     * of [slotType]. Each hour at 10x covers 10h of progress (saving up to 9h real time per potion).
     */
    fun simulatePotions(
        export: VillageExport?,
        engine: TimerEngine,
        state: AppState,
        now: Long,
        slotType: PotionSlotType,
        potionCount: Int,
        names: NameMap,
        sleepWindow: SleepWindow? = null,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<SimulatedJob> {
        if (export == null || potionCount <= 0) return emptyList()
        val targetCategory = when (slotType) {
            PotionSlotType.BUILDER -> JobCategory.BUILDER
            PotionSlotType.LAB -> JobCategory.LAB
            PotionSlotType.PET -> JobCategory.PET
        }
        val boostRealSec = potionCount * 3600L
        val boostWorkSec = potionCount * 10L * 3600L
        val hasSleep = sleepWindow != null && sleepWindow.startMin != sleepWindow.endMin
        return export.jobs.filter { it.category == targetCategory }.mapNotNull { job ->
            val finishAt = engine.finishTime(job, export, state.links, state.boosts, state.activeBoosts.modifiers, state.eventSettings.events)
            val before = finishAt - now
            if (before <= Finish.GRACE_SEC) return@mapNotNull null
            val after = if (before <= boostWorkSec) {
                (before / 10L).coerceAtLeast(60L)
            } else {
                before - (boostWorkSec - boostRealSec)
            }
            val sleepFlag = hasSleep && slotType == PotionSlotType.BUILDER && sleepWindow!!.asleep(now + after, zone)
            SimulatedJob(
                jobKey = job.key,
                dataId = job.dataId,
                name = names.title(job),
                levelLabel = if (job.isNewBuilding) "1" else job.targetLevel.toString(),
                beforeRemainingSec = before,
                afterRemainingSec = after,
                savedSec = (before - after).coerceAtLeast(0L),
                finishesDuringSleep = sleepFlag,
            )
        }.sortedBy { it.afterRemainingSec }
    }

    fun rankBookCandidates(
        export: VillageExport?,
        engine: TimerEngine,
        state: AppState,
        now: Long,
        names: NameMap,
    ): List<BookCandidate> {
        if (export == null) return emptyList()
        return export.jobs.mapNotNull { job ->
            val finishAt = engine.finishTime(job, export, state.links, state.boosts, state.activeBoosts.modifiers, state.eventSettings.events)
            val left = finishAt - now
            if (left <= Finish.GRACE_SEC) return@mapNotNull null
            val kind = when (job.source) {
                JobSource.HERO, JobSource.BB_HERO -> BookKind.HEROES
                JobSource.UNIT, JobSource.BB_UNIT -> BookKind.FIGHTING
                JobSource.SPELL -> BookKind.SPELLS
                else -> BookKind.BUILDING
            }
            BookCandidate(
                jobKey = job.key,
                dataId = job.dataId,
                name = names.title(job),
                targetLevel = job.targetLevel,
                remainingSec = left,
                bookKind = kind,
            )
        }.sortedByDescending { it.remainingSec }
    }

    fun findCwlConflicts(
        export: VillageExport?,
        engine: TimerEngine,
        state: AppState,
        now: Long,
        events: List<GameEvent>,
        names: NameMap,
    ): List<CwlConflict> {
        if (export == null) return emptyList()
        val cwlWars = events.firstOrNull { it.id == GameEvents.CWL_WARS } ?: return emptyList()
        val activeOrSoon = cwlWars.active || cwlWars.startsAt - now <= 10 * 86_400L
        if (!activeOrSoon) return emptyList()
        val gate = if (cwlWars.active) now else cwlWars.startsAt
        return export.jobs.mapNotNull { job ->
            val isHero = job.source == JobSource.HERO
            val isOffense = job.dataId in OFFENSE_BUILDING_IDS
            if (!isHero && !isOffense) return@mapNotNull null
            val finishAt = engine.finishTime(job, export, state.links, state.boosts, state.activeBoosts.modifiers, state.eventSettings.events)
            if (finishAt <= gate || Finish.isDone(finishAt, now)) return@mapNotNull null
            CwlConflict(
                jobKey = job.key,
                dataId = job.dataId,
                name = names.title(job),
                targetLevel = job.targetLevel,
                finishAt = finishAt,
                overlapSec = finishAt - gate,
                isHero = isHero,
                cwlActiveNow = cwlWars.active,
            )
        }.sortedWith(compareByDescending<CwlConflict> { it.isHero }.thenBy { it.finishAt })
    }

    /**
     * Estimates days needed to farm remaining equipment ores from daily Star Bonus ([league])
     * plus optional Clan Wars (assumed ~50% win rate average per war every ~2.3 days when [warsPerWeek] > 0).
     */
    fun estimateOreDays(
        neededShiny: Long,
        neededGlowy: Long,
        neededStarry: Long,
        league: OreLeague,
        warsPerWeek: Int = 2,
    ): OreEstimate {
        // Average ore per TH15-17 war (~70% win-weighted yield: ~950 shiny, ~35 glowy, ~4.5 starry) scaled by wars/7 days.
        val warFactor = warsPerWeek.coerceIn(0, 3) / 7.0
        val dailyShiny = league.shinyPerDay + warFactor * 950.0
        val dailyGlowy = league.glowyPerDay + warFactor * 35.0
        val dailyStarry = league.starryPerDay + warFactor * 4.5
        val dShiny = if (neededShiny <= 0) 0 else ceil(neededShiny / dailyShiny.coerceAtLeast(1.0)).toInt()
        val dGlowy = if (neededGlowy <= 0) 0 else ceil(neededGlowy / dailyGlowy.coerceAtLeast(1.0)).toInt()
        val dStarry = if (neededStarry <= 0) 0 else if (dailyStarry <= 0.0) 999 else ceil(neededStarry / dailyStarry).toInt()
        return OreEstimate(dShiny, dGlowy, dStarry, maxOf(dShiny, dGlowy, dStarry))
    }

    fun helperStatuses(export: VillageExport?, now: Long): List<HelperStatus> {
        if (export == null) return emptyList()
        return export.helpers.filter { it.level > 0 }.mapNotNull { h ->
            val role = when (h.dataId) {
                GameIds.HELPER_BUILDERS_APPRENTICE -> HelperRole.APPRENTICE
                GameIds.HELPER_LAB_ASSISTANT -> HelperRole.LAB_ASSISTANT
                GameIds.HELPER_ALCHEMIST -> HelperRole.ALCHEMIST
                else -> return@mapNotNull null
            }
            val readyAt = export.timestamp + h.cooldown.coerceAtLeast(0L)
            HelperStatus(
                dataId = h.dataId,
                role = role,
                level = h.level,
                readyAt = readyAt,
                remainingCooldownSec = (readyAt - now).coerceAtLeast(0L),
            )
        }.sortedBy { it.role.ordinal }
    }

    /** Tracks the 3 numeric Builder Base requirements toward B.O.B (6th Home Village builder). */
    fun bobProgress(export: VillageExport?, data: MaxData, names: NameMap): BobProgress? {
        if (export == null) return null
        if (export.hasBobsHut) {
            return BobProgress(
                unlocked = true,
                troop = BobRequirementProgress(18, 18),
                defense = BobRequirementProgress(9, 9),
                heroes = BobRequirementProgress(45, 45),
            )
        }
        if (Bedtime.builderHall(export) == null) return null

        var bestTroopId: Int? = null
        var bestTroopLvl = 0
        var bestDefId: Int? = null
        var bestDefLvl = 0
        var heroSum = 0

        for ((id, levels) in export.ownedLevels) {
            val item = data.items[id] ?: continue
            val top = levels.maxOrNull() ?: 0
            when {
                item.kind == 'S' && top > bestTroopLvl -> {
                    bestTroopLvl = top
                    bestTroopId = id
                }
                item.kind == 'B' && item.category == 'd' && top > bestDefLvl -> {
                    bestDefLvl = top
                    bestDefId = id
                }
                item.kind == 'M' -> heroSum += top
            }
        }
        return BobProgress(
            unlocked = false,
            troop = BobRequirementProgress(bestTroopLvl.coerceAtMost(18), 18, bestTroopId?.let(names::name)),
            defense = BobRequirementProgress(bestDefLvl.coerceAtMost(9), 9, bestDefId?.let(names::name)),
            heroes = BobRequirementProgress(heroSum.coerceAtMost(45), 45),
        )
    }

    /** Key offensive Town Hall upgrades (Army Camps, CC, Lab, Barracks, Spell Factories, Heroes, Blacksmith, Pet House). */
    fun offenseChecklist(road: RoadToMax): OffenseChecklist {
        val list = road.items
            .filter { it.id in OFFENSE_CHECKLIST_IDS || it.group == 'h' }
            .sortedWith(compareByDescending<RoadItem> { it.levelsLeft }.thenBy { it.group == 'h' }.thenBy { it.id })
        var have = 0L
        var max = 0L
        for (item in list) {
            have += item.levels.sumOf { it.coerceAtMost(item.maxLevel).toLong() }
            max += item.count.toLong() * item.maxLevel
        }
        return OffenseChecklist(list, LevelShare(have, max))
    }

    /** Estimates daily/weekly hours saved by Apprentice & Lab Assistant and which active upgrade gains the most. */
    fun helperRoiAnalysis(
        export: VillageExport?,
        engine: TimerEngine,
        state: AppState,
        now: Long,
        names: NameMap,
    ): List<HelperRoiInfo> {
        if (export == null) return emptyList()
        val out = mutableListOf<HelperRoiInfo>()
        for (h in export.helpers.filter { it.level > 0 }) {
            val role = when (h.dataId) {
                GameIds.HELPER_BUILDERS_APPRENTICE -> HelperRole.APPRENTICE
                GameIds.HELPER_LAB_ASSISTANT -> HelperRole.LAB_ASSISTANT
                else -> continue
            }
            val targetCat = if (role == HelperRole.APPRENTICE) JobCategory.BUILDER else JobCategory.LAB
            val dailyHours = h.level
            val jobs = export.jobs.filter { it.category == targetCat && !it.isGoblin }
            var bestName: String? = null
            var bestSavedHours = 0
            for (job in jobs) {
                val fin = engine.finishTime(job, export, state.links, state.boosts, state.activeBoosts.modifiers, state.eventSettings.events)
                val remSec = (fin - now).coerceAtLeast(0L)
                if (remSec <= Finish.GRACE_SEC) continue
                // Every 24h cycle with a helper saves ~`level` hours up to the full remaining duration.
                val cycleSpan = (23L + 1L) * 3600L
                val sessions = ((remSec / cycleSpan) + 1L).coerceAtLeast(1L)
                val estHours = minOf((sessions * dailyHours).toLong(), remSec / 3600L).toInt()
                if (estHours > bestSavedHours) {
                    bestSavedHours = estHours
                    bestName = "${names.title(job)} ➛ ${job.targetLevel}"
                }
            }
            out += HelperRoiInfo(
                role = role,
                level = h.level,
                hoursSavedPerDay = dailyHours,
                hoursSavedPerWeek = dailyHours * 7,
                bestJobName = bestName,
                bestJobSavedHours = bestSavedHours,
            )
        }
        return out
    }

    /** Estimates real calendar finish dates (with Apprentice & Lab Assistant pace) and Town Hall bottleneck. */
    fun roadForecast(road: RoadToMax, export: VillageExport?, builders: Int, now: Long): RoadForecast {
        val n = builders.coerceAtLeast(1)
        val apprenticeLvl = export?.helper(GameIds.HELPER_BUILDERS_APPRENTICE)?.level?.coerceAtLeast(0) ?: 0
        val labHelperLvl = export?.helper(GameIds.HELPER_LAB_ASSISTANT)?.level?.coerceAtLeast(0) ?: 0
        val builderRatio = (n * 24.0) / (n * 24.0 + apprenticeLvl)
        val labRatio = 24.0 / (24.0 + labHelperLvl)

        val builderSec = (road.builderSeconds(n) * builderRatio).toLong().coerceAtLeast(0L)
        val labSec = (road.lab.seconds * labRatio).toLong().coerceAtLeast(0L)
        val petSec = road.pets.seconds.coerceAtLeast(0L)
        val heroSec = (road.heroSeconds(n) * builderRatio).toLong().coerceAtLeast(0L)

        val bDays = ceil(builderSec / 86_400.0).toLong()
        val lDays = ceil(labSec / 86_400.0).toLong()
        val pDays = ceil(petSec / 86_400.0).toLong()
        val hDays = ceil(heroSec / 86_400.0).toLong()

        val sorted = listOf(bDays, lDays, pDays).sortedDescending()
        val maxDays = sorted.first()
        val secondDays = sorted.getOrElse(1) { 0L }
        val gap = (maxDays - secondDays).coerceAtLeast(0L)

        val bottleneck = when {
            maxDays == 0L -> RoadBottleneck.BALANCED
            lDays == maxDays && gap >= 3L -> RoadBottleneck.LAB
            pDays == maxDays && gap >= 3L -> RoadBottleneck.PETS
            bDays == maxDays && gap >= 3L -> {
                if (hDays > lDays && hDays >= (bDays * 0.70).toLong()) RoadBottleneck.HEROES
                else RoadBottleneck.BUILDERS
            }
            else -> RoadBottleneck.BALANCED
        }

        return RoadForecast(
            builderDaysWithHelpers = bDays,
            builderFinishEpoch = now + builderSec,
            labDaysWithHelper = lDays,
            labFinishEpoch = now + labSec,
            petDays = pDays,
            bottleneck = bottleneck,
            bottleneckGapDays = gap,
        )
    }

    /** Simulates how many lowest-level walls can be upgraded immediately with [budgetResources]. */
    fun simulateWallsForLoot(
        road: RoadToMax,
        data: MaxData,
        budgetResources: Long,
        builderPercent: Int = 0,
    ): WallSimResult {
        val wallsItem = road.items.firstOrNull { it.group == 'w' }
            ?: return WallSimResult(0, 0L, 0, 0)
        val steps = data.items[wallsItem.id]?.steps.orEmpty()
        val totalLeft = wallsItem.levelsLeft
        if (totalLeft <= 0 || budgetResources <= 0L) {
            return WallSimResult(0, 0L, totalLeft, totalLeft)
        }
        val gp = builderPercent.coerceIn(0, 20)
        val counts = IntArray(wallsItem.maxLevel + 1)
        for (lvl in wallsItem.levels) {
            val c = lvl.coerceIn(0, wallsItem.maxLevel)
            counts[c]++
        }
        var budget = budgetResources
        var upgraded = 0
        var spent = 0L
        for (lvl in 0 until wallsItem.maxLevel) {
            val avail = counts[lvl]
            if (avail <= 0) continue
            val rawCost = steps.getOrNull(lvl)?.cost ?: continue
            val unitCost = Math.round(rawCost * (100 - gp) / 100.0).coerceAtLeast(1L)
            val affordable = (budget / unitCost).toInt().coerceAtMost(avail)
            if (affordable > 0) {
                upgraded += affordable
                val used = affordable * unitCost
                spent += used
                budget -= used
                counts[lvl + 1] += affordable
            }
            if (affordable < avail) break
        }
        return WallSimResult(
            upgradedWalls = upgraded,
            spentCost = spent,
            wallsLeftAfter = (totalLeft - upgraded).coerceAtLeast(0),
            unupgradedTotal = totalLeft,
        )
    }
}
