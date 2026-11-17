package com.killersparrow1.clashtrackers.core

import android.content.Context
import com.killersparrow1.clashtrackers.R
import com.killersparrow1.clashtrackers.locale.LanguageManager

/** What happens to the stored state when the player taps Sync. */
object SyncLogic {

    sealed interface Outcome {
        val state: AppState

        data class Synced(override val state: AppState, val export: VillageExport) : Outcome
        data class Failed(override val state: AppState, val reason: String) : Outcome
    }

    fun apply(old: AppState, export: VillageExport, rawJson: String): Outcome {
        fun fail(reason: String) = Outcome.Failed(old.copy(syncFailed = true), reason)

        val previous = old.export
        if (previous != null && export.timestamp < previous.timestamp) {
            return fail("Clipboard export is older than the last sync")
        }

        val links = HelperLinks(
            apprenticeJobKey = linkFor(JobCategory.BUILDER, export, previous, old.links.apprenticeJobKey),
            labAssistantJobKey = linkFor(JobCategory.LAB, export, previous, old.links.labAssistantJobKey),
        )
        var boosts = old.boosts.filter { it.end > export.timestamp }
        // `boosts.builder_boost` is the game's own remaining builder-potion time (stacked potions included),
        // so it replaces any builder-potion windows tapped on the widget instead of adding to them.
        export.builderBoostRemaining?.let { left ->
            boosts = boosts.filterNot { it.type == BoostType.BUILDER_POTION } +
                BoostUse(BoostType.BUILDER_POTION, export.timestamp, export.timestamp + left)
        }

        // Research and pet potions: same idea, the game's own remaining time replaces any tapped windows.
        export.labBoostRemaining?.let { left ->
            boosts = boosts.filterNot { it.type == BoostType.RESEARCH_POTION } +
                BoostUse(BoostType.RESEARCH_POTION, export.timestamp, export.timestamp + left)
        }
        export.petBoostRemaining?.let { left ->
            boosts = boosts.filterNot { it.type == BoostType.PET_POTION } +
                BoostUse(BoostType.PET_POTION, export.timestamp, export.timestamp + left)
        }
        // Builder Bite: the game's own remaining time replaces any Bite windows tapped on the widget.
        export.studySoupRemaining?.let { left ->
            boosts = boosts.filterNot { it.type == BoostType.STUDY_SOUP } +
                BoostUse(BoostType.STUDY_SOUP, export.timestamp, export.timestamp + left)
        }
        export.builderBiteRemaining?.let { left ->
            boosts = boosts.filterNot { it.type == BoostType.BUILDER_BITE } +
                BoostUse(BoostType.BUILDER_BITE, export.timestamp, export.timestamp + left)
        }

        return Outcome.Synced(
            AppState(
                exportJson = rawJson.trim(),
                links = links,
                boosts = boosts,
                syncFailed = false,
                firstRemaining = export.jobs.associate { job ->
                    // max(): a key can be reused by a different instance (e.g. the second of two
                    // identical upgrades after the first finished); never shrink the bar's length.
                    job.key to maxOf(old.firstRemaining[job.key] ?: 0L, job.remainingAtT0)
                },
            ),
            export,
        )
    }

    fun apply(old: AppState, clipboard: String?): Outcome {
        fun fail(reason: String) = Outcome.Failed(old.copy(syncFailed = true), reason)

        if (clipboard.isNullOrBlank()) return fail("Clipboard is empty")
        val export = when (val r = ExportParser.parse(clipboard)) {
            is ParseResult.Invalid -> return fail("Not village data: ${r.reason}")
            is ParseResult.Success -> r.export
        }
        return apply(old, export, clipboard)
    }

    /**
     * `helper_recurrent: true` on a running job sets the link. On a finished item (no `timer`) the helper
     * is idle → no link. Otherwise a manual link from the widget is carried over if its job still runs.
     */
    private fun linkFor(category: JobCategory, export: VillageExport, previous: VillageExport?, oldKey: String?): String? {
        val candidates = export.jobs.filter { it.category == category }
        candidates.firstOrNull { it.helperLinked }?.let { return it.key }
        if (category in export.helperOnFinishedItem) return null
        return carryOver(oldKey, previous, candidates)
    }

    /**
     * Keeps a manual link only if the same job (category + data + target level) still exists.
     * Prefers the exact same occurrence (e.g. the second of two identical Wizard Towers).
     */
    private fun carryOver(key: String?, previous: VillageExport?, candidates: List<Job>): String? {
        if (key == null) return null
        candidates.firstOrNull { it.key == key }?.let { return it.key }
        val old = previous?.jobs?.firstOrNull { it.key == key } ?: return null
        return candidates.firstOrNull {
            it.category == old.category && it.dataId == old.dataId && it.targetLevel == old.targetLevel
        }?.key
    }
}

