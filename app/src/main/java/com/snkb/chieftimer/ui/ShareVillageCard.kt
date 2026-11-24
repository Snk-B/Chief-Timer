package com.snkb.chieftimer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.RoadToMax
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.ui.theme.Chief3DPalette
import com.snkb.chieftimer.widget.WidgetIcons
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders a stylized 3D Village Progress Card PNG (no player tag) and opens Android's share sheet. */
internal suspend fun shareVillageCard(
    context: Context,
    playerName: String,
    road: RoadToMax,
    builders: Int,
    doneFraction: Double?,
    pal: Chief3DPalette,
) {
    val uri = withContext(Dispatchers.IO) {
        val lctx = LanguageManager.wrap(context)
        val bmp = renderVillageCardBitmap(context, lctx, playerName, road, builders.coerceAtLeast(1), doneFraction, pal)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "chief_timer_village.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 96, out) }
        bmp.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.road_share_card)))
}

private fun renderVillageCardBitmap(
    context: Context,
    lctx: Context,
    playerName: String,
    road: RoadToMax,
    builders: Int,
    doneFraction: Double?,
    pal: Chief3DPalette,
): Bitmap {
    val w = 1080
    val h = 1280
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val tf = runCatching { ResourcesCompat.getFont(context, R.font.lilita_one) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    fun ColorArgb(composeColor: androidx.compose.ui.graphics.Color): Int {
        val a = (composeColor.alpha * 255f).toInt().coerceIn(0, 255)
        val r = (composeColor.red * 255f).toInt().coerceIn(0, 255)
        val g = (composeColor.green * 255f).toInt().coerceIn(0, 255)
        val b = (composeColor.blue * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    val bgTop = ColorArgb(pal.screenBgTop)
    val bgBot = ColorArgb(pal.screenBgBot)
    val cardTop = ColorArgb(pal.cardTop)
    val cardBot = ColorArgb(pal.cardBot)
    val borderCol = ColorArgb(pal.cardBorder)
    val accentCol = ColorArgb(pal.accentLight)
    val barBg = ColorArgb(pal.barTrackBg)

    // 1. Background gradient
    c.drawRect(
        0f, 0f, w.toFloat(), h.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), bgTop, bgBot, Shader.TileMode.CLAMP)
        },
    )

    // 2. Main card frame
    val outer = RectF(56f, 56f, w - 56f, h - 56f)
    c.drawRoundRect(
        outer, 44f, 44f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, outer.top, 0f, outer.bottom, cardTop, cardBot, Shader.TileMode.CLAMP)
        },
    )
    c.drawRoundRect(
        outer, 44f, 44f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = borderCol
        },
    )

    fun textPaint(size: Float, color: Int = android.graphics.Color.WHITE, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            this.color = color
            textAlign = align
        }

    fun drawOutlined(text: String, x: Float, y: Float, size: Float, fillCol: Int, align: Paint.Align = Paint.Align.LEFT) {
        val stroke = textPaint(size, 0xCC0A0A0A.toInt(), align).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.14f
            strokeJoin = Paint.Join.ROUND
        }
        val fill = textPaint(size, fillCol, align)
        c.drawText(text, x, y, stroke)
        c.drawText(text, x, y, fill)
    }

    // 3. Header: Town Hall icon + Player + TH title + Overall %
    val icons = WidgetIcons.get(context)
    val thBmp = icons.bitmapForLevel(1000001, road.townHall, 176)
    if (thBmp != null) {
        c.drawBitmap(thBmp, 108f, 108f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
    val safeName = playerName.ifBlank { "Chief" }
    drawOutlined(safeName, 312f, 178f, 54f, android.graphics.Color.WHITE)
    drawOutlined(
        lctx.getString(R.string.road_title, road.townHall),
        312f,
        240f,
        34f,
        accentCol,
    )
    val pct = ((doneFraction ?: 1.0) * 100).toInt().coerceIn(0, 100)
    drawOutlined("$pct%", w - 108f, 224f, 76f, accentCol, Paint.Align.RIGHT)

    // 4. Overall progress tube bar
    val barRect = RectF(108f, 318f, w - 108f, 368f)
    c.drawRoundRect(barRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barBg })
    c.drawRoundRect(
        barRect, 25f, 25f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = borderCol },
    )
    val fillFrac = ((doneFraction ?: 1.0).toFloat()).coerceIn(0.02f, 1f)
    val fillRect = RectF(barRect.left + 4f, barRect.top + 4f, barRect.left + (barRect.width() - 8f) * fillFrac + 4f, barRect.bottom - 4f)
    c.drawRoundRect(
        fillRect, 21f, 21f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, fillRect.top, 0f, fillRect.bottom, ColorArgb(pal.accentLight), ColorArgb(pal.accent), Shader.TileMode.CLAMP)
        },
    )

    // 5. Category breakdown pills (2 columns x 4 rows)
    fun pctStr(v: Double?) = "${((v ?: 1.0) * 100).toInt().coerceIn(0, 100)}%"
    val cats = listOf(
        lctx.getString(R.string.road_buildings) to pctStr(road.work.buildings.done),
        lctx.getString(R.string.road_defences) to pctStr(road.work.defences.done),
        lctx.getString(R.string.road_heroes) to pctStr(road.work.heroes.done),
        lctx.getString(R.string.road_troops) to pctStr(road.work.troops.done),
        lctx.getString(R.string.road_spells) to pctStr(road.work.spells.done),
        lctx.getString(R.string.road_equipment) to pctStr(road.work.equipment.done),
        lctx.getString(R.string.road_pets) to pctStr(road.work.pets.done),
        lctx.getString(R.string.road_walls) to pctStr(road.work.walls.done),
    )
    val colW = (w - 246f) / 2f
    val boxH = 104f
    cats.forEachIndexed { i, (label, value) ->
        val col = i % 2
        val row = i / 2
        val left = 108f + col * (colW + 30f)
        val top = 420f + row * (boxH + 22f)
        val r = RectF(left, top, left + colW, top + boxH)
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barBg })
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = borderCol })
        c.drawText(label, left + 24f, top + 64f, textPaint(29f, 0xFFE8E2D6.toInt()))
        drawOutlined(value, r.right - 24f, top + 66f, 34f, accentCol, Paint.Align.RIGHT)
    }

    // 6. Time summary footer box
    val dayUnit = lctx.getString(R.string.time_day_short)
    fun fmt(sec: Long): String {
        val d = sec / 86_400
        return if (d >= 1) String.format(Locale.US, "%,d %s", d, dayUnit) else Countdown.text(sec, dayUnit)
    }
    val summaryRect = RectF(108f, 948f, w - 108f, 1110f)
    c.drawRoundRect(summaryRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barBg })
    c.drawRoundRect(summaryRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = borderCol })
    c.drawText(
        "${lctx.getString(R.string.road_builders_x, builders)}: ${fmt(road.builderSeconds(builders))}",
        summaryRect.left + 28f, summaryRect.top + 64f, textPaint(30f, android.graphics.Color.WHITE),
    )
    c.drawText(
        "${lctx.getString(R.string.road_lab)}: ${fmt(road.lab.seconds)}  ·  ${lctx.getString(R.string.road_pets)}: ${fmt(road.pets.seconds)}",
        summaryRect.left + 28f, summaryRect.top + 122f, textPaint(28f, 0xFFDAD4C8.toInt()),
    )

    // Watermark
    drawOutlined("Chief Timer", w / 2f, h - 86f, 30f, accentCol, Paint.Align.CENTER)
    return bmp
}
