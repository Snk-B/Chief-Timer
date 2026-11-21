package com.snkb.chieftimer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.snkb.chieftimer.R
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.data.WidgetTexts
import com.snkb.chieftimer.locale.LanguageManager
import java.util.Locale
import java.util.Objects

/**
 * Publishes themed and localized [RemoteViews] previews to the launcher widget picker on Android 15+
 * (API 35+ via [AppWidgetManager.setWidgetPreview]). Older Android versions fall back to static XML previews.
 */
object WidgetPreviews {
    private const val QUEEN_ID = 28000001
    private const val CANNON_ID = 1000008
    private const val LAB_ID = 1000007
    private const val SNEEZY_ID = 73000016

    @Volatile
    private var lastSignature: Int? = null

    internal fun previewSignature(
        theme: String,
        lang: String,
        panel: Int,
        gold: Int,
        sub: Int,
        isMaterial: Boolean,
    ): Int = Objects.hash(theme, lang, panel, gold, sub, isMaterial)

    internal fun formatDays(days: Int, dayUnit: String): String = "$days $dayUnit"

    internal fun formatDaysHours(days: Int, hours: Int, dayUnit: String, hourUnit: String): String =
        "${days}${dayUnit} ${hours}${hourUnit}"

    /** Reads the current active widget theme and updates picker previews if needed. */
    suspend fun sync(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val app = context.applicationContext
        val theme = StateRepository(app).currentSettings().effectiveWidgetTheme
        update(app, theme, force = force)
    }

