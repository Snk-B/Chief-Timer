@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Bedtime
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.PlanSlot
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.WidgetRow
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.sync.SleepStore
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DefaultDayWidth = 132.dp
private val MinDayWidth = 40.dp
private val MaxDayWidth = 720.dp

/** Upgrades queued per slot, and how far ahead (days) they are suggested. */
private const val QueueMax = 12
private const val QueueDays = 21L
private val LabelWidth = 88.dp
private val HeaderHeight = 46.dp
private val SectionHeight = 34.dp
private val LaneHeight = 54.dp

private class Bar(
    val start: Long,
    val end: Long,
    val title: String,
    val sub: String,
    val iconIds: List<Int>,
    val iconLevel: Int?,
    val suggested: Boolean,
)

private class Lane(val label: String, val bars: List<Bar>)

private class Section(val title: String, val kind: Int, val lanes: List<Lane>)

/**
 * 3D-styled calendar timeline of active upgrades plus upcoming Bedtime suggestions across builder,
 * lab, pet, and Builder Base slots.
 */
@Composable
internal fun TimerCalendar(model: WidgetModel, export: VillageExport?, now: Long, filter: TimerFilter) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val dayUnit = stringResource(R.string.time_day_short)
    val dataVersion = MaxDataStore.version
    val window = remember { SleepStore.get(context) }
    val slotText = stringResource(R.string.calendar_slot)
    val goblinText = stringResource(R.string.cat_goblin_builder)
    val starLabText = stringResource(R.string.plan_star_lab)
    val nextText = stringResource(R.string.calendar_next)
    val titles = listOf(
        stringResource(R.string.plan_builders),
        stringResource(R.string.plan_lab),
        stringResource(R.string.plan_pets),
        stringResource(R.string.filter_bb),
    )

    val nowMinute = now / 60L
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    LaunchedEffect(export, filter, nowMinute, window, dataVersion, titles) {
        val snapshotNow = nowMinute * 60L
        sections = withContext(Dispatchers.Default) {
            val data = MaxDataStore.get(context)
            val names = Names.get(context)
            fun running(row: WidgetRow): Bar? {
                val end = row.finishAt ?: return null
                if (row.kind != RowKind.RUNNING) return null
                val total = if (row.progress < 0.999f) (row.remaining / (1f - row.progress)).toLong() else row.remaining
                return Bar(end - total, end, row.label.replace(" → ", " ➛ "), "", row.iconIds, row.iconLevel, false)
            }
            val used = HashMap<Pair<Int, Int>, Int>()
            fun next(slot: PlanSlot, freeAt: Long): Bar? {
                if (export == null) return null
                val plan = Bedtime.plan(data, export, slot, freeAt, window, zone, limit = 40) ?: return null
                val pick = plan.items.firstOrNull { (used[it.id to it.level] ?: 0) < it.count } ?: return null
                used[pick.id to pick.level] = (used[pick.id to pick.level] ?: 0) + 1
                val title = "${names.name(pick.id)} ➛ ${pick.level}"
                return Bar(plan.startAt, plan.startAt + pick.seconds, title, nextText, listOf(pick.id), pick.level, true)
            }
            fun lanes(rows: List<WidgetRow>, slot: PlanSlot?, label: (Int, WidgetRow) -> String): List<Lane> =
                rows.mapIndexed { i, row ->
                    val run = running(row)
                    val bars = ArrayList<Bar>()
                    run?.let { bars += it }
                    var free = run?.end ?: snapshotNow
                    if (slot != null) {
                        var n = 0
                        while (n < QueueMax && free < snapshotNow + QueueDays * 86_400L) {
                            val up = next(slot, free) ?: break
                            bars += up
                            free = up.end
                            n++
                        }
                    }
                    Lane(label(i, row), bars)
                }
            fun idleLane(label: String, slot: PlanSlot): Lane {
                val bars = ArrayList<Bar>()
                var free = snapshotNow
                var n = 0
                while (n < QueueMax && free < snapshotNow + QueueDays * 86_400L) {
                    val up = next(slot, free) ?: break
                    bars += up
                    free = up.end
                    n++
                }
                return Lane(label, bars)
            }
            buildList {
                if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDERS) {
                    add(Section(titles[0], 0, lanes(model.builderRows, PlanSlot.BUILDER) { i, r -> if (r.goblin) goblinText else slotText.format(i + 1) }))
                }
                if (filter == TimerFilter.ALL || filter == TimerFilter.LAB) {
                    add(Section(titles[1], 1, lanes(model.labRows, PlanSlot.LAB) { i, _ -> slotText.format(i + 1) }))
                }
                if (filter == TimerFilter.ALL || filter == TimerFilter.PETS) {
                    add(Section(titles[2], 2, lanes(listOf(model.petRow), PlanSlot.PET) { i, _ -> slotText.format(i + 1) }))
                }
                if (filter == TimerFilter.ALL || filter == TimerFilter.BUILDER_BASE) {
                    val hasBb = export != null && Bedtime.builderHall(export) != null
                    val builders = lanes(model.bbRows, if (hasBb) PlanSlot.BB_BUILDER else null) { i, _ -> slotText.format(i + 1) }
                    val idle = if (hasBb) (builders.size until 2).map { idleLane(slotText.format(it + 1), PlanSlot.BB_BUILDER) } else emptyList()
                    val starLabRow = model.starLabRow
                    val starLab: List<Lane> = when {
                        starLabRow != null -> lanes(listOf(starLabRow), if (hasBb) PlanSlot.STAR_LAB else null) { _, _ -> starLabText }
                        hasBb -> listOf(idleLane(starLabText, PlanSlot.STAR_LAB))
                        else -> emptyList()
                    }
                    val all = builders + idle + starLab
                    if (all.isNotEmpty()) add(Section(titles[3], 3, all))
                }
            }
        }
    }

    val t0 = remember(now / 3600) { Instant.ofEpochSecond(now).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond() }
    val lastEnd = sections.maxOfOrNull { s -> s.lanes.maxOfOrNull { l -> l.bars.maxOfOrNull { it.end } ?: 0L } ?: 0L } ?: 0L
    val days = (((maxOf(lastEnd, now + 2 * 86_400L) - t0) / 86_400L) + 1).toInt().coerceIn(3, 45)
    var dayDp by rememberSaveable { mutableFloatStateOf(DefaultDayWidth.value) }
    val dayWidth = dayDp.dp
    val hourStep = when {
        dayDp >= 560f -> 1
        dayDp >= 280f -> 3
        dayDp >= 170f -> 6
        else -> 0
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableIntStateOf(0) }
    fun zoomBy(factor: Float) {
        val old = dayDp
        val new = (old * factor).coerceIn(MinDayWidth.value, MaxDayWidth.value)
        if (new == old) return
        dayDp = new
        val ratio = new / old
        val target = ((scroll.value + viewport / 2f) * ratio - viewport / 2f).toInt().coerceAtLeast(0)
        scope.launch { withFrameNanos { }; scroll.scrollTo(target) }
    }
    val hourWidth = dayWidth / 24f
    fun xOf(t: Long): Dp = hourWidth * ((t - t0) / 3600f)

    val locale = Locale.getDefault()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale) }
    val today = stringResource(R.string.calendar_today)
    val lineColor = pal.cardBorder.copy(alpha = 0.55f)
    val nowColor = Color(0xFFEE7214)
    val density = LocalDensity.current

    TavernStoneCard(contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_zoom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Clash3DButton(
                onClick = { zoomBy(1f / 1.5f) },
                enabled = dayDp > MinDayWidth.value,
                style = ClashButtonStyle.STONE,
                minHeight = 36.dp,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
            ) {
                ClashOutlinedText("−", fontSize = 18.sp, color = Color.White, strokeWidth = 4f)
            }
            Clash3DButton(
                onClick = { zoomBy(1.5f) },
                enabled = dayDp < MaxDayWidth.value,
                style = ClashButtonStyle.STONE,
                minHeight = 36.dp,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
            ) {
                ClashOutlinedText("+", fontSize = 18.sp, color = Color.White, strokeWidth = 4f)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(LabelWidth)) {
                CalendarSpacer(HeaderHeight)
                sections.forEach { s ->
                    Box(Modifier.height(SectionHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        ClashOutlinedText(
                            text = s.title,
                            fontSize = 13.sp,
                            color = TavernGold,
                            strokeWidth = 3.8f,
                            maxLines = 1,
                        )
                    }
                    s.lanes.forEach { l ->
                        Box(Modifier.height(LaneHeight).fillMaxWidth().padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                            Text(
                                l.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.barTrackBg.copy(alpha = 0.55f))
                    .onSizeChanged { viewport = it.width }
                    .pinchZoom { zoomBy(it) }
                    .horizontalScroll(scroll),
            ) {
                Column(
                    Modifier
                        .width(dayWidth * days)
                        .drawBehind {
                            val dayPx = dayWidth.toPx()
                            for (d in 0..days) drawLine(lineColor, Offset(d * dayPx, 0f), Offset(d * dayPx, size.height), 1.dp.toPx())
                            if (hourStep > 0) {
                                val faint = lineColor.copy(alpha = lineColor.alpha * 0.45f)
                                for (d in 0 until days) for (h in hourStep until 24 step hourStep) {
                                    val x = d * dayPx + dayPx * h / 24f
                                    drawLine(faint, Offset(x, HeaderHeight.toPx()), Offset(x, size.height), 1.dp.toPx())
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val x = with(density) { xOf(now).toPx() }
                            drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        },
                ) {
                    Row(Modifier.height(HeaderHeight)) {
                        for (d in 0 until days) {
                            val label = if (d == 0) today else dayFormat.format(Instant.ofEpochSecond(t0 + d * 86_400L).atZone(zone))
                            Box(Modifier.width(dayWidth).fillMaxHeight()) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (d == 0) TavernGoldLight else Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp),
                                )
                                if (hourStep > 0) for (h in hourStep until 24 step hourStep) {
                                    Text(
                                        "%02d:00".format(h),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.align(Alignment.BottomStart).offset(x = hourWidth * h.toFloat() + 3.dp).padding(bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    sections.forEach { s ->
                        CalendarSpacer(SectionHeight)
                        s.lanes.forEach { lane ->
                            Box(Modifier.height(LaneHeight).fillMaxWidth()) {
                                lane.bars.forEach { bar -> CalendarBar(bar, s.kind, now, dayUnit, xOf(maxOf(bar.start, t0)), xOf(bar.end)) }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.calendar_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarSpacer(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))

@Composable
private fun CalendarBar(bar: Bar, kind: Int, now: Long, dayUnit: String, left: Dp, right: Dp) {
    val pal = LocalChiefPalette.current
    val fillColors: List<Color> = when {
        bar.suggested -> listOf(pal.cardTop.copy(alpha = 0.72f), pal.cardBot.copy(alpha = 0.88f))
        kind == 1 -> listOf(pal.barSecondary.first.copy(alpha = 0.40f), pal.barSecondary.third.copy(alpha = 0.60f))
        kind == 2 -> listOf(pal.barTertiary.first.copy(alpha = 0.36f), pal.barTertiary.third.copy(alpha = 0.56f))
        kind == 3 -> listOf(Color(0xFF1F5468), Color(0xFF133340))
        else -> listOf(pal.barPrimary.first.copy(alpha = 0.34f), pal.barPrimary.third.copy(alpha = 0.54f))
    }
    val rimColor: Color = when {
        bar.suggested -> pal.accent.copy(alpha = 0.52f)
        kind == 1 -> pal.barSecondary.first
        kind == 2 -> pal.barTertiary.first
        kind == 3 -> Color(0xFF4CC9F0)
        else -> pal.barPrimary.first
    }
    val width = (right - left).coerceAtLeast(42.dp)
    val shape = RoundedCornerShape(12.dp)
    val icon = rememberFirstGameIcon(bar.iconIds, bar.iconLevel, 26.dp)
    Row(
        Modifier
            .offset(x = left, y = 5.dp)
            .width(width)
            .height(LaneHeight - 10.dp)
            .clip(shape)
            .background(Brush.verticalGradient(fillColors))
            .border(BorderStroke(1.2.dp, rimColor), shape)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            TavernIconFrame(size = 30.dp) {
                Image(icon, contentDescription = null, modifier = Modifier.padding(2.dp).fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(bar.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (bar.suggested) {
                "${bar.sub} · ${Countdown.text(bar.end - bar.start, dayUnit)}"
            } else {
                Countdown.text((bar.end - now).coerceAtLeast(0), dayUnit)
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (bar.suggested) TavernGoldLight else Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two-finger pinch reported as a zoom factor; the fingers are consumed so the list does not scroll while pinching. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
