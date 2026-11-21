package com.snkb.chieftimer.core

import android.content.Context
import com.snkb.chieftimer.R
import com.snkb.chieftimer.locale.LanguageManager

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
        // A helper actively working right now (`helper_timer > 0`) links immediately even without `helper_recurrent: true`.
        candidates.firstOrNull { it.helperTimer > 0 }?.let { return it.key }
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
    val lang = LanguageManager.language(context).lowercase()
    val isPt = lang == "pt"
    val isRu = lang == "ru"
    val lctx = LanguageManager.wrap(context)
    val res = lctx.resources
    return when (this) {
        is SyncLogic.Outcome.Failed -> {
            when {
                reason.contains("empty", ignoreCase = true) -> {
                    if (isPt) "Falha na sincronização: A área de transferência está vazia"
                    else if (isRu) "Ошибка синхронизации: Буфер обмена пуст"
                    else res.getString(R.string.sync_fail_empty)
                }
                reason.contains("Not village data", ignoreCase = true) -> {
                    if (isPt) "Falha na sincronização: Os dados não são de uma vila válida"
                    else if (isRu) "Ошибка синхронизации: Неверные данные деревни"
                    else res.getString(R.string.sync_fail_not_village)
                }
                reason.contains("older", ignoreCase = true) -> {
                    if (isPt) "Falha na sincronização: A exportação copiada é mais antiga que a última sincronização"
                    else if (isRu) "Ошибка синхронизации: Данные в буфере обмена старше последней синхронизации"
                    else res.getString(R.string.sync_fail_stale)
                }
                else -> {
                    val prefix = if (isPt) "Falha na sincronização" else if (isRu) "Ошибка синхронизации" else res.getString(R.string.sync_failed)
                    "$prefix: $reason"
                }
            }
        }
        is SyncLogic.Outcome.Synced -> buildString {
            val e = export
            val syncedLabel = if (isPt) "Sincronizado" else if (isRu) "Синхронизировано" else res.getString(R.string.sync_toast_synced)
            append(syncedLabel)
            e.townHallLevel?.let {
                val thLabel = if (isPt) "CV$it" else if (isRu) "ТХ$it" else res.getString(R.string.sync_toast_th, it)
                append(" $thLabel")
            }
            val goblin = e.builderJobs.count { it.isGoblin }
            val builderJobsCount = e.regularBuilderJobs.size
            val builderJobsStr = if (isPt) {
                if (builderJobsCount == 1) "1 construtor" else "$builderJobsCount construtores"
            } else if (isRu) {
                if (builderJobsCount == 1) "1 строитель" else "$builderJobsCount строителей"
            } else {
                if (builderJobsCount == 1) res.getString(R.string.sync_toast_builder_single) else res.getString(R.string.sync_toast_builders, builderJobsCount)
            }
            append(" · ").append(builderJobsStr)
            if (goblin > 0) append(if (isPt) " + Goblin" else if (isRu) " + Гоблин" else res.getString(R.string.sync_toast_goblin))

            val idleStr = if (isPt) "livre" else if (isRu) "свободно" else res.getString(R.string.sync_toast_idle)
            val labDesc = e.labJobs.takeIf { it.isNotEmpty() }?.joinToString(" + ") { names.label(it) } ?: idleStr
            val labPrefix = if (isPt) "laboratório" else if (isRu) "лаборатория" else "lab"
            append(" · $labPrefix: $labDesc")

            val petDesc = e.petJob?.let(names::label) ?: idleStr
            val petPrefix = if (isPt) "animais" else if (isRu) "питомец" else "pet"
            append(" · $petPrefix: $petDesc")

            e.builderBoostRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · poção do construtor: $t restante")
                else if (isRu) append(" · зелье строителя: $t осталось")
                else append(" · builder potion $t left")
            }
            e.labBoostRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · poção de pesquisa: $t restante")
                else if (isRu) append(" · зелье исследований: $t осталось")
                else append(" · research potion $t left")
            }
            e.petBoostRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · poção de animais: $t restante")
                else if (isRu) append(" · зелье питомцев: $t осталось")
                else append(" · pet potion $t left")
            }
            e.studySoupRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · sopa de estudos: $t restante")
                else if (isRu) append(" · суп для учебы: $t осталось")
                else append(" · study soup $t left")
            }
            e.clockTowerCooldownRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · recarga da torre do relógio: $t")
                else if (isRu) append(" · перезарядка часовой башни: $t")
                else append(" · clock tower cooldown $t")
            }
            e.clockTowerBoostRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · aceleração da torre do relógio: $t restante")
                else if (isRu) append(" · ускорение часовой башни: $t осталось")
                else append(" · clock tower boost $t left")
            }
            e.builderBiteRemaining?.let {
                val t = WidgetModelBuilder.formatRemaining(it)
                if (isPt) append(" · mordida do construtor: $t restante")
                else if (isRu) append(" · укус строителя: $t осталось")
                else append(" · builder bite $t left")
            }
        }
    }
}
