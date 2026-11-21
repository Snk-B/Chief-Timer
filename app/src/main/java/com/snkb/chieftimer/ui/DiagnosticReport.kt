package com.snkb.chieftimer.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.snkb.chieftimer.core.AppState
import com.snkb.chieftimer.core.SyncLog
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.formatTimestamp
import com.snkb.chieftimer.crash.CrashReporter
import com.snkb.chieftimer.notify.Notifier
import com.snkb.chieftimer.sync.ClockTowerTracking
import com.snkb.chieftimer.widget.RoadWidgetUpdater
import com.snkb.chieftimer.widget.WidgetAlarm
import com.snkb.chieftimer.widget.WidgetIcons
import com.snkb.chieftimer.widget.WidgetUpdater

/** One row of the health checklist: a short label and whether it is fine. */
data class HealthCheck(val label: String, val ok: Boolean, val detail: String = "")

object DiagnosticReport {
    private const val LOG_LINES = 30
    private const val CRASH_LINES = 40
    private val PLAYER_TAG_REGEX = Regex("""#[0-9A-Za-z]{4,}""")

    internal fun redactPersonalData(text: String, playerName: String? = null, playerTag: String? = null): String {
        var out = text
        if (!playerTag.isNullOrBlank()) {
            out = out.replace(playerTag, "#REDACTED", ignoreCase = true)
        }
        out = PLAYER_TAG_REGEX.replace(out, "#REDACTED")
        val trimmedName = playerName?.trim()
        if (!trimmedName.isNullOrEmpty() && trimmedName.length >= 2) {
            out = out.replace(Regex(Regex.escape(trimmedName), RegexOption.IGNORE_CASE), "<player>")
        }
        return out
    }

    /** Settings that commonly stop timers and alerts, checked right now. */
    fun health(context: Context, state: AppState): List<HealthCheck> {
        val iconCount = runCatching { WidgetIcons.get(context).available.size }.getOrDefault(0)
        val battery = runCatching {
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        return listOf(
            HealthCheck("Saved village", state.export != null, if (state.export == null) "paste your export first" else ""),
            HealthCheck("Notifications allowed", Notifier.allowed(context), "finish alerts need this"),
            HealthCheck("Exact alarms allowed", WidgetAlarm.canScheduleExact(context), "alerts may be late without it"),
            HealthCheck("Battery: not restricted", battery, "restricted apps can stop updating"),
            HealthCheck("Icons bundled", iconCount > 0, "$iconCount files"),
        )
    }

    /** A plain-text summary for a GitHub issue. It never includes the player tag or name. */
    fun build(context: Context, state: AppState, model: WidgetModel, log: SyncLog, now: Long): String = buildString {
        appendLine("Chief Timer diagnostic report")
        appendLine("App: ${appVersion(context)}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Time: ${formatTimestamp(now)}")
        appendLine()
        appendLine("Health")
        health(context, state).forEach { appendLine("  ${if (it.ok) "OK  " else "FAIL"} ${it.label}${if (it.detail.isNotEmpty() && !it.ok) " (${it.detail})" else ""}") }
        appendLine("  Clock Tower chip: ${if (ClockTowerTracking.enabled(context)) "on" else "off"}, alert: ${if (ClockTowerTracking.alertEnabled(context)) "on" else "off"}")
        appendLine()
        appendLine("Widgets")
        val count = runCatching { WidgetUpdater.widgetIds(context).size + RoadWidgetUpdater.ids(context).size }.getOrDefault(-1)
        appendLine("  placed: $count")
        runCatching { widgetSizeReport(context, state) }.getOrNull()?.lines()?.filter { it.isNotBlank() }?.forEach { appendLine("  $it") }
        appendLine()
        appendLine("Village")
        val e = state.export
        if (e == null) appendLine("  none saved") else {
            appendLine("  Town Hall: ${e.townHallLevel ?: "?"}, jobs: ${e.jobs.size}, copied: ${formatTimestamp(e.timestamp)}")
            val boosts = listOfNotNull(
                e.builderBoostRemaining?.let { "builder potion ${it}s" },
                e.labBoostRemaining?.let { "research potion ${it}s" },
                e.petBoostRemaining?.let { "pet potion ${it}s" },
                e.builderBiteRemaining?.let { "builder bite ${it}s" },
                e.studySoupRemaining?.let { "study soup ${it}s" },
                e.clockTowerLevel?.let { "clock tower L$it" },
                e.clockTowerBoostRemaining?.let { "clock tower boost ${it}s" },
                e.clockTowerCooldownRemaining?.let { "clock tower cooldown ${it}s" },
            )
            appendLine("  Detected: ${if (boosts.isEmpty()) "no boosts" else boosts.joinToString(", ")}")
        }
        appendLine()
        appendLine("Row icons (name -> data ids, level -> file)")
        val icons = WidgetIcons.get(context)
        (model.builderRows + model.labRows + listOf(model.petRow) + model.bbRows + listOfNotNull(model.starLabRow)).forEach { r ->
            appendLine("  ${r.name}: ${r.iconIds} L${r.iconLevel ?: "-"} -> ${icons.fileFor(r.iconIds, r.iconLevel) ?: "none"}")
        }
        appendLine()
        appendLine("Widget text")
        val previewLines = widgetPreview(model).lines()
        val sanitizedPreview = previewLines.mapIndexed { idx, line ->
            if (idx == 0 && line.endsWith("   [Sync]") && line != "Chief Timer   [Sync]") {
                "Chief Timer   [Sync]"
            } else {
                redactPersonalData(line, playerTag = e?.tag)
            }
        }
        sanitizedPreview.forEach { appendLine("  $it") }
        appendLine()
        appendLine("Recent activity (newest first)")
        if (log.entries.isEmpty()) appendLine("  none")
        log.entries.take(LOG_LINES).forEach { appendLine("  ${redactPersonalData(it.line(), playerTag = e?.tag)}") }
        appendLine()
        appendLine("Last crash")
        val crash = CrashReporter.lastReport(context)
        if (crash == null) appendLine("  none saved") else crash.lines().take(CRASH_LINES).forEach { appendLine("  $it") }
    }
}