/** Plain-text summary of a sync outcome for the toast and the launcher screen. */
fun SyncLogic.Outcome.describe(names: NameMap): String = when (this) {
    is SyncLogic.Outcome.Failed -> "Sync failed: $reason"
    is SyncLogic.Outcome.Synced -> buildString {
        val e = export
        append("Synced")
        e.townHallLevel?.let { append(" TH$it") }
        val goblin = e.builderJobs.count { it.isGoblin }
        append(" · ${e.regularBuilderJobs.size} builder job")
        if (e.regularBuilderJobs.size != 1) append("s")
        if (goblin > 0) append(" + Goblin")
        append(" · lab: ").append(e.labJobs.takeIf { it.isNotEmpty() }?.joinToString(" + ") { names.label(it) } ?: "idle")
        append(" · pet: ").append(e.petJob?.let(names::label) ?: "idle")
        e.builderBoostRemaining?.let { append(" · builder potion ${WidgetModelBuilder.formatRemaining(it)} left") }
        e.labBoostRemaining?.let { append(" · research potion ${WidgetModelBuilder.formatRemaining(it)} left") }
        e.petBoostRemaining?.let { append(" · pet potion ${WidgetModelBuilder.formatRemaining(it)} left") }
        e.studySoupRemaining?.let { append(" · study soup ${WidgetModelBuilder.formatRemaining(it)} left") }
        e.clockTowerCooldownRemaining?.let { append(" · clock tower cooldown ${WidgetModelBuilder.formatRemaining(it)}") }
        e.clockTowerBoostRemaining?.let { append(" · clock tower boost ${WidgetModelBuilder.formatRemaining(it)} left") }
        e.builderBiteRemaining?.let { append(" · builder bite ${WidgetModelBuilder.formatRemaining(it)} left") }
    }
}

/** Localized summary of a sync outcome for toasts and user-facing notifications. */
fun SyncLogic.Outcome.describe(context: Context, names: NameMap): String {
    val lctx = LanguageManager.wrap(context)
    val res = lctx.resources
    return when (this) {
        is SyncLogic.Outcome.Failed -> {
            when {
                reason.contains("empty", ignoreCase = true) -> res.getString(R.string.sync_fail_empty)
                reason.contains("Not village data", ignoreCase = true) -> res.getString(R.string.sync_fail_not_village)
                reason.contains("older", ignoreCase = true) -> res.getString(R.string.sync_fail_stale)
                else -> "${res.getString(R.string.sync_failed)}: $reason"
            }
        }
        is SyncLogic.Outcome.Synced -> buildString {
            val e = export
            append(res.getString(R.string.sync_toast_synced))
            e.townHallLevel?.let {
                append(" ")
                append(res.getString(R.string.sync_toast_th, it))
            }
            val goblin = e.builderJobs.count { it.isGoblin }
            val builderJobsCount = e.regularBuilderJobs.size
            val builderJobsStr = if (builderJobsCount == 1) {
                res.getString(R.string.sync_toast_builder_single)
            } else {
                res.getString(R.string.sync_toast_builders, builderJobsCount)
            }
            append(" · ").append(builderJobsStr)
            if (goblin > 0) append(res.getString(R.string.sync_toast_goblin))

            val idleStr = res.getString(R.string.sync_toast_idle)
            val labDesc = e.labJobs.takeIf { it.isNotEmpty() }?.joinToString(" + ") { names.label(it) } ?: idleStr
            append(" · ").append(res.getString(R.string.sync_toast_lab, labDesc))

            val petDesc = e.petJob?.let(names::label) ?: idleStr
            append(" · ").append(res.getString(R.string.sync_toast_pet, petDesc))

            e.builderBoostRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_builder_potion, WidgetModelBuilder.formatRemaining(it)))
            }
            e.labBoostRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_research_potion, WidgetModelBuilder.formatRemaining(it)))
            }
            e.petBoostRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_pet_potion, WidgetModelBuilder.formatRemaining(it)))
            }
            e.studySoupRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_study_soup, WidgetModelBuilder.formatRemaining(it)))
            }
            e.clockTowerCooldownRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_ct_cooldown, WidgetModelBuilder.formatRemaining(it)))
            }
            e.clockTowerBoostRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_ct_boost, WidgetModelBuilder.formatRemaining(it)))
            }
            e.builderBiteRemaining?.let {
                append(" · ").append(res.getString(R.string.sync_toast_builder_bite, WidgetModelBuilder.formatRemaining(it)))
            }
        }
    }
}
