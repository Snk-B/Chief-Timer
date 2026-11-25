@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Bedtime
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.GameEvents
import com.snkb.chieftimer.core.MaxData
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
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import com.snkb.chieftimer.update.ClashKingDatesStore
import com.snkb.chieftimer.update.EventsStore
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
private val BarShape = RoundedCornerShape(12.dp)

private class Bar(
    val start: Long,
    val end: Long,
    val title: String,
    val sub: String,
    val iconIds: List<Int>,
    val iconLevel: Int?,
    val suggested: Boolean,
    val cost: Long? = null,
    val resource: Int? = null,
)

private class Lane(val label: String, val bars: List<Bar>)

private class Section(val title: String, val kind: Int, val lanes: List<Lane>)

private class CalendarBarStyle(val fillBrush: Brush, val rimColor: Color)

/**
 * 3D-styled calendar timeline of active upgrades plus upcoming Bedtime suggestions across builder,
 * lab, pet, and Builder Base slots, with shaded sleep bands, CWL marker, and tap-to-inspect details.
 */
@Composable
internal fun TimerCalendar(model: WidgetModel, export: VillageExport?, now: Long, filter: TimerFilter) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val dayUnit = stringResource(R.string.time_day_short)
    val dataVersion = MaxDataStore.version
    val sleepVersion = SleepStore.version
    val window = remember(sleepVersion) { SleepStore.get(context) }
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

    // Key queue calculation on structural state + sleep window (avoid re-running 60 Bedtime.plan passes every 60s).
    val runningSignature = remember(model, filter) {
        (model.builderRows + model.labRows + listOfNotNull(model.petRow, model.starLabRow) + model.bbRows)
            .joinToString("|") { "${it.jobKey ?: it.label}:${it.kind}:${it.finishAt}" }
    }
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    LaunchedEffect(export, filter, runningSignature, window, dataVersion, titles) {
        val snapshotNow = now
        sections = withContext(Dispatchers.Default) {
            val data: MaxData = MaxDataStore.get(context)
            val names = Names.get(context)
            fun lookupStepCost(ids: List<Int>, level: Int?): Pair<Long?, Int?> {
                val lvl = level ?: return null to null
                for (id in ids) {
                    val step = data.items[id]?.steps?.getOrNull(lvl - 1) ?: continue
                    return step.cost to step.resource
                }
                return null to null
            }
            fun running(row: WidgetRow): Bar? {
                val end = row.finishAt ?: return null
                if (row.kind != RowKind.RUNNING) return null
                val total = if (row.progress < 0.999f) (row.remaining / (1f - row.progress)).toLong() else row.remaining
                val (cost, res) = lookupStepCost(row.iconIds, row.iconLevel)
                return Bar(end - total, end, row.label.replace(" → ", " ➛ "), "", row.iconIds, row.iconLevel, false, cost, res)
            }
            val used = HashMap<Pair<Int, Int>, Int>()
            fun next(slot: PlanSlot, freeAt: Long): Bar? {
                if (export == null) return null
                val plan = Bedtime.plan(data, export, slot, freeAt, window, zone, limit = 40) ?: return null
                val pick = plan.items.firstOrNull { (used[it.id to it.level] ?: 0) < it.count } ?: return null
                used[pick.id to pick.level] = (used[pick.id to pick.level] ?: 0) + 1
                val title = "${names.name(pick.id)} ➛ ${pick.level}"
                return Bar(plan.startAt, plan.startAt + pick.seconds, title, nextText, listOf(pick.id), pick.level, true, pick.cost, pick.resource)
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
    val tEnd = t0 + days * 86_400L
    val sleepSegments = remember(t0, days, window, zone) {
        if (window.startMin == window.endMin) return@remember emptyList()
        buildList {
            val baseDate = Instant.ofEpochSecond(t0).atZone(zone).toLocalDate()
            for (d in -1..days) {
                val date = baseDate.plusDays(d.toLong())
                val startZdt = date.atTime(window.startMin / 60, window.startMin % 60).atZone(zone)
                val endDate = if (window.startMin < window.endMin) date else date.plusDays(1)
                val endZdt = endDate.atTime(window.endMin / 60, window.endMin % 60).atZone(zone)
                val s = maxOf(startZdt.toEpochSecond(), t0)
                val e = minOf(endZdt.toEpochSecond(), tEnd)
                if (e > s) add(s to e)
            }
        }
    }
    val cwlEvent = remember(t0, days) {
        GameEvents.upcoming(t0, EventsStore.config, ClashKingDatesStore.dates)
            .firstOrNull { it.id == GameEvents.CWL_WARS && it.startsAt < tEnd && it.endsAt > t0 }
    }
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
    val cwlTag = stringResource(R.string.calendar_cwl_tag)
    val lineColor = pal.cardBorder.copy(alpha = 0.55f)
    val sleepBandColor = Color(0x36141F3A)
    val cwlColor = Color(0xFFE9A825)
    val nowColor = Color(0xFFEE7214)
    val density = LocalDensity.current
    val dayPxFloat = with(density) { dayWidth.toPx() }
    val barStyles = remember(pal) {
        fun style(kind: Int, suggested: Boolean): CalendarBarStyle {
            val fill = when {
                suggested -> listOf(pal.cardTop.copy(alpha = 0.72f), pal.cardBot.copy(alpha = 0.88f))
                kind == 1 -> listOf(pal.barSecondary.first.copy(alpha = 0.40f), pal.barSecondary.third.copy(alpha = 0.60f))
                kind == 2 -> listOf(pal.barTertiary.first.copy(alpha = 0.36f), pal.barTertiary.third.copy(alpha = 0.56f))
                kind == 3 -> listOf(Color(0xFF1F5468), Color(0xFF133340))
                else -> listOf(pal.barPrimary.first.copy(alpha = 0.34f), pal.barPrimary.third.copy(alpha = 0.54f))
            }
            val rim = when {
                suggested -> pal.accent.copy(alpha = 0.52f)
                kind == 1 -> pal.barSecondary.first
                kind == 2 -> pal.barTertiary.first
                kind == 3 -> Color(0xFF4CC9F0)
                else -> pal.barPrimary.first
            }
            return CalendarBarStyle(Brush.verticalGradient(fill), rim)
        }
        mapOf(
            (0 to false) to style(0, false),
            (1 to false) to style(1, false),
            (2 to false) to style(2, false),
            (3 to false) to style(3, false),
            (0 to true) to style(0, true),
        )
    }
    var selectedBar by remember { mutableStateOf<Bar?>(null) }

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
                            val headerPx = HeaderHeight.toPx()
                            for ((s, e) in sleepSegments) {
                                val x1 = ((s - t0) / 86_400f) * dayPx
                                val x2 = ((e - t0) / 86_400f) * dayPx
                                drawRect(sleepBandColor, Offset(x1, headerPx), Size((x2 - x1).coerceAtLeast(1f), size.height - headerPx))
                            }
                            for (d in 0..days) drawLine(lineColor, Offset(d * dayPx, 0f), Offset(d * dayPx, size.height), 1.dp.toPx())
                            if (hourStep > 0) {
                                val faint = lineColor.copy(alpha = lineColor.alpha * 0.45f)
                                for (d in 0 until days) for (h in hourStep until 24 step hourStep) {
                                    val x = d * dayPx + dayPx * h / 24f
                                    drawLine(faint, Offset(x, headerPx), Offset(x, size.height), 1.dp.toPx())
                                }
                            }
                            cwlEvent?.let { ev ->
                                if (ev.startsAt in t0..tEnd) {
                                    val cx = ((ev.startsAt - t0) / 86_400f) * dayPx
                                    drawLine(
                                        color = cwlColor,
                                        start = Offset(cx, headerPx * 0.45f),
                                        end = Offset(cx, size.height),
                                        strokeWidth = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f),
                                    )
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val x = with(density) { xOf(now).toPx() }
                            drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        },
                ) {
                    Box(Modifier.height(HeaderHeight).fillMaxWidth()) {
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
                        cwlEvent?.let { ev ->
                            if (ev.startsAt in t0..tEnd) {
                                Text(
                                    text = "⚔ $cwlTag",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cwlColor,
                                    modifier = Modifier.align(Alignment.TopStart).offset(x = xOf(ev.startsAt) + 4.dp, y = 4.dp),
                                )
                            }
                        }
                    }
                    val visLeftPx = if (viewport > 0) scroll.value - viewport * 0.5f else -Float.MAX_VALUE
                    val visRightPx = if (viewport > 0) scroll.value + viewport * 1.5f else Float.MAX_VALUE
                    sections.forEach { s ->
                        CalendarSpacer(SectionHeight)
                        s.lanes.forEach { lane ->
                            Box(Modifier.height(LaneHeight).fillMaxWidth()) {
                                lane.bars.forEach { bar ->
                                    val barLeftSec = maxOf(bar.start, t0)
                                    val barLeftPx = ((barLeftSec - t0) / 86_400f) * dayPxFloat
                                    val barRightPx = ((bar.end - t0) / 86_400f) * dayPxFloat
                                    if (barRightPx >= visLeftPx && barLeftPx <= visRightPx) {
                                        val style = barStyles[if (bar.suggested) 0 to true else s.kind to false]
                                            ?: barStyles.getValue(0 to false)
                                        CalendarBar(
                                            bar = bar,
                                            style = style,
                                            now = now,
                                            dayUnit = dayUnit,
                                            left = xOf(barLeftSec),
                                            right = xOf(bar.end),
                                            onClick = { selectedBar = bar },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.calendar_sleep_legend),
            style = MaterialTheme.typography.labelSmall,
            color = pal.accentLight,
        )
        Text(
            stringResource(R.string.calendar_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    selectedBar?.let { bar ->
        val dateTimeFmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zone) }
        val endsInSleep = remember(bar, window) { window.asleep(bar.end, zone) }
        val hitsCwl = remember(bar, cwlEvent) { cwlEvent != null && bar.end > cwlEvent.startsAt && bar.start < cwlEvent.endsAt }
        val icon = rememberFirstGameIcon(bar.iconIds, bar.iconLevel, 36.dp)
        AlertDialog(
            onDismissRequest = { selectedBar = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (icon != null) {
                        TavernIconFrame(size = 42.dp) {
                            Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    ClashOutlinedText(
                        text = bar.title,
                        fontSize = 17.sp,
                        color = TavernGoldLight,
                        strokeWidth = 4.2f,
                        maxLines = 2,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(if (bar.suggested) R.string.calendar_bar_suggested else R.string.calendar_bar_active),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (bar.suggested) pal.accentLight else TavernGreenLight,
                    )
                    Text(
                        text = stringResource(R.string.calendar_bar_start, dateTimeFmt.format(Instant.ofEpochSecond(bar.start))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.calendar_bar_end, dateTimeFmt.format(Instant.ofEpochSecond(bar.end))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.calendar_bar_duration, Countdown.text((bar.end - bar.start).coerceAtLeast(0L), dayUnit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    bar.cost?.takeIf { it > 0L }?.let { c ->
                        val resLabel = when (bar.resource) {
                            1, 5 -> stringResource(R.string.resource_elixir)
                            2 -> stringResource(R.string.resource_dark_elixir)
                            3 -> stringResource(R.string.road_gold_or_elixir)
                            else -> stringResource(R.string.resource_gold)
                        }
                        Text(
                            text = stringResource(R.string.calendar_bar_cost, NumberFormat.getIntegerInstance().format(c), resLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.accentLight,
                        )
                    }
                    if (endsInSleep) {
                        Text(
                            text = "☾ ${stringResource(R.string.calendar_sleep_warn)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFB74D),
                        )
                    }
                    if (hitsCwl) {
                        Text(
                            text = "⚔ ${stringResource(R.string.calendar_cwl_warn)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = cwlColor,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBar = null }) {
                    Text(stringResource(R.string.action_close), color = TavernGold)
                }
            },
        )
    }
}

@Composable
private fun CalendarSpacer(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))

@Composable
private fun CalendarBar(bar: Bar, style: CalendarBarStyle, now: Long, dayUnit: String, left: Dp, right: Dp, onClick: () -> Unit) {
    val width = (right - left).coerceAtLeast(42.dp)
    val icon = rememberFirstGameIcon(bar.iconIds, bar.iconLevel, 26.dp)
    Row(
        Modifier
            .offset(x = left, y = 5.dp)
            .width(width)
            .height(LaneHeight - 10.dp)
            .clip(BarShape)
            .background(style.fillBrush)
            .border(BorderStroke(1.2.dp, style.rimColor), BarShape)
            .clickable(onClick = onClick)
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
