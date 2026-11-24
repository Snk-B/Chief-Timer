package com.snkb.chieftimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.snkb.chieftimer.MainActivity
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.AccountSettings
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.WidgetModelBuilder
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.data.Times
import com.snkb.chieftimer.data.WidgetTexts
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.sync.GoldPassStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Multi-village compact overview home-screen widget showing all saved accounts at a glance. */
class OverviewWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                OverviewWidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

object OverviewWidgetUpdater {
    private const val MAX_ACCOUNTS = 6

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, OverviewWidgetReceiver::class.java))

    suspend fun updateAll(context: Context): Int {
        val app = context.applicationContext
        val ids = ids(app)
        if (ids.isEmpty()) return 0
        val repo = StateRepository(app)
        val manager = AppWidgetManager.getInstance(app)
        val accounts = repo.accounts.first()
        val global = repo.currentSettings()
        val theme = global.effectiveWidgetTheme
        val renderer = WidgetRenderer(app)
        val t = renderer.palette(theme)
        val ctx = LanguageManager.wrap(app)
        val icons = WidgetIcons.get(app)
        val names = Names.get(app)
        val times = Times.get(app)
        val widgetText = WidgetTexts.get(app)
        val now = System.currentTimeMillis() / 1000L
        val dayUnit = ctx.getString(R.string.time_day_short)
        val density = app.resources.displayMetrics.density
        val iconPx = (24 * density).toInt() * 2

        val rowViews = accounts.take(MAX_ACCOUNTS).map { acct ->
            val state = repo.getAccountState(acct.tag)
            val acctSet = repo.getAccountSettings(acct.tag)
            val settings = AppSettings(
                totalBuildersOverride = acctSet.totalBuildersOverride,
                boosts = acctSet.boosts,
                notificationsEnabled = global.notificationsEnabled,
                liveCountdown = global.liveCountdown,
                playerName = acctSet.playerName,
            )
            val model = WidgetModelBuilder.build(
                state, settings, names, times, now, acct.tag, theme, widgetText, GoldPassStore.reduction(app, acct.tag),
            )
            val r = RemoteViews(app.packageName, R.layout.widget_overview_row)
            r.setInt(R.id.row, "setBackgroundResource", t.rowSlot)
            val thStr = model.townHall?.let { ctx.getString(R.string.widget_th_tag, it) }
            r.setTextViewText(R.id.ov_name, listOfNotNull(acct.displayName, thStr).joinToString(" · "))
            r.setTextColor(R.id.ov_name, t.text)

            val thBmp = model.townHall?.let { icons.bitmapForLevel(1000001, it, iconPx) }
            if (thBmp != null) {
                r.setImageViewBitmap(R.id.ov_icon, thBmp)
            } else {
                r.setImageViewResource(R.id.ov_icon, R.drawable.ic_tavern_crest_hammer)
            }

            val labRunning = (listOf(model.labRow) + model.extraLabRows).filter { it.kind == RowKind.RUNNING }
            val labLabel = if (labRunning.isEmpty()) {
                ctx.getString(R.string.stat_free_now)
            } else {
                Countdown.text(labRunning.minOf { it.remaining }, dayUnit)
            }
            r.setTextViewText(R.id.ov_sub, "🔨 ${model.buildersBusy}/${model.buildersTotal} · 🧪 $labLabel")
            r.setTextColor(R.id.ov_sub, t.sub)

            val buildersFree = model.buildersBusy < model.buildersTotal
            val statusText = if (buildersFree) {
                ctx.getString(R.string.stat_free_now)
            } else {
                model.builderRows.filter { it.kind == RowKind.RUNNING && !it.goblin }
                    .minOfOrNull { it.remaining }?.let { Countdown.text(it, dayUnit) } ?: "—"
            }
            r.setTextViewText(R.id.ov_status, statusText)
            r.setTextColor(R.id.ov_status, if (buildersFree) 0xFF8CFF61.toInt() else t.gold)

            val openIntent = Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SELECT_TAG, acct.tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                app,
                ("ov_${acct.tag}").hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            r.setOnClickPendingIntent(R.id.row, pi)
            r
        }

        for (id in ids) {
            val rv = RemoteViews(app.packageName, R.layout.widget_overview)
            rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
            rv.setInt(R.id.header, "setBackgroundResource", t.headerBg)
            rv.setTextViewText(R.id.who, ctx.getString(R.string.overview_widget_title))
            rv.setTextColor(R.id.who, t.text)
            rv.setTextColor(R.id.empty, t.text)
            if (rowViews.isEmpty()) {
                rv.setViewVisibility(R.id.rows, View.GONE)
                rv.setViewVisibility(R.id.empty, View.VISIBLE)
                rv.setTextViewText(R.id.empty, ctx.getString(R.string.road_widget_empty))
            } else {
                rv.setViewVisibility(R.id.empty, View.GONE)
                rv.setViewVisibility(R.id.rows, View.VISIBLE)
                rv.removeAllViews(R.id.rows)
                rowViews.forEach { rv.addView(R.id.rows, it) }
            }
            val baseOpen = Intent(app, MainActivity::class.java)
            val basePi = PendingIntent.getActivity(app, 21, baseOpen, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.who, basePi)
            runCatching { manager.updateAppWidget(id, rv) }
        }
        return ids.size
    }
}
