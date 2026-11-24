package com.snkb.chieftimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.core.content.res.ResourcesCompat
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.BoostPill
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.Finish
import com.snkb.chieftimer.core.FooterTone
import com.snkb.chieftimer.core.GameIds
import com.snkb.chieftimer.core.RowButton
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.WidgetActions
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.WidgetRow
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.sync.ClockTowerTracking
import com.snkb.chieftimer.sync.SyncActivity
import kotlin.math.roundToInt

/** Maps a [WidgetModel] onto Taverna Clash widget layouts (`widget_root_scroll.xml`, `widget_row.xml`, `widget_tile.xml`). */
class WidgetRenderer(private val context: Context) {

    private val pkg = context.packageName
    private val icons = WidgetIcons.get(context)
    private val density = context.resources.displayMetrics.density
    private val lctx get() = LanguageManager.wrap(context)
    private val lilitaTypeface: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.lilita_one) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    data class Palette(
        @param:DrawableRes val panel: Int,
        @param:DrawableRes val headerBg: Int,
        @param:DrawableRes val rowSlot: Int,
        @param:DrawableRes val rowDone: Int,
        @param:DrawableRes val pillOn: Int,
        @param:DrawableRes val pillOff: Int,
        @param:DrawableRes val iconSlot: Int,
        @param:DrawableRes val syncButton: Int,
        @param:DrawableRes val plusButton: Int,
        @param:DrawableRes val helperButton: Int,
        val text: Int,
        val sub: Int,
        val gold: Int,
        val goblin: Int,
        val idle: Int,
        val error: Int,
        val syncText: Int,
        val fillId: Int = R.id.row_fill,
        val isMaterial: Boolean = false,
        val pillTextOn: Int = 0xFF2B1703.toInt(),
        val ringRim: Int = 0xFF4D4339.toInt(),
        val ringBg: Int = 0xFF191512.toInt(),
        val ringPrimaryTop: Int = 0xFF74F043.toInt(),
        val ringPrimaryBot: Int = 0xFF259918.toInt(),
        val ringSecondaryTop: Int = 0xFFFA78FF.toInt(),
        val ringSecondaryBot: Int = 0xFF8E1DC4.toInt(),
        val ringTertiaryTop: Int = 0xFFFFE47A.toInt(),
        val ringTertiaryBot: Int = 0xFFC98314.toInt(),
    )

    private val ROW_FILLS = intArrayOf(
        R.id.row_fill,
        R.id.row_fill_clash,
        R.id.row_fill_dark,
        R.id.row_fill_mat_1,
        R.id.row_fill_mat_2,
        R.id.row_fill_mat_3,
    )

    private fun showFillForTone(v: RemoteViews, tone: Int, isMaterial: Boolean): Int {
        val targetId = if (isMaterial) {
            when (tone) {
                TONE_ELIXIR -> R.id.row_fill_mat_2
                TONE_GOLD -> R.id.row_fill_mat_3
                else -> R.id.row_fill_mat_1
            }
        } else {
            when (tone) {
                TONE_ELIXIR -> R.id.row_fill_clash // mapped to @drawable/row_fill_tavern_elixir
                TONE_GOLD -> R.id.row_fill_dark    // mapped to @drawable/row_fill_tavern_gold
                else -> R.id.row_fill              // mapped to @drawable/row_fill_tavern_green
            }
        }
        ROW_FILLS.forEach { v.setViewVisibility(it, if (it == targetId) View.VISIBLE else View.GONE) }
        return targetId
    }

    /** Taverna Clash, Taverna Escura, Material You (3D Dynamic), or Material AMOLED (#000000 3D Dynamic). */
    internal fun palette(theme: String): Palette {
        if (theme == "material_you" || theme == "black") {
            val isAmoled = theme == "black"
            val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val accentGold = if (api31) context.getColor(android.R.color.system_accent1_200)
            else if (isAmoled) 0xFFD2ADFF.toInt() else 0xFFA8D8FF.toInt()
            val accentGoblin = if (api31) context.getColor(android.R.color.system_accent2_100)
            else 0xFF9AF0E4.toInt()
            val accentSub = if (api31) context.getColor(android.R.color.system_neutral1_200)
            else 0xFFD6DFEE.toInt()
            val accentIdle = if (api31) context.getColor(android.R.color.system_accent3_200)
            else 0xFFC7BFFF.toInt()
            val pillTextOn = if (api31) context.getColor(android.R.color.system_accent1_900)
            else 0xFF0A1D36.toInt()
            val ringRim = if (isAmoled) {
                0xFF282E3B.toInt()
            } else if (api31) {
                context.getColor(android.R.color.system_neutral2_600)
            } else {
                0xFF3E5278.toInt()
            }
            val ringBg = if (isAmoled) 0xFF020305.toInt() else 0xFF0E1420.toInt()
            val pTop = if (api31) context.getColor(android.R.color.system_accent1_200) else 0xFF85C8FF.toInt()
            val pBot = if (api31) context.getColor(android.R.color.system_accent1_500) else 0xFF1F7ED9.toInt()
            val sTop = if (api31) context.getColor(android.R.color.system_accent3_200) else 0xFFC2B8FF.toInt()
            val sBot = if (api31) context.getColor(android.R.color.system_accent3_500) else 0xFF6B54E0.toInt()
            val tTop = if (api31) context.getColor(android.R.color.system_accent2_200) else 0xFF92E3D8.toInt()
            val tBot = if (api31) context.getColor(android.R.color.system_accent2_500) else 0xFF238F82.toInt()
            return Palette(
                panel = if (isAmoled) R.drawable.widget_panel_material_amoled else R.drawable.widget_panel_material_you,
                headerBg = if (isAmoled) R.drawable.widget_header_material_amoled else R.drawable.widget_header_material_you,
                rowSlot = if (isAmoled) R.drawable.row_slot_material_amoled else R.drawable.row_slot_material_you,
                rowDone = if (isAmoled) R.drawable.row_done_material_amoled else R.drawable.row_done_material_you,
                pillOn = R.drawable.pill_on_material_you,
                pillOff = if (isAmoled) R.drawable.pill_off_material_amoled else R.drawable.pill_off_material_you,
                iconSlot = if (isAmoled) R.drawable.icon_slot_material_amoled else R.drawable.icon_slot_material_you,
                syncButton = R.drawable.btn_sync_material_you,
                plusButton = R.drawable.btn_plus_material_you,
                helperButton = R.drawable.btn_helper_material_you,
                text = 0xFFFFFFFF.toInt(),
                sub = accentSub,
                gold = accentGold,
                goblin = accentGoblin,
                idle = accentIdle,
                error = 0xFFFF7A7A.toInt(),
                syncText = 0xFFFFFFFF.toInt(),
                isMaterial = true,
                pillTextOn = pillTextOn,
                ringRim = ringRim,
                ringBg = ringBg,
                ringPrimaryTop = pTop,
                ringPrimaryBot = pBot,
                ringSecondaryTop = sTop,
                ringSecondaryBot = sBot,
                ringTertiaryTop = tTop,
                ringTertiaryBot = tBot,
            )
        }
        val isDark = theme == "dark"
        return Palette(
            panel = if (isDark) R.drawable.widget_panel_tavern_dark_9p else R.drawable.widget_panel_tavern_9p,
            headerBg = if (isDark) R.drawable.widget_header_tavern_dark else R.drawable.widget_header_tavern,
            rowSlot = if (isDark) R.drawable.row_slot_tavern_dark else R.drawable.row_slot_tavern,
            rowDone = if (isDark) R.drawable.row_done_tavern_dark else R.drawable.row_done_tavern,
            pillOn = R.drawable.pill_on_tavern,
            pillOff = if (isDark) R.drawable.pill_off_tavern_dark else R.drawable.pill_off_tavern,
            iconSlot = R.drawable.icon_slot_tavern,
            syncButton = R.drawable.btn_sync_tavern,
            plusButton = R.drawable.btn_plus_tavern,
            helperButton = R.drawable.btn_helper_tavern,
            text = 0xFFFFFFFF.toInt(),
            sub = if (isDark) 0xFFCFD6E4.toInt() else 0xFFE6D8C3.toInt(),
            gold = 0xFFFFD863.toInt(),
            goblin = 0xFF72EE42.toInt(),
            idle = 0xFFFFB85C.toInt(),
            error = 0xFFFF7A7A.toInt(),
            syncText = 0xFFFFFFFF.toInt(),
        )
    }

    var embedItems: Boolean = true
    private var itemStyle: String? = null

    private fun attachList(rv: RemoteViews, appWidgetId: Int, small: Boolean, cols: Int, model: WidgetModel, live: Boolean) {
        if (embedItems && Build.VERSION.SDK_INT >= 36) {
            val items = runCatching {
                val factory = WidgetListService.Factory(context, appWidgetId, small, cols, WidgetListService.Preset(model, live, itemStyle))
                factory.onCreate()
                factory.onDataSetChanged()
                val b = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(8).setHasStableIds(false)
                for (i in 0 until factory.getCount()) b.addItem(i.toLong(), factory.getViewAt(i))
                b.build()
            }.getOrNull()
            if (items != null) {
                rv.setRemoteAdapter(R.id.list, items)
                rv.setPendingIntentTemplate(R.id.list, tapTemplate())
                return
            }
        }
        val adapter = Intent(context, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(WidgetListService.EXTRA_COLS, cols)
        if (small) adapter.putExtra(WidgetListService.EXTRA_SMALL, true)
        adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
        rv.setRemoteAdapter(R.id.list, adapter)
        rv.setPendingIntentTemplate(R.id.list, tapTemplate())
    }

    fun render(
        model: WidgetModel,
        liveCountdown: Boolean,
        options: Bundle?,
        style: String = "list",
        appWidgetId: Int = 0,
        multipleAccounts: Boolean = false,
    ): RemoteViews {
        val resolvedStyle = if (style == com.snkb.chieftimer.data.StateRepository.WIDGET_STYLE_TILES) {
            com.snkb.chieftimer.data.StateRepository.WIDGET_STYLE_TILES
        } else {
            com.snkb.chieftimer.data.StateRepository.WIDGET_STYLE_LIST
        }
        itemStyle = resolvedStyle
        val heightDp = reportedHeightDp(options) ?: 320
        val isSmall = heightDp < 185
        if (resolvedStyle == com.snkb.chieftimer.data.StateRepository.WIDGET_STYLE_TILES) {
            return renderLarge(model, liveCountdown, options, appWidgetId, multipleAccounts)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val smallSize = android.util.SizeF(250f, 130f)
            val largeSize = android.util.SizeF(250f, 320f)
            val smallRv = renderSmall(model, liveCountdown, options, appWidgetId, multipleAccounts)
            val largeRv = renderLarge(model, liveCountdown, options, appWidgetId, multipleAccounts)
            RemoteViews(mapOf(smallSize to smallRv, largeSize to largeRv))
        } else {
            if (isSmall) renderSmall(model, liveCountdown, options, appWidgetId, multipleAccounts)
            else renderLarge(model, liveCountdown, options, appWidgetId, multipleAccounts)
        }
    }

    private fun bindHeaderTownHall(rv: RemoteViews, townHall: Int?, sizeDp: Int) {
        val thBmp = icons.bitmapForLevel(1000001, townHall, (sizeDp * density).roundToInt())
        if (thBmp != null) {
            rv.setImageViewBitmap(R.id.who_th_icon, thBmp)
            rv.setViewVisibility(R.id.who_th_icon, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.who_th_icon, View.GONE)
        }
    }

    private fun renderLarge(
        model: WidgetModel,
        liveCountdown: Boolean,
        options: Bundle?,
        appWidgetId: Int,
        multipleAccounts: Boolean = false,
    ): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root_scroll)
        val sync = syncIntent()
        val t = palette(model.widgetTheme)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setInt(R.id.header, "setBackgroundResource", t.headerBg)
        rv.setInt(R.id.sync_button, "setBackgroundResource", t.syncButton)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        bindHeaderTownHall(rv, model.townHall, 30)
        rv.setTextViewText(R.id.who, title(model, t, multipleAccounts))
        rv.setTextColor(R.id.who, t.text)
        if (multipleAccounts && appWidgetId > 0) {
            val sw = switchAccountIntent(appWidgetId)
            rv.setOnClickPendingIntent(R.id.who, sw)
            rv.setOnClickPendingIntent(R.id.who_th_icon, sw)
        }
        rv.setTextViewText(R.id.lbl_sync, lctx.getString(R.string.widget_sync))
        rv.setTextColor(R.id.lbl_sync, t.syncText)

        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) model.text.syncFailed else model.text.noData)
            rv.setTextColor(R.id.empty_text, t.text)
            rv.setTextViewText(R.id.lbl_no_data_hint, lctx.getString(R.string.widget_no_data_hint))
            rv.setTextColor(R.id.lbl_no_data_hint, t.sub)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)

        attachList(rv, appWidgetId, small = false, cols = tileColumns(options), model = model, live = liveCountdown)

        rv.setTextViewText(R.id.foot_left, footer(model, t))
        rv.setTextColor(R.id.foot_left, t.sub)
        rv.setTextViewText(R.id.foot_right, model.footerRight)
        rv.setTextColor(R.id.foot_right, t.sub)
        return rv
    }

    fun minHeaderView(h: ListItem.Header, model: WidgetModel): RemoteViews = headerView(h, model, palette(model.widgetTheme))

    fun minRowView(source: WidgetRow, model: WidgetModel, live: Boolean): RemoteViews =
        timerView(source, model, live, palette(model.widgetTheme), compact = false, tone = TONE_GREEN)

    private fun renderSmall(
        model: WidgetModel,
        liveCountdown: Boolean,
        options: Bundle?,
        appWidgetId: Int,
        multipleAccounts: Boolean = false,
    ): RemoteViews {
        val rv = RemoteViews(pkg, R.layout.widget_root_small)
        val sync = syncIntent()
        val t = palette(model.widgetTheme)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setInt(R.id.header, "setBackgroundResource", t.headerBg)
        rv.setInt(R.id.sync_button, "setBackgroundResource", t.syncButton)
        rv.setOnClickPendingIntent(R.id.sync_button, sync)
        bindHeaderTownHall(rv, model.townHall, 22)
        rv.setTextViewText(R.id.who, title(model, t, multipleAccounts))
        rv.setTextColor(R.id.who, t.text)
        if (multipleAccounts && appWidgetId > 0) {
            val sw = switchAccountIntent(appWidgetId)
            rv.setOnClickPendingIntent(R.id.who, sw)
            rv.setOnClickPendingIntent(R.id.who_th_icon, sw)
        }

        if (!model.hasData) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, sync)
            rv.setTextViewText(R.id.empty_text, if (model.syncFailed) model.text.syncFailed else model.text.noData)
            rv.setTextColor(R.id.empty_text, t.text)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)

        attachList(rv, appWidgetId, small = true, cols = TILES_PER_ROW, model = model, live = liveCountdown)
        return rv
    }

    /**
     * Renders the 3D Glowing Circular Progress Ring + Official Building/Troop/Pet Icon + Stroked % Label
     * onto a sharp anti-aliased Bitmap for Modo Grid (`widget_tile.xml`).
     */
    private fun drawTavernTileRingBitmap(row: WidgetRow, tone: Int, t: Palette): Bitmap {
        val sz = (48 * density).roundToInt().coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sz * 0.105f
        val pad = stroke * 0.72f
        val arcRect = RectF(pad, pad, sz - pad, sz - pad)

        // 1. Dark recessed circular channel + subtle beveled rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke + 2f * density
            color = t.ringRim
            strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(arcRect, 125f, 290f, false, rimPaint)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = t.ringBg
            strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(arcRect, 125f, 290f, false, trackPaint)

        // 2. 3D Glowing Colored Progress Arc
        val frac = if (row.kind == RowKind.COMPLETED) 1f else row.progress.coerceIn(0f, 1f)
        val sweep = 290f * maxOf(0.04f, frac)
        val (topCol, botCol) = when (tone) {
            TONE_ELIXIR -> t.ringSecondaryTop to t.ringSecondaryBot
            TONE_GOLD -> t.ringTertiaryTop to t.ringTertiaryBot
            else -> t.ringPrimaryTop to t.ringPrimaryBot
        }
        val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(0f, 0f, 0f, sz.toFloat(), topCol, botCol, Shader.TileMode.CLAMP)
        }
        c.drawArc(arcRect, 125f, sweep, false, progPaint)

        // Specular highlight along progress arc
        val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (stroke * 0.28f).coerceAtLeast(1.5f)
            strokeCap = Paint.Cap.ROUND
            color = 0x55FFFFFF
        }
        c.drawArc(arcRect, 125f, sweep, false, hiPaint)

        // 3. Official 3D Game Icon centered inside the ring
        val iconSz = (sz * 0.58f).roundToInt().coerceAtLeast(24)
        val iconBmp = row.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, row.iconLevel, iconSz) }
        if (iconBmp != null) {
            val ix = (sz - iconBmp.width) / 2f
            val iy = (sz - iconBmp.height) / 2f - sz * 0.03f
            c.drawBitmap(iconBmp, ix, iy, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = lilitaTypeface
                textSize = sz * 0.24f
                textAlign = Paint.Align.CENTER
                color = t.gold
            }
            c.drawText(row.initials, sz / 2f, sz * 0.54f, initPaint)
        }

        // 4. High-Contrast Pill Badge + Outlined Percentage Label at bottom of ring (e.g. "90%")
        val pctText = "${(frac * 100).roundToInt()}%"
        val textSz = sz * 0.22f
        val fillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = lilitaTypeface
            textSize = textSz
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            color = if (t.isMaterial) t.gold else 0xFFFFEA9F.toInt()
        }
        val tw = fillTextPaint.measureText(pctText)
        val pillH = textSz * 1.24f
        val pillW = (tw + sz * 0.18f).coerceAtLeast(sz * 0.44f)
        val pillTop = sz - pillH - 1f * density
        val pillRect = RectF((sz - pillW) / 2f, pillTop, (sz + pillW) / 2f, sz - 1f * density)
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xEB0C0E12.toInt()
        }
        val pillRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * density
            color = (t.gold and 0x00FFFFFF) or 0xB3000000.toInt()
        }
        c.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
        c.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillRimPaint)

        val py = pillRect.centerY() - (fillTextPaint.descent() + fillTextPaint.ascent()) / 2f
        val strokeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = lilitaTypeface
            textSize = textSz
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * density
            strokeJoin = Paint.Join.ROUND
            color = 0xFF0A0602.toInt()
        }
        c.drawText(pctText, sz / 2f, py, strokeTextPaint)
        c.drawText(pctText, sz / 2f, py, fillTextPaint)

        return bmp
    }

    private fun tile(model: WidgetRow, live: Boolean, nowMs: Long, elapsed: Long, accountTag: String, t: Palette, tone: Int): RemoteViews {
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v = RemoteViews(pkg, R.layout.widget_tile)
        v.setInt(R.id.tile, "setBackgroundResource", if (row.kind == RowKind.COMPLETED) t.rowDone else t.rowSlot)
        v.setViewVisibility(R.id.tile_ring, View.GONE)
        v.setViewVisibility(R.id.tile_initials, View.GONE)
        v.setViewVisibility(R.id.tile_goblin, View.GONE)
        v.setViewVisibility(R.id.tile_btn, View.GONE)
        v.setViewVisibility(R.id.tile_btn_icon, View.GONE)
        v.setViewVisibility(R.id.tile_btn_text, View.VISIBLE)
        v.setTextViewText(R.id.tile_btn_text, "+")
        v.setTextColor(R.id.tile_btn_text, 0xFFFFFFFF.toInt())

        val ringBmp = drawTavernTileRingBitmap(row, tone, t)
        v.setImageViewBitmap(R.id.tile_icon, ringBmp)
        v.setViewVisibility(R.id.tile_icon, View.VISIBLE)

        if (row.goblin) {
            icons.bitmap("goblin", (15 * density).roundToInt())?.let {
                v.setImageViewBitmap(R.id.tile_goblin, it)
                v.setViewVisibility(R.id.tile_goblin, View.VISIBLE)
            }
        }
        if (row.button != RowButton.NONE && row.jobKey != null) {
            v.setViewVisibility(R.id.tile_btn, View.VISIBLE)
            tap(v, R.id.tile_btn, WidgetActions.HELPER, row.jobKey, accountTag, true)
            when (row.button) {
                RowButton.LINK -> v.setInt(R.id.tile_btn, "setBackgroundResource", t.plusButton)
                else -> {
                    v.setInt(R.id.tile_btn, "setBackgroundResource", t.helperButton)
                    val name = if (row.button == RowButton.APPRENTICE) "apprentice" else "lab_assistant"
                    val badge = icons.bitmap(name, (15 * density).roundToInt())
                    if (badge != null) {
                        v.setImageViewBitmap(R.id.tile_btn_icon, badge)
                        v.setViewVisibility(R.id.tile_btn_icon, View.VISIBLE)
                        v.setViewVisibility(R.id.tile_btn_text, View.GONE)
                    } else {
                        v.setTextViewText(R.id.tile_btn_text, "H")
                        v.setTextColor(R.id.tile_btn_text, t.gold)
                        v.setTextViewTextSize(R.id.tile_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 9.5f)
                    }
                }
            }
        }
        v.setTextViewText(R.id.tile_name, row.name)
        v.setTextColor(R.id.tile_name, t.text)
        val level = row.levelText?.takeIf { row.kind == RowKind.RUNNING }
        if (level != null) {
            v.setViewVisibility(R.id.tile_level, View.VISIBLE)
            v.setTextViewText(R.id.tile_level, (if (row.showArrow) "\u2192 " else "") + level)
            v.setTextColor(R.id.tile_level, t.gold)
        } else {
            v.setViewVisibility(R.id.tile_level, View.GONE)
        }
        v.setTextColor(R.id.tile_chrono, t.gold)
        v.setTextColor(R.id.tile_text, t.gold)

        val segment = if (row.kind == RowKind.RUNNING && live) row.finishAt?.let { Countdown.segment(it, nowMs, row.text.day) } else null
        if (segment != null) {
            v.setViewVisibility(R.id.tile_chrono, View.VISIBLE)
            v.setViewVisibility(R.id.tile_text, View.GONE)
            v.setChronometer(R.id.tile_chrono, elapsed + segment.chronometerMs, segment.format, true)
            v.setChronometerCountDown(R.id.tile_chrono, true)
        } else {
            v.setViewVisibility(R.id.tile_chrono, View.GONE)
            v.setViewVisibility(R.id.tile_text, View.VISIBLE)
            v.setTextViewText(R.id.tile_text, row.staticStatus)
        }
        return v
    }

    private fun row(
        model: WidgetRow,
        compact: Boolean,
        live: Boolean,
        nowMs: Long,
        elapsed: Long,
        accountTag: String,
        t: Palette,
        inList: Boolean = false,
        tone: Int = TONE_GREEN,
    ): RemoteViews {
        val row = if (model.kind == RowKind.RUNNING && model.finishAt != null && Finish.isDone(model.finishAt, nowMs / 1000)) {
            model.copy(kind = RowKind.COMPLETED, button = RowButton.NONE, remaining = 0, progress = 1f)
        } else {
            model
        }
        val v = RemoteViews(pkg, if (compact) R.layout.widget_row_compact else R.layout.widget_row)
        val fillId = showFillForTone(v, tone, t.isMaterial)
        val iconPx = ((if (compact) 23 else 27) * density).roundToInt()
        if (inList) {
            v.setViewVisibility(R.id.row_icon, View.GONE)
            v.setViewVisibility(R.id.row_initials, View.VISIBLE)
            v.setViewVisibility(R.id.row_goblin, View.GONE)
            v.setViewVisibility(R.id.row_goblin_icon, View.GONE)
            v.setViewVisibility(R.id.row_level, View.VISIBLE)
            v.setViewVisibility(R.id.row_bar_wrap, View.VISIBLE)
            v.setViewVisibility(R.id.row_pct, View.VISIBLE)
            v.setViewVisibility(R.id.row_clock, View.GONE)
            v.setViewVisibility(R.id.row_chrono, View.VISIBLE)
            v.setViewVisibility(R.id.row_text, View.GONE)
            v.setViewVisibility(R.id.row_check, View.GONE)
            v.setViewVisibility(R.id.row_btn, View.VISIBLE)
            v.setViewVisibility(R.id.row_btn_icon, View.GONE)
            v.setViewVisibility(R.id.row_btn_text, View.VISIBLE)
            v.setTextViewText(R.id.row_btn_text, "+")
            v.setTextColor(R.id.row_btn_text, 0xFFFFFFFF.toInt())
            v.setTextViewTextSize(R.id.row_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, if (compact) 13f else 15f)
            v.setInt(R.id.row_btn, "setBackgroundResource", t.plusButton)
        }
        v.setInt(R.id.row, "setBackgroundResource", if (row.kind == RowKind.RUNNING) t.rowSlot else t.rowDone)
        v.setInt(R.id.row_icon_slot, "setBackgroundResource", t.iconSlot)

        val icon = row.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, row.iconLevel, iconPx) }
        if (icon != null) {
            v.setImageViewBitmap(R.id.row_icon, icon)
            v.setViewVisibility(R.id.row_icon, View.VISIBLE)
            v.setViewVisibility(R.id.row_initials, View.GONE)
        } else {
            v.setTextViewText(R.id.row_initials, row.initials)
            v.setTextColor(R.id.row_initials, t.gold)
        }

        if (row.goblin) {
            v.setViewVisibility(R.id.row_goblin, View.VISIBLE)
            icons.bitmap("goblin", (10 * density).roundToInt())?.let {
                v.setImageViewBitmap(R.id.row_goblin_icon, it)
                v.setViewVisibility(R.id.row_goblin_icon, View.VISIBLE)
            }
        }
        v.setViewVisibility(R.id.row_arrow, View.GONE)
        v.setViewVisibility(R.id.row_level, View.GONE)
        val fullTitle: CharSequence = if (row.levelText != null) {
            SpannableStringBuilder(row.name).apply {
                val start = length
                append(if (row.showArrow) "  \u2192 ${row.levelText}" else " ${row.levelText}")
                setSpan(ForegroundColorSpan(t.gold), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.90f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            row.name
        }
        v.setTextViewText(R.id.row_name, fullTitle)
        v.setTextColor(R.id.row_name, t.text)
        v.setTextColor(R.id.row_pct, if (t.isMaterial) t.gold else 0xFFFFE89E.toInt())
        v.setTextColor(R.id.row_chrono, t.text)
        v.setTextColor(R.id.row_text, t.gold)

        when (row.kind) {
            RowKind.RUNNING -> {
                val pctInt = (row.progress.coerceIn(0f, 1f) * 100).roundToInt()
                val barProg = (row.progress.coerceIn(0.04f, 1f) * 1000).roundToInt()
                v.setViewVisibility(R.id.row_bar_wrap, View.VISIBLE)
                v.setProgressBar(fillId, 1000, barProg, false)
                v.setTextViewText(R.id.row_pct, "$pctInt%")
                val segment = if (live) row.finishAt?.let { Countdown.segment(it, nowMs, model.text.day) } else null
                if (segment != null) {
                    v.setChronometer(R.id.row_chrono, elapsed + segment.chronometerMs, segment.format, true)
                    v.setChronometerCountDown(R.id.row_chrono, true)
                    v.setViewVisibility(R.id.row_chrono, View.VISIBLE)
                    v.setViewVisibility(R.id.row_text, View.GONE)
                } else {
                    v.setViewVisibility(R.id.row_chrono, View.GONE)
                    v.setViewVisibility(R.id.row_text, View.VISIBLE)
                    v.setTextViewText(R.id.row_text, row.staticStatus)
                }
            }
            RowKind.COMPLETED, RowKind.IDLE -> {
                ROW_FILLS.forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(R.id.row_bar_wrap, View.GONE)
                v.setViewVisibility(R.id.row_pct, View.GONE)
                v.setViewVisibility(R.id.row_clock, View.GONE)
                v.setViewVisibility(R.id.row_chrono, View.GONE)
                v.setViewVisibility(R.id.row_text, View.VISIBLE)
                v.setTextViewText(R.id.row_text, row.staticStatus)
                if (row.kind == RowKind.COMPLETED) v.setViewVisibility(R.id.row_check, View.VISIBLE)
            }
        }

        if (row.button != RowButton.NONE && row.jobKey != null) {
            tap(v, R.id.row_btn, WidgetActions.HELPER, row.jobKey, accountTag, inList)
        }
        when (row.button) {
            RowButton.NONE -> v.setViewVisibility(R.id.row_btn, View.INVISIBLE)
            RowButton.LINK -> v.setInt(R.id.row_btn, "setBackgroundResource", t.plusButton)
            RowButton.APPRENTICE, RowButton.LAB_ASSISTANT -> {
                v.setInt(R.id.row_btn, "setBackgroundResource", t.helperButton)
                val name = if (row.button == RowButton.APPRENTICE) "apprentice" else "lab_assistant"
                val badge = icons.bitmap(name, ((if (compact) 18 else 21) * density).roundToInt())
                if (badge != null) {
                    v.setImageViewBitmap(R.id.row_btn_icon, badge)
                    v.setViewVisibility(R.id.row_btn_icon, View.VISIBLE)
                    v.setViewVisibility(R.id.row_btn_text, View.GONE)
                } else {
                    v.setTextViewText(R.id.row_btn_text, "H")
                    v.setTextColor(R.id.row_btn_text, t.gold)
                    v.setTextViewTextSize(R.id.row_btn_text, android.util.TypedValue.COMPLEX_UNIT_DIP, 10f)
                }
            }
        }
        return v
    }

    private class PillIds(
        @param:IdRes val box: Int,
        @param:IdRes val add: Int,
        @param:IdRes val undo: Int,
        @param:IdRes val icon: Int,
        @param:IdRes val text: Int,
    )

    private fun pill(rv: RemoteViews, ids: PillIds, pill: BoostPill, asset: String, @DrawableRes fallback: Int, accountTag: String, t: Palette, inList: Boolean = false) {
        val bmp = icons.bitmap(asset, (14 * density).roundToInt())
        if (bmp != null) rv.setImageViewBitmap(ids.icon, bmp) else rv.setImageViewResource(ids.icon, fallback)
        rv.setTextViewText(ids.text, pill.text)
        rv.setTextColor(ids.text, if (pill.active) t.pillTextOn else t.text)
        rv.setInt(ids.box, "setBackgroundResource", if (pill.active || pill.canUndo) t.pillOn else t.pillOff)
        tap(rv, ids.add, WidgetActions.ADD_BOOST, pill.type.name, accountTag, inList)
        rv.setViewVisibility(ids.undo, if (pill.canUndo) View.VISIBLE else View.GONE)
        if (pill.canUndo) tap(rv, ids.undo, WidgetActions.UNDO_BOOST, pill.type.name, accountTag, inList)
    }

    private fun tapUri(action: String, arg: String, accountTag: String): Uri =
        Uri.Builder().scheme("chieftimer").authority("tap").appendPath(action).appendPath(arg)
            .appendQueryParameter("account", accountTag).build()

    private fun tapIntent(action: String, arg: String, accountTag: String): PendingIntent {
        val intent = Intent(context, WidgetInternalReceiver::class.java)
            .setAction(ChiefWidgetReceiver.ACTION_TAP)
            .setData(tapUri(action, arg, accountTag))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun tapTemplate(): PendingIntent {
        val intent = Intent(context, WidgetInternalReceiver::class.java).setAction(ChiefWidgetReceiver.ACTION_TAP)
        return PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun tap(v: RemoteViews, @IdRes id: Int, action: String, arg: String, accountTag: String, inList: Boolean) {
        if (inList) v.setOnClickFillInIntent(id, Intent().setData(tapUri(action, arg, accountTag)))
        else v.setOnClickPendingIntent(id, tapIntent(action, arg, accountTag))
    }

    sealed class ListItem {
        class Header(val title: CharSequence, val pills: List<Int>) : ListItem()
        class Timer(val row: WidgetRow, val tone: Int = TONE_GREEN) : ListItem()
        class Tiles(val rows: List<WidgetRow>, val tone: Int = TONE_GREEN) : ListItem()
    }

    fun listItems(model: WidgetModel, t: Palette): List<ListItem> {
        val items = mutableListOf<ListItem>()
        fun section(title: CharSequence, pills: List<Int>, rows: List<WidgetRow>, tone: Int) {
            items += ListItem.Header(title, pills)
            rows.take(MAX_BUILDER_ROWS).forEach { items += ListItem.Timer(it, tone) }
        }
        section(buildersLabel(model, t), listOf(PILL_BITE, PILL_BUILDER), model.builderRows, TONE_GREEN)
        section(lctx.getString(R.string.widget_lab), listOf(PILL_SOUP, PILL_RESEARCH), model.labRows, TONE_ELIXIR)
        section(lctx.getString(R.string.widget_pets), listOf(PILL_PET), listOf(model.petRow), TONE_GOLD)
        val ctPill = if (model.clockTower != null && ClockTowerTracking.enabled(context)) listOf(PILL_CLOCKTOWER) else emptyList()
        if (model.showBuilderBase || ctPill.isNotEmpty()) {
            section(lctx.getString(R.string.widget_bb), ctPill, model.bbRows + listOfNotNull(model.starLabRow), TONE_GREEN)
        }
        return items
    }

    fun tileItems(model: WidgetModel, t: Palette, cols: Int = TILES_PER_ROW): List<ListItem> {
        val items = mutableListOf<ListItem>()
        fun section(title: CharSequence, pills: List<Int>, rows: List<WidgetRow>, tone: Int) {
            items += ListItem.Header(title, pills)
            rows.filter { it.kind != RowKind.IDLE }.take(MAX_TILES).chunked(cols).forEach { items += ListItem.Tiles(it, tone) }
        }
        section(buildersLabel(model, t), listOf(PILL_BITE, PILL_BUILDER), model.builderRows, TONE_GREEN)
        section(lctx.getString(R.string.widget_lab), listOf(PILL_SOUP, PILL_RESEARCH), model.labRows, TONE_ELIXIR)
        section(lctx.getString(R.string.widget_pets), listOf(PILL_PET), listOf(model.petRow), TONE_GOLD)
        val ctPill = if (model.clockTower != null && ClockTowerTracking.enabled(context)) listOf(PILL_CLOCKTOWER) else emptyList()
        if (model.showBuilderBase || ctPill.isNotEmpty()) {
            section(lctx.getString(R.string.widget_bb), ctPill, model.bbRows + listOfNotNull(model.starLabRow), TONE_GOLD)
        }
        return items
    }

    fun tilesView(rows: List<WidgetRow>, model: WidgetModel, live: Boolean, t: Palette, cols: Int = TILES_PER_ROW, tone: Int = TONE_GREEN): RemoteViews {
        val line = RemoteViews(pkg, R.layout.widget_tile_row)
        line.removeAllViews(R.id.tile_row)
        val nowMs = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        rows.forEach { line.addView(R.id.tile_row, tile(it, live, nowMs, elapsed, model.accountTag, t, tone)) }
        repeat((cols - rows.size).coerceAtLeast(0)) {
            line.addView(R.id.tile_row, RemoteViews(pkg, R.layout.widget_tile).also { f -> f.setViewVisibility(R.id.tile, View.INVISIBLE) })
        }
        return line
    }

    fun headerView(h: ListItem.Header, model: WidgetModel, t: Palette): RemoteViews {
        val v = RemoteViews(pkg, R.layout.widget_list_header)
        v.setTextViewText(R.id.hdr_label, h.title)
        v.setTextColor(R.id.hdr_label, t.gold)
        for (slot in h.pills) when (slot) {
            PILL_CLOCKTOWER -> model.clockTower?.let { ct ->
                val bmp = icons.bitmapForLevel(GameIds.CLOCK_TOWER, ct.level, (14 * density).roundToInt())
                if (bmp != null) v.setImageViewBitmap(R.id.pill_ct_icon, bmp) else v.setImageViewResource(R.id.pill_ct_icon, R.drawable.ic_clock_tower)
                v.setTextViewText(R.id.pill_ct_text, ct.text)
                v.setTextColor(R.id.pill_ct_text, if (ct.highlighted) t.pillTextOn else t.text)
                v.setInt(R.id.pill_ct, "setBackgroundResource", if (ct.highlighted) t.pillOn else t.pillOff)
            }
            PILL_BITE -> pill(v, PillIds(R.id.pill_bite, R.id.pill_bite_add, R.id.pill_bite_undo, R.id.pill_bite_icon, R.id.pill_bite_text),
                model.bite, "builder_bite", R.drawable.ic_builder_bite, model.accountTag, t, true)
            PILL_BUILDER -> pill(v, PillIds(R.id.pill_bpot, R.id.pill_bpot_add, R.id.pill_bpot_undo, R.id.pill_bpot_icon, R.id.pill_bpot_text),
                model.builderPotion, "builder_potion", R.drawable.ic_builder_potion, model.accountTag, t, true)
            PILL_RESEARCH -> pill(v, PillIds(R.id.pill_rpot, R.id.pill_rpot_add, R.id.pill_rpot_undo, R.id.pill_rpot_icon, R.id.pill_rpot_text),
                model.researchPotion, "research_potion", R.drawable.ic_research_potion, model.accountTag, t, true)
            PILL_SOUP -> pill(v, PillIds(R.id.pill_soup, R.id.pill_soup_add, R.id.pill_soup_undo, R.id.pill_soup_icon, R.id.pill_soup_text),
                model.soup, "study_soup", R.drawable.ic_study_soup, model.accountTag, t, true)
            PILL_PET -> pill(v, PillIds(R.id.pill_ppot, R.id.pill_ppot_add, R.id.pill_ppot_undo, R.id.pill_ppot_icon, R.id.pill_ppot_text),
                model.petPotion, "pet_potion", R.drawable.ic_pet_potion, model.accountTag, t, true)
        }
        for (box in listOf(R.id.pill_bite, R.id.pill_soup, R.id.pill_bpot, R.id.pill_rpot, R.id.pill_ppot, R.id.pill_ct)) v.setViewVisibility(box, View.GONE)
        for (slot in h.pills) {
            val box = when (slot) { PILL_BITE -> R.id.pill_bite; PILL_SOUP -> R.id.pill_soup; PILL_BUILDER -> R.id.pill_bpot; PILL_RESEARCH -> R.id.pill_rpot; PILL_CLOCKTOWER -> R.id.pill_ct; else -> R.id.pill_ppot }
            v.setViewVisibility(box, View.VISIBLE)
        }
        return v
    }

    fun smallItems(model: WidgetModel): List<ListItem> =
        model.topTimers(Int.MAX_VALUE).first.map { ListItem.Timer(it, TONE_GREEN) }

    fun timerView(r: WidgetRow, model: WidgetModel, live: Boolean, t: Palette, compact: Boolean = false, tone: Int = TONE_GREEN): RemoteViews =
        row(r, compact, live, System.currentTimeMillis(), SystemClock.elapsedRealtime(), model.accountTag, t, inList = true, tone = tone)

    fun themePalette(theme: String): Palette = palette(theme)

    /** Player name + `· CV19` / `· TH19` in warm gold, plus switch glyph when >1 account exists. */
    private fun title(model: WidgetModel, t: Palette, multipleAccounts: Boolean = false): CharSequence {
        val sb = SpannableStringBuilder(model.playerName)
        model.townHall?.let { th ->
            val start = sb.length
            val prefix = if (lctx.resources.configuration.locales[0]?.language == "pt") " · CV$th" else " · TH$th"
            sb.append(prefix)
            sb.setSpan(ForegroundColorSpan(t.gold), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (multipleAccounts) {
            val start = sb.length
            sb.append(" ⇄")
            sb.setSpan(ForegroundColorSpan(t.sub), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun buildersLabel(model: WidgetModel, t: Palette): CharSequence {
        val sb = SpannableStringBuilder(model.buildersLabel)
        if (model.goblinWorking) {
            val start = sb.length
            sb.append(" ${model.text.goblinSuffix}")
            sb.setSpan(ForegroundColorSpan(t.goblin), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun footer(model: WidgetModel, t: Palette): CharSequence {
        val sb = SpannableStringBuilder()
        for (part in model.footerLeft) {
            val start = sb.length
            sb.append(part.text)
            val color = when (part.tone) {
                FooterTone.NORMAL -> null
                FooterTone.IDLE -> t.idle
                FooterTone.ERROR -> t.error
            }
            color?.let { sb.setSpan(ForegroundColorSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        return sb
    }

    /** The Sync button starts the invisible Sync screen, which reads the copied village data without opening the app. */
    private fun syncIntent(): PendingIntent {
        val intent = Intent(context, SyncActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(SyncActivity.EXTRA_WIDGET_TAP, true)
        val options = android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options,
        )
    }

    private fun switchAccountIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetInternalReceiver::class.java)
            .setAction(ChiefWidgetReceiver.ACTION_SWITCH_ACCOUNT)
            .putExtra(ChiefWidgetReceiver.EXTRA_WIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            ("switch_acct:$appWidgetId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val MAX_BUILDER_ROWS = 8
        const val PILL_BITE = 0
        const val PILL_BUILDER = 1
        const val PILL_RESEARCH = 2
        const val PILL_PET = 3
        const val PILL_SOUP = 4
        const val PILL_CLOCKTOWER = 5
        const val MAX_TILES = 18
        const val TILES_PER_ROW = 4

        const val TONE_GREEN = 0
        const val TONE_ELIXIR = 1
        const val TONE_GOLD = 2

        fun normalHeightDp(rows: Int): Int = 165 + 42 * rows

        fun reportedHeightDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }

        fun reportedWidthDp(options: Bundle?): Int? =
            options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 }

        /** Tiles per row: up to 4 spacious cards per row on phones (2..4 depending on widget width) so rings, badges and timers never overlap card borders. */
        fun tileColumns(options: Bundle?): Int =
            (((reportedWidthDp(options) ?: 310) - 20) / 76).coerceIn(2, 4)

        fun useCompactRows(options: Bundle?, rows: Int): Boolean =
            reportedHeightDp(options)?.let { it < normalHeightDp(rows) } ?: false
    }
}

class WidgetIcons private constructor(private val context: Context) {

    @Volatile private var bundledCache: Set<String>? = null
    private fun bundledNames(): Set<String> = bundledCache ?: synchronized(this) {
        bundledCache ?: (runCatching { context.assets.list("icons")?.toSet() }.getOrNull() ?: emptySet()).also { bundledCache = it }
    }

    /** Icons downloaded from ClashKing, stored internally and prioritized over bundled placeholders. */
    private val remoteDir = java.io.File(context.filesDir, "remote_icons")
    @Volatile private var remote: Set<String>? = null
    private fun remoteNames(): Set<String> = remote ?: synchronized(this) {
        remote ?: (remoteDir.list()?.toSet() ?: emptySet()).also { remote = it }
    }

    @Volatile private var townHallFiles: Set<String>? = null
    private fun townHallNames(): Set<String> = townHallFiles ?: synchronized(this) {
        townHallFiles ?: (runCatching { context.assets.list("townhall")?.toSet() }.getOrNull() ?: emptySet()).also { townHallFiles = it }
    }

    val available: Set<String> get() = bundledNames() + remoteNames()

    /** Populates asset/remote file indices off the UI thread so Compose cache lookups stay I/O-free. */
    fun warmUp() {
        available
        townHallNames()
    }

    fun refreshRemote() {
        synchronized(this) {
            remote = null
            cache.evictAll()
        }
    }

    private val cache = LruCache<String, Bitmap>(160)

    private fun findFileIn(name: String, set: Set<String>): String? {
        val webp = "$name.webp"
        if (webp in set) return webp
        val png = "$name.png"
        if (png in set) return png
        return null
    }

    private fun findFile(name: String): String? = findFileIn(name, available)

    /** Pure in-memory cache lookup with zero disk/asset I/O; returns null on cache miss or cold index. */
    fun cachedBitmap(name: String, sizePx: Int): Bitmap? {
        val r = remote ?: return null
        val b = bundledCache ?: return null
        val remoteFile = findFileIn(name, r)
        val file = if (remoteFile == null) findFileIn(name, b) else null
        val key = "${file ?: remoteFile ?: return null}@$sizePx"
        return cache.get(key)
    }

    /** Pure in-memory cache lookup for an item/level icon; returns null on cache miss or cold index. */
    fun cachedBitmapForLevel(id: Int, level: Int?, sizePx: Int): Bitmap? {
        val r = remote ?: return null
        val b = bundledCache ?: return null
        fun knownFile(name: String): String? = findFileIn(name, r) ?: findFileIn(name, b)
        if (level != null) {
            for (l in level downTo 1) {
                val f = knownFile("${id}_$l") ?: continue
                return cache.get("$f@$sizePx")
            }
        }
        val f = knownFile(id.toString()) ?: return null
        return cache.get("$f@$sizePx")
    }

    /** Pure in-memory cache lookup across candidate IDs; returns null if any winning candidate has not been decoded yet. */
    fun cachedFirstBitmapForLevel(ids: List<Int>, level: Int?, sizePx: Int): Bitmap? {
        val r = remote ?: return null
        val b = bundledCache ?: return null
        fun knownFile(name: String): String? = findFileIn(name, r) ?: findFileIn(name, b)
        for (id in ids) {
            if (level != null) {
                for (l in level downTo 1) {
                    val f = knownFile("${id}_$l") ?: continue
                    return cache.get("$f@$sizePx")
                }
            }
            knownFile(id.toString())?.let { f -> return cache.get("$f@$sizePx") }
        }
        return null
    }

    /** Pure in-memory cache lookup for a Town Hall image; returns null on cache miss or cold index. */
    fun cachedTownHall(level: Int?, sizePx: Int): Bitmap? {
        val top = (level ?: return null).coerceIn(1, 40)
        val files = townHallFiles ?: return null
        if ("th_$top.webp" in files) {
            return cache.get("th_$top@$sizePx")
        }
        if (top > 19) {
            val r = remote ?: return null
            val b = bundledCache ?: return null
            val f = findFileIn("1000001", r) ?: findFileIn("1000001", b)
            if (f != null) return cache.get("$f@$sizePx")
        }
        for (l in (top - 1) downTo 1) {
            if ("th_$l.webp" in files) {
                return cache.get("th_$l@$sizePx")
            }
        }
        return null
    }

    fun bitmap(name: String, sizePx: Int): Bitmap? {
        val remoteFile = findFileIn(name, remoteNames())
        val file = if (remoteFile == null) findFileIn(name, bundledNames()) else null
        if (file == null && remoteFile == null) return null
        val key = "${file ?: remoteFile}@$sizePx"
        cache.get(key)?.let { return it }
        val decoded = runCatching {
            if (file != null) context.assets.open("icons/$file").use { BitmapFactory.decodeStream(it) }
            else java.io.File(remoteDir, remoteFile!!).inputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.let { if (remoteFile != null) squared(it) else it } ?: return null
        val scaled = if (decoded.width == sizePx && decoded.height == sizePx) decoded else {
            val scale = sizePx.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        }
        cache.put(key, scaled)
        return scaled
    }

    private fun squared(b: Bitmap): Bitmap {
        if (b.width == b.height) return b
        val side = maxOf(b.width, b.height)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, (side - b.width) / 2f, (side - b.height) / 2f, null)
        return out
    }

    fun bitmapForLevel(id: Int, level: Int?, sizePx: Int): Bitmap? {
        if (level != null) {
            for (l in level downTo 1) bitmap("${id}_$l", sizePx)?.let { return it }
        }
        return bitmap(id.toString(), sizePx)
    }

    /** High-res 384 px Town Hall art for the app & account sheet (`assets/townhall/th_<level>.webp`), falling back safely. */
    fun townHall(level: Int?, sizePx: Int): Bitmap? {
        val top = (level ?: return null).coerceIn(1, 40)
        val files = townHallNames()
        if ("th_$top.webp" in files) {
            val exactKey = "th_$top@$sizePx"
            cache.get(exactKey)?.let { return it }
            val exactBundled = runCatching {
                context.assets.open("townhall/th_$top.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (exactBundled != null) {
                val scale = sizePx.toFloat() / maxOf(exactBundled.width, exactBundled.height)
                val out = if (scale >= 1f) exactBundled else Bitmap.createScaledBitmap(
                    exactBundled, (exactBundled.width * scale).roundToInt().coerceAtLeast(1), (exactBundled.height * scale).roundToInt().coerceAtLeast(1), true,
                ).also { if (it !== exactBundled) exactBundled.recycle() }
                cache.put(exactKey, out)
                return out
            }
        }
        if (top > 19) bitmap("1000001", sizePx)?.let { return it }
        for (l in (top - 1) downTo 1) {
            if ("th_$l.webp" !in files) continue
            val key = "th_$l@$sizePx"
            cache.get(key)?.let { return it }
            val decoded = runCatching {
                context.assets.open("townhall/th_$l.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: continue
            val scale = sizePx.toFloat() / maxOf(decoded.width, decoded.height)
            val out = if (scale >= 1f) decoded else Bitmap.createScaledBitmap(
                decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1), (decoded.height * scale).roundToInt().coerceAtLeast(1), true,
            ).also { if (it !== decoded) decoded.recycle() }
            cache.put(key, out)
            return out
        }
        return null
    }

    fun fileFor(ids: List<Int>, level: Int?): String? {
        for (id in ids) {
            if (level != null) for (l in level downTo 1) findFile("${id}_$l")?.let { return it }
            findFile(id.toString())?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile private var instance: WidgetIcons? = null

        fun get(context: Context): WidgetIcons = instance ?: synchronized(this) {
            instance ?: WidgetIcons(context.applicationContext).also { instance = it }
        }
    }
}
