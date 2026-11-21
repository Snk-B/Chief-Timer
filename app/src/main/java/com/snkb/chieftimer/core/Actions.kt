package com.snkb.chieftimer.core

import java.util.Locale

/** What a widget button does. Pure state changes; the receiver saves and redraws. */
object WidgetActions {

    /** Tap action names, also used in the tap intents' URIs. */
    const val HELPER = "helper"
    const val ADD_BOOST = "add"
    const val UNDO_BOOST = "undo"

    /**
     * Green `+` / gold `H` on a row. `+` links the category's helper to [jobKey], moving it off any
     * other row (one link per helper); tapping the row the helper is on unlinks it. Pets have no helper.
     */
    fun toggleHelper(state: AppState, jobKey: String): AppState {
        val job = state.export?.jobs?.firstOrNull { it.key == jobKey } ?: return state
        val links = state.links
        val newLinks = when (job.category) {
            JobCategory.BUILDER ->
                links.copy(apprenticeJobKey = if (links.apprenticeJobKey == jobKey) null else jobKey)
            JobCategory.LAB ->
                links.copy(labAssistantJobKey = if (links.labAssistantJobKey == jobKey) null else jobKey)
            JobCategory.PET, JobCategory.BB_BUILDER, JobCategory.STAR_LAB -> return state
        }
        return state.copy(links = newLinks)
    }

    /** Potion / Bite button: one more hour of [type] from [now], or right after the running one. */
    fun addBoost(state: AppState, type: BoostType, now: Long, settings: BoostSettings): AppState {
        if (state.export == null) return state
        // Windows that ended before the export are already in its timers; drop them while we're here.
        val kept = state.boosts.filter { it.end > (state.export?.timestamp ?: 0) }
        return state.copy(boosts = BoostStacking.add(kept, type, now, settings.boostDurationSec))
    }

    /** Small `−` next to a running boost: undo the last hour of [type]. */
    fun undoBoost(state: AppState, type: BoostType, now: Long): AppState =
        state.copy(boosts = BoostStacking.undoLast(state.boosts, type, now))

    /** Dispatches a tap by its action name and argument (job key or [BoostType] name); unknown taps change nothing. */
    fun apply(state: AppState, action: String, arg: String, now: Long, settings: BoostSettings): AppState {
        val type = BoostType.entries.firstOrNull { it.name == arg }
        return when (action) {
            HELPER -> toggleHelper(state, arg)
            ADD_BOOST -> type?.let { addBoost(state, it, now, settings) } ?: state
            UNDO_BOOST -> type?.let { undoBoost(state, it, now) } ?: state
            else -> state
        }
    }

    /** One line for the on-screen log, e.g. `Apprentice → Logger → 5` or `Builder potion +1 h (until 23:44)`. */
    fun describe(before: AppState, after: AppState, action: String, arg: String, names: NameMap): String {
        fun jobName(key: String?) = key?.let { k -> after.export?.jobs?.firstOrNull { it.key == k } }?.let(names::label)
        return when (action) {
            HELPER -> {
                val (helper, old, new) = if (after.links.apprenticeJobKey != before.links.apprenticeJobKey) {
                    Triple("Apprentice", before.links.apprenticeJobKey, after.links.apprenticeJobKey)
                } else {
                    Triple("Lab Assistant", before.links.labAssistantJobKey, after.links.labAssistantJobKey)
                }
                when {
                    old == new -> "Helper tap changed nothing"
                    new == null -> "$helper unlinked from ${jobName(old) ?: "?"}"
                    else -> "$helper linked to ${jobName(new)}" + (jobName(old)?.let { " (moved from $it)" } ?: "")
                }
            }
            ADD_BOOST -> "${boostName(arg)} +1 h" + if (after.boosts.size == before.boosts.size) " (ignored)" else ""
            UNDO_BOOST -> "${boostName(arg)} undo" + if (after.boosts == before.boosts) " (nothing to undo)" else ""
            else -> "Unknown tap $action/$arg"
        }
    }

    private fun boostName(arg: String) = when (arg) {
        BoostType.BUILDER_POTION.name -> "Builder potion"
        BoostType.BUILDER_BITE.name -> "Builder Bite"
        BoostType.STUDY_SOUP.name -> "Study Soup"
        BoostType.RESEARCH_POTION.name -> "Research potion"
        BoostType.PET_POTION.name -> "Pet potion"
        else -> arg
    }
}

/** A "something finished" notification. */
data class FinishNotice(val jobKey: String, val title: String, val text: String)

/** Localized notification templates; English is the default so unit tests remain Android-free. */
data class NoticeText(
    val builderFree: String = "Builder free: %1\$s",
    val goblinDone: String = "Goblin Builder done: %1\$s",
    val bbBuilderFree: String = "Builder Base builder free: %1\$s",
    val labFree: String = "Lab free",
    val starLabFree: String = "Star Lab free",
    val petFree: String = "Pet house free",
    val itemBuilt: String = "%1\$s is built.",
    val itemReachedLevel: String = "%1\$s reached level %2\$d.",
    val labStillFreeTitle: String = "Lab is still free",
    val labStillFreeText: String = "The lab has been free for %1\$d h. Start a research.",
    val builderStillFreeTitle: String = "Builder is still free",
    val builderStillFreeText: String = "A builder has been free for %1\$d h. Start an upgrade.",
    val clockTowerTitle: String = FinishNotices.CLOCK_TOWER_TITLE,
    val clockTowerText: String = FinishNotices.CLOCK_TOWER_TEXT,
) {
    companion object {
        val EN = NoticeText()
    }
}

object FinishNotices {
    /** Jobs that finished longer ago than this are marked as notified without a notification. */
    const val MAX_AGE_SEC = 12 * 3600L

