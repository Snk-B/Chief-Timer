package com.snkb.chieftimer.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.snkb.chieftimer.core.AccountSettings
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.Finish
import com.snkb.chieftimer.core.FinishNotices
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.WidgetActions
import com.snkb.chieftimer.core.WidgetModelBuilder
import com.snkb.chieftimer.core.WidgetRow
import com.snkb.chieftimer.core.WidgetSchedule
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.data.Times
import com.snkb.chieftimer.data.WidgetTexts
import com.snkb.chieftimer.notify.LiveCountdownService
import com.snkb.chieftimer.notify.Notifier
import com.snkb.chieftimer.R
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.sync.LiveUpdateAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The home-screen widget, drawn with plain RemoteViews (not Glance) so it can use the Lilita One
 * font, Chronometer countdowns, progress bars and the design's drawables.
 * The class name is unchanged from the Glance version so a placed widget keeps working.
 *
 * Also the app's single broadcast entry point: widget button taps (step 5), the redraw and
 * finish alarms, and boot / app update / clock change, which all end in [WidgetUpdater.updateAll].
 */
class ChiefWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetReceiverDispatch.refresh(this, context, "widget")

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        WidgetReceiverDispatch.refresh(this, context, "widget")

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetReceiverDispatch.runAsync(this) {
            val repo = StateRepository(context.applicationContext)
            for (id in appWidgetIds) {
                repo.removeWidgetAccount(id)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, ACTION_QUICKBOOT,
            -> WidgetReceiverDispatch.refresh(this, context, "system")
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) = WidgetAlarm.cancelRedraw(context)

    companion object {
        const val ACTION_TICK = "com.snkb.chieftimer.WIDGET_TICK"
        const val ACTION_FINISH = "com.snkb.chieftimer.JOB_FINISH"
        const val ACTION_TAP = "com.snkb.chieftimer.WIDGET_TAP"
        const val ACTION_DISMISS_LIVE = "com.snkb.chieftimer.DISMISS_LIVE"
        const val EXTRA_ACCOUNT_TAG = "accountTag"
        const val EXTRA_JOB_KEY = "jobKey"
        const val EXTRA_FINISH_AT = "finishAt"
        /** Some ROMs send this instead of BOOT_COMPLETED after a fast reboot. */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}

/**
 * Unexported receiver dedicated to internal widget taps, scheduled alarms, and Live Update dismissal
 * so external apps cannot forge internal state mutations via the exported AppWidgetProvider.
 */
class WidgetInternalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ChiefWidgetReceiver.ACTION_TICK -> WidgetReceiverDispatch.refresh(this, context, "widget")
            ChiefWidgetReceiver.ACTION_FINISH -> WidgetReceiverDispatch.refresh(this, context, "alarm")
            ChiefWidgetReceiver.ACTION_TAP -> WidgetReceiverDispatch.tap(this, context, intent)
            ChiefWidgetReceiver.ACTION_DISMISS_LIVE -> {
                val tag = intent.getStringExtra(ChiefWidgetReceiver.EXTRA_ACCOUNT_TAG)
                val key = intent.getStringExtra(ChiefWidgetReceiver.EXTRA_JOB_KEY)
                val fin = intent.getLongExtra(ChiefWidgetReceiver.EXTRA_FINISH_AT, 0L)
                if (!tag.isNullOrBlank() && !key.isNullOrBlank() && fin > 0L) {
                    LiveUpdateAlert.dismissJob(context, tag, key, fin)
                    LiveCountdownService.onJobDismissed(context)
                }
                WidgetReceiverDispatch.refresh(this, context, "dismiss_live")
            }
        }
    }
}