    /** Builds and registers themed & localized picker previews on Android 15+ (no-op on older versions). */
    fun update(context: Context, theme: String, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val app = context.applicationContext
        val lang = LanguageManager.language(app)
        val lctx = LanguageManager.wrap(app)
        val pal = WidgetRenderer(app).palette(theme)
        val sig = previewSignature(theme, lang, pal.panel, pal.gold, pal.sub, pal.isMaterial)
        if (!force && lastSignature == sig) return

        val manager = AppWidgetManager.getInstance(app) ?: return
        val rootRv = buildRootPreview(app, lctx, pal)
        val roadRv = buildRoadPreview(app, lctx, pal)

        val rootOk = runCatching {
            manager.setWidgetPreview(
                ComponentName(app, ChiefWidgetReceiver::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                rootRv,
            )
        }.getOrDefault(false)

        val roadOk = runCatching {
            manager.setWidgetPreview(
                ComponentName(app, RoadWidgetReceiver::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                roadRv,
            )
        }.getOrDefault(false)

        if (rootOk && roadOk) {
            lastSignature = sig
        }
    }

    private fun buildRootPreview(
        app: Context,
        lctx: Context,
        pal: WidgetRenderer.Palette,
    ): RemoteViews {
        val wt = WidgetTexts.get(app)
        val names = Names.get(app)
        val locale = lctx.resources.configuration.locales[0] ?: Locale.getDefault()
        val pctColor = if (pal.isMaterial) pal.gold else 0xFFFFE89E.toInt()
        val tavernVis = if (pal.isMaterial) View.GONE else View.VISIBLE
        val matVis = if (pal.isMaterial) View.VISIBLE else View.GONE

        return RemoteViews(app.packageName, R.layout.widget_root_preview).apply {
            setInt(R.id.prev_panel, "setBackgroundResource", pal.panel)
            setInt(R.id.prev_header, "setBackgroundResource", pal.headerBg)
            setTextColor(R.id.prev_title, pal.text)
            setInt(R.id.prev_sync_btn, "setBackgroundResource", pal.syncButton)
            setTextViewText(R.id.prev_sync_txt, lctx.getString(R.string.widget_sync))
            setTextColor(R.id.prev_sync_txt, pal.syncText)

            setTextViewText(R.id.prev_sec_builders, wt.builders.uppercase(locale))
            setTextColor(R.id.prev_sec_builders, pal.gold)
            setTextViewText(R.id.prev_sec_lab, wt.laboratory.uppercase(locale))
            setTextColor(R.id.prev_sec_lab, pal.gold)
            setTextViewText(R.id.prev_sec_pets, wt.petHouse.uppercase(locale))
            setTextColor(R.id.prev_sec_pets, pal.gold)

            val pills = intArrayOf(R.id.prev_pill_bite, R.id.prev_pill_bpot, R.id.prev_pill_rpot, R.id.prev_pill_ppot)
            for (pillId in pills) {
                setInt(pillId, "setBackgroundResource", pal.pillOff)
            }
            setTextViewText(R.id.prev_bite_txt, wt.bite)
            setTextViewText(R.id.prev_bpot_txt, wt.plusOne)
            setTextViewText(R.id.prev_rpot_txt, wt.plusOne)
            setTextViewText(R.id.prev_ppot_txt, wt.plusOne)
            val pillTexts = intArrayOf(R.id.prev_bite_txt, R.id.prev_bpot_txt, R.id.prev_rpot_txt, R.id.prev_ppot_txt)
            for (txtId in pillTexts) {
                setTextColor(txtId, pal.sub)
            }

            setTextViewText(R.id.prev_name_1, "${names.name(QUEEN_ID)} → 63")
            setTextViewText(R.id.prev_time_1, "2:51:02")
            setTextViewText(R.id.prev_name_2, "${names.name(CANNON_ID)} → 21")
            setTextViewText(R.id.prev_time_2, formatDaysHours(1, 4, wt.day, wt.hour))
            setTextViewText(R.id.prev_name_3, "${names.name(LAB_ID)} → 16")
            setTextViewText(R.id.prev_time_3, formatDaysHours(3, 9, wt.day, wt.hour))
            setTextViewText(R.id.prev_name_4, "${names.name(SNEEZY_ID)} → 8")
            setTextViewText(R.id.prev_time_4, formatDaysHours(5, 16, wt.day, wt.hour))

            val rowSlots = intArrayOf(R.id.prev_row_1, R.id.prev_row_2, R.id.prev_row_3, R.id.prev_row_4)
            val iconSlots = intArrayOf(R.id.prev_icon_slot_1, R.id.prev_icon_slot_2, R.id.prev_icon_slot_3, R.id.prev_icon_slot_4)
            val nameIds = intArrayOf(R.id.prev_name_1, R.id.prev_name_2, R.id.prev_name_3, R.id.prev_name_4)
            val pctIds = intArrayOf(R.id.prev_pct_1, R.id.prev_pct_2, R.id.prev_pct_3, R.id.prev_pct_4)
            val timeIds = intArrayOf(R.id.prev_time_1, R.id.prev_time_2, R.id.prev_time_3, R.id.prev_time_4)
            val tavernBars = intArrayOf(R.id.prev_bar_tavern_1, R.id.prev_bar_tavern_2, R.id.prev_bar_tavern_3, R.id.prev_bar_tavern_4)
            val matBars = intArrayOf(R.id.prev_bar_mat_1, R.id.prev_bar_mat_2, R.id.prev_bar_mat_3, R.id.prev_bar_mat_4)

            for (i in 0..3) {
                setInt(rowSlots[i], "setBackgroundResource", pal.rowSlot)
                setInt(iconSlots[i], "setBackgroundResource", pal.iconSlot)
                setTextColor(nameIds[i], pal.text)
                setTextColor(pctIds[i], pctColor)
                setTextColor(timeIds[i], pal.text)
                setViewVisibility(tavernBars[i], tavernVis)
                setViewVisibility(matBars[i], matVis)
            }

            setTextViewText(R.id.prev_footer_left, wt.apprenticeIdle)
            setTextColor(R.id.prev_footer_left, pal.gold)
            setTextViewText(R.id.prev_footer_synced, wt.synced)
            setTextColor(R.id.prev_footer_synced, pal.sub)
            setTextViewText(R.id.prev_footer_ago, wt.justNow)
            setTextColor(R.id.prev_footer_ago, pal.sub)
        }
    }

    private fun buildRoadPreview(
        app: Context,
        lctx: Context,
        pal: WidgetRenderer.Palette,
    ): RemoteViews {
        val wt = WidgetTexts.get(app)
        return RemoteViews(app.packageName, R.layout.widget_road_preview).apply {
            setInt(R.id.prev_panel, "setBackgroundResource", pal.panel)
            setInt(R.id.prev_header, "setBackgroundResource", pal.headerBg)
            setTextViewText(R.id.prev_title, lctx.getString(R.string.road_widget_title))
            setTextColor(R.id.prev_title, pal.text)

            val labels = arrayOf(
                lctx.getString(R.string.road_widget_builders),
                lctx.getString(R.string.road_lab),
                lctx.getString(R.string.road_pets),
                lctx.getString(R.string.resource_gold),
                lctx.getString(R.string.resource_elixir),
                lctx.getString(R.string.resource_dark_elixir),
            )
            val values = arrayOf(
                formatDays(42, wt.day),
                formatDays(31, wt.day),
                formatDays(6, wt.day),
                "182M",
                "204M",
                "3.1M",
            )
            val rows = intArrayOf(R.id.prev_row_1, R.id.prev_row_2, R.id.prev_row_3, R.id.prev_row_4, R.id.prev_row_5, R.id.prev_row_6)
            val lblIds = intArrayOf(R.id.prev_lbl_1, R.id.prev_lbl_2, R.id.prev_lbl_3, R.id.prev_lbl_4, R.id.prev_lbl_5, R.id.prev_lbl_6)
            val valIds = intArrayOf(R.id.prev_val_1, R.id.prev_val_2, R.id.prev_val_3, R.id.prev_val_4, R.id.prev_val_5, R.id.prev_val_6)

            for (i in 0..5) {
                setInt(rows[i], "setBackgroundResource", pal.rowSlot)
                setTextViewText(lblIds[i], labels[i])
                setTextColor(lblIds[i], pal.sub)
                setTextViewText(valIds[i], values[i])
                setTextColor(valIds[i], pal.gold)
            }
        }
    }
}