    data class Result(val notices: List<FinishNotice>, val notified: Set<String>)

    /**
     * Jobs finished at [now] that haven't been announced yet, and the new [AppState.notified] set
     * (only keys that are still finished, so a job that is "un-finished" by an undo can be announced again).
     */
    fun check(
        state: AppState,
        settings: AppSettings,
        names: NameMap,
        now: Long,
        clockTowerAlert: Boolean = false,
        idleAfterSec: Long? = null,
        text: NoticeText = NoticeText.EN,
    ): Result {
        val export = state.export ?: return Result(emptyList(), emptySet())
        val engine = TimerEngine(settings.boosts)
        val finished = export.jobs
            .map { it to engine.finishTime(it, export, state.links, state.boosts) }
            .filter { (_, at) -> Finish.isDone(at, now) }
        val notices = finished
            .filter { (job, at) -> job.key !in state.notified && at > now - MAX_AGE_SEC }
            .sortedBy { it.second }
            .map { (job, _) -> notice(job, names, text) }
        val ctEnd = if (clockTowerAlert) clockTowerEnd(export) else null
        val ctDone = ctEnd != null && Finish.isDone(ctEnd, now)
        val ctKey = ctEnd?.let { "$CLOCK_TOWER_KEY$it" }
        val ctNotice = if (ctDone && ctKey !in state.notified && ctEnd!! > now - MAX_AGE_SEC)
            listOf(FinishNotice(ctKey!!, text.clockTowerTitle, text.clockTowerText)) else emptyList()
        // "Still free" reminders: a builder or lab job finished [idleAfterSec] ago and the next sync hasn't replaced it.
        val idleDue = if (idleAfterSec == null) emptyList() else finished
            .filter { (job, _) -> (job.category == JobCategory.BUILDER || job.category == JobCategory.LAB) && !job.isGoblin }
            .filter { (_, at) -> Finish.isDone(at + idleAfterSec, now) }
        val idleNotices = idleDue
            .filter { (job, at) -> IDLE_KEY + job.key !in state.notified && at + idleAfterSec!! > now - MAX_AGE_SEC }
            .sortedBy { it.second }
            .map { (job, _) -> idleNotice(job, idleAfterSec!!, text) }
        val keys = finished.map { it.first.key }.toSet() + listOfNotNull(ctKey.takeIf { ctDone }) + idleDue.map { IDLE_KEY + it.first.key }
        return Result(notices + idleNotices + ctNotice, keys)
    }

    const val IDLE_KEY = "idle:"

    fun idleNotice(job: Job, afterSec: Long, text: NoticeText = NoticeText.EN): FinishNotice {
        val hours = (afterSec / 3600).coerceAtLeast(1)
        val (title, body) = if (job.category == JobCategory.LAB) {
            text.labStillFreeTitle to String.format(Locale.ROOT, text.labStillFreeText, hours)
        } else {
            text.builderStillFreeTitle to String.format(Locale.ROOT, text.builderStillFreeText, hours)
        }
        return FinishNotice(IDLE_KEY + job.key, title, body)
    }

    const val CLOCK_TOWER_KEY = "clocktower:"
    const val CLOCK_TOWER_TITLE = "Clock Tower ready"
    const val CLOCK_TOWER_TEXT = "Its cooldown is over, you can boost the Builder Base again."

    /** Epoch s when the exported Clock Tower cooldown ends; null if there is none. */
    fun clockTowerEnd(export: VillageExport): Long? =
        export.clockTowerCooldownRemaining?.takeIf { it > 0 }?.let { export.timestamp + it }

    /** Next finish time (epoch s) of a job that isn't [Finish.isDone] at [now]. */
    fun nextFinish(state: AppState, settings: AppSettings, now: Long, clockTowerAlert: Boolean = false, idleAfterSec: Long? = null): Long? {
        val export = state.export ?: return null
        val engine = TimerEngine(settings.boosts)
        val ct = if (clockTowerAlert) clockTowerEnd(export) else null
        val finishes = export.jobs.map { it to engine.finishTime(it, export, state.links, state.boosts) }
        val idle = if (idleAfterSec == null) emptyList() else finishes
            .filter { (job, _) -> (job.category == JobCategory.BUILDER || job.category == JobCategory.LAB) && !job.isGoblin }
            .map { it.second + idleAfterSec }
        return (finishes.map { it.second } + idle + listOfNotNull(ct))
            .filter { !Finish.isDone(it, now) }.minOrNull()
    }

    /**
     * Epoch s for the wakeup finish alarm: [Finish.GRACE_SEC] after the next finish, so the redraw it
     * triggers is certain to see the job as done.
     */
    fun alarmAt(state: AppState, settings: AppSettings, now: Long, clockTowerAlert: Boolean = false, idleAfterSec: Long? = null): Long? =
        nextFinish(state, settings, now, clockTowerAlert, idleAfterSec)?.plus(Finish.GRACE_SEC)

    fun notice(job: Job, names: NameMap, text: NoticeText = NoticeText.EN): FinishNotice {
        val title = names.title(job)
        val body = if (job.isNewBuilding) {
            String.format(Locale.ROOT, text.itemBuilt, title)
        } else {
            String.format(Locale.ROOT, text.itemReachedLevel, title, job.targetLevel)
        }
        val heading = when {
            job.category == JobCategory.LAB -> text.labFree
            job.category == JobCategory.PET -> text.petFree
            job.category == JobCategory.BB_BUILDER -> String.format(Locale.ROOT, text.bbBuilderFree, title)
            job.category == JobCategory.STAR_LAB -> text.starLabFree
            job.isGoblin -> String.format(Locale.ROOT, text.goblinDone, title)
            else -> String.format(Locale.ROOT, text.builderFree, title)
        }
        return FinishNotice(job.key, heading, body)
    }
}