internal object WidgetReceiverDispatch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(receiver: BroadcastReceiver, context: Context, source: String) =
        runAsync(receiver) { WidgetUpdater.updateAll(context, source) }

    /** A widget button: `chieftimer://tap/<action>/<arg>?account=<tag>`. Saved, logged, then redrawn at once. */
    fun tap(receiver: BroadcastReceiver, context: Context, intent: Intent) {
        val segments = intent.data?.pathSegments.orEmpty()
        val action = segments.getOrNull(0) ?: return
        val arg = segments.getOrNull(1) ?: return
        val accountTag = intent.data?.getQueryParameter("account")
        runAsync(receiver) {
            val app = context.applicationContext
            val repo = StateRepository(app)
            try {
                val accounts = repo.accounts.first()
                val selected = repo.selectedAccountTag.first()
                val tag = accountTag ?: selected ?: accounts.firstOrNull()?.tag ?: return@runAsync
                val settings = repo.getAccountSettings(tag)
                val now = System.currentTimeMillis() / 1000
                repo.updateAccountState(tag) { before ->
                    WidgetActions.apply(before, action, arg, now, settings.boosts)
                }
                repo.log("tap", null, "Tap on $tag: $action/$arg")
            } catch (t: Throwable) {
                repo.log("tap", false, "Tap failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            WidgetUpdater.updateAll(app, "tap")
        }
    }

    fun runAsync(receiver: BroadcastReceiver, block: suspend () -> Unit) {
        val pending = receiver.goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }
}

object WidgetUpdater {

    /** The model one placed widget shows right now (also used by the scrolling list's service), or null with no data. */
    suspend fun modelFor(context: Context, id: Int): Pair<com.snkb.chieftimer.core.WidgetModel, Boolean> {
        val app = context.applicationContext
        val repo = StateRepository(app)
        val accounts = repo.accounts.first()
        val tag = repo.getWidgetAccount(id) ?: repo.selectedAccountTag.first() ?: accounts.firstOrNull()?.tag
        val state = if (tag != null) repo.getAccountState(tag) else repo.currentState()
        val acctSettings = if (tag != null) repo.getAccountSettings(tag) else AccountSettings()
        val global = repo.currentSettings()
        val settings = AppSettings(
            totalBuildersOverride = acctSettings.totalBuildersOverride,
            boosts = acctSettings.boosts,
            notificationsEnabled = global.notificationsEnabled,
            liveCountdown = global.liveCountdown,
            playerName = acctSettings.playerName,
        )
        val model = WidgetModelBuilder.build(
            state, settings, Names.get(app), Times.get(app), System.currentTimeMillis() / 1000, tag ?: "",
            repo.getWidgetTheme(id), WidgetTexts.get(app), GoldPassStore.reduction(app, tag),
        )
        return model to settings.liveCountdown
    }

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ChiefWidgetReceiver::class.java))

    /**
     * Everything that follows a change or an alarm: posts "… free" notifications for jobs that just
     * finished across all accounts, re-renders every placed widget with its account's state, and schedules alarms.
     */
    suspend fun updateAll(context: Context, source: String): Int {
        val app = context.applicationContext
        val repo = StateRepository(app)
        return try {
            val names = Names.get(app)
            val widgetText = WidgetTexts.get(app)
            val noticeText = WidgetTexts.noticeText(app)
            val nowMs = System.currentTimeMillis()
            val now = nowMs / 1000

            val accounts = repo.accounts.first()
            val selectedTag = repo.selectedAccountTag.first()
            val globalSettings = repo.currentSettings()

            val allFinishTimes = mutableListOf<Long>()
            val dueLiveItems = mutableListOf<Notifier.LiveCountdownItem>()
            val ctAlert = com.snkb.chieftimer.sync.ClockTowerTracking.alertEnabled(app)
            val idleAfter = com.snkb.chieftimer.sync.IdleAlert.afterSec(app)
            val liveLeadSec = if (globalSettings.notificationsEnabled) LiveUpdateAlert.leadSec(app) else null
            val lctx = com.snkb.chieftimer.locale.LanguageManager.wrap(app)
            val timesData = Times.get(app)

            for (acct in accounts) {
                val state = repo.getAccountState(acct.tag)
                val acctSettings = repo.getAccountSettings(acct.tag)
                val settings = AppSettings(
                    totalBuildersOverride = acctSettings.totalBuildersOverride,
                    boosts = acctSettings.boosts,
                    notificationsEnabled = globalSettings.notificationsEnabled,
                    liveCountdown = globalSettings.liveCountdown,
                    playerName = acctSettings.playerName,
                )
                try {
                    val r = FinishNotices.check(state, settings, names, now, ctAlert, idleAfter, noticeText)
                    val fresh = r.notices.filter { it.jobKey !in state.notified }
                    repo.updateAccountState(acct.tag) { st ->
                        st.copy(notified = r.notified)
                    }
                    if (fresh.isNotEmpty()) {
                        if (globalSettings.notificationsEnabled) {
                            val posted = Notifier.post(app, fresh, acct.displayName, acct.tag, accounts.size > 1)
                            repo.log(source, null, if (posted) "Notified (${acct.tag}): " + fresh.joinToString("; ") { it.title }
                                else "Not notified (notifications are blocked for the app): " + fresh.joinToString("; ") { it.title })
                        } else {
                            repo.log(source, null, "Finished (${acct.tag}, notifications off): " + fresh.joinToString("; ") { it.title })
                        }
                    }
                } catch (t: Throwable) {
                    repo.log(source, false, "Notification failed (${acct.tag}): ${t.javaClass.simpleName}: ${t.message}")
                }

                FinishNotices.alarmAt(state, settings, now, ctAlert, idleAfter)?.let { allFinishTimes.add(it) }

                if (liveLeadSec != null && state.export != null) {
                    val acctModel = WidgetModelBuilder.build(
                        state, settings, names, timesData, now, acct.tag,
                        globalSettings.effectiveWidgetTheme, widgetText, GoldPassStore.reduction(app, acct.tag),
                    )
                    fun collectLive(rows: List<WidgetRow>, catLabel: String, accentColor: Int) {
                        for (row in rows) {
                            val fin = row.finishAt ?: continue
                            if (row.kind != RowKind.RUNNING) continue
                            val jobKey = row.jobKey ?: row.label
                            val left = fin - now
                            if (left > Finish.GRACE_SEC && left <= liveLeadSec) {
                                if (!LiveUpdateAlert.isDismissed(app, acct.tag, jobKey, fin)) {
                                    dueLiveItems += Notifier.LiveCountdownItem(
                                        accountTag = acct.tag,
                                        accountName = acct.displayName,
                                        jobKey = jobKey,
                                        shortName = row.name,
                                        label = row.label,
                                        categoryTitle = catLabel,
                                        finishAt = fin,
                                        capturedLeftSec = left,
                                        progress = row.progress,
                                        iconIds = row.iconIds,
                                        iconLevel = row.iconLevel,
                                        accentColor = accentColor,
                                    )
                                }
                            } else if (fin - liveLeadSec > now) {
                                // Wake up right when this upgrade enters the Live Notification countdown window
                                allFinishTimes.add(fin - liveLeadSec)
                            }
                        }
                    }
                    val buildersCat = "${lctx.getString(R.string.filter_builders)} (${acctModel.buildersBusy}/${acctModel.buildersTotal})"
                    collectLive(acctModel.builderRows, buildersCat, Notifier.LIVE_PILL_ORANGE)
                    collectLive(acctModel.labRows, lctx.getString(R.string.filter_lab), Notifier.LIVE_PILL_ORANGE)
                    collectLive(listOf(acctModel.petRow), lctx.getString(R.string.filter_pets), Notifier.LIVE_PILL_ORANGE)
                    collectLive(acctModel.bbRows + listOfNotNull(acctModel.starLabRow), lctx.getString(R.string.filter_bb), Notifier.LIVE_PILL_ORANGE)
                }
            }

            runCatching { Notifier.syncLiveUpdates(app, dueLiveItems.sortedBy { it.finishAt }, accounts.size > 1) }

            WidgetAlarm.scheduleFinish(app, allFinishTimes.minOrNull()?.times(1000))

            // The Road to max widget only changes with the data, so it is redrawn here and needs no alarm.
            val forceRoad = source in setOf("config", "theme", "language", "app", "settings", "icons")
            runCatching { WidgetPreviews.update(app, globalSettings.effectiveWidgetTheme, force = forceRoad) }
            val roadCount = RoadWidgetUpdater.updateAll(app, force = forceRoad)

            val manager = AppWidgetManager.getInstance(app)
            val ids = widgetIds(app)
            if (ids.isEmpty()) {
                WidgetAlarm.cancelRedraw(app)
                return roadCount
            }

            val renderer = WidgetRenderer(app)
            val nextUpdates = mutableListOf<Long>()
            val fallbackTag = selectedTag ?: accounts.firstOrNull()?.tag

            for (id in ids) {
                val tag = repo.getWidgetAccount(id) ?: fallbackTag
                val state = if (tag != null) repo.getAccountState(tag) else repo.currentState()
                val acctSettings = if (tag != null) repo.getAccountSettings(tag) else AccountSettings()
                val settings = AppSettings(
                    totalBuildersOverride = acctSettings.totalBuildersOverride,
                    boosts = acctSettings.boosts,
                    notificationsEnabled = globalSettings.notificationsEnabled,
                    liveCountdown = globalSettings.liveCountdown,
                    playerName = acctSettings.playerName,
                )

                val theme = repo.getWidgetTheme(id)
                val model = WidgetModelBuilder.build(state, settings, names, Times.get(app), now, tag ?: "", theme, widgetText, GoldPassStore.reduction(app, tag))
                val style = repo.getWidgetStyle(id)
                try {
                    manager.updateAppWidget(id, renderer.render(model, settings.liveCountdown, manager.getAppWidgetOptions(id), style, id))
                } catch (e: RuntimeException) {
                    // Rows embedded in the widget (Android 17) were too big to send: use the list service instead.
                    if (!renderer.embedItems) throw e
                    renderer.embedItems = false
                    repo.log(source, false, "Embedded widget rows failed (${e.javaClass.simpleName}); using the list service")
                    manager.updateAppWidget(id, renderer.render(model, settings.liveCountdown, manager.getAppWidgetOptions(id), style, id))
                }
                manager.notifyAppWidgetViewDataChanged(id, com.snkb.chieftimer.R.id.list)

                WidgetSchedule.nextUpdateMs(model, nowMs, settings.liveCountdown)?.let { nextUpdates.add(it) }
            }
            WidgetAlarm.scheduleRedraw(app, nextUpdates.minOrNull())
            ids.size + roadCount
        } catch (t: Throwable) {
            repo.log(source, false, "Widget refresh failed: ${t.javaClass.simpleName}: ${t.message}")
            -1
        }
    }
}

object WidgetAlarm {
    private const val SLACK_MS = 250L
    private const val MIN_DELAY_MS = 1_000L

    private fun intent(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, WidgetInternalReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun scheduleRedraw(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ChiefWidgetReceiver.ACTION_TICK, 1)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            am.setExact(AlarmManager.RTC, at, pi)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        }
    }

    fun scheduleFinish(context: Context, atMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(context, ChiefWidgetReceiver.ACTION_FINISH, 2)
        if (atMs == null) return am.cancel(pi)
        val at = maxOf(atMs + SLACK_MS, System.currentTimeMillis() + MIN_DELAY_MS)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelRedraw(context: Context) = scheduleRedraw(context, null)
}
