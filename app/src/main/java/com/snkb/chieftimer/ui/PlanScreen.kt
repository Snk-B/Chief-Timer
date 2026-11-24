@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Bedtime
import com.snkb.chieftimer.core.PlanCandidate
import com.snkb.chieftimer.core.PlanFilter
import com.snkb.chieftimer.core.PlanSlot
import com.snkb.chieftimer.core.PlanSort
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.SleepWindow
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.sync.SleepStore
import com.snkb.chieftimer.sync.SyncRunner
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldDark
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NamedPlanCandidate(val name: String, val item: PlanCandidate)

private data class SlotPlanState(
    val slot: PlanSlot,
    val waiting: Boolean,
    val freeAt: Long,
    val items: List<NamedPlanCandidate>,
)

/** One page of the Plan tab: 0 is Road to max, 1 is Bedtime level up. */
@Composable
internal fun PlanPage(export: VillageExport?, model: WidgetModel, now: Long, page: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataVersion = MaxDataStore.version
    var window by remember { mutableStateOf(SleepStore.get(context)) }
    var sleepAlert by remember { mutableStateOf(SleepStore.alertEnabled(context)) }
    val zone = remember { ZoneId.systemDefault() }
    var filter by remember { mutableStateOf(PlanFilter()) }
    val locale = Locale.getDefault()
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEjmm") }
    val clockFormat = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    val dayUnit = stringResource(R.string.time_day_short)

    val nowMinute = now / 60L
    var slotPlans by remember { mutableStateOf<List<SlotPlanState>>(emptyList()) }
    if (page == 1 && export != null && model.hasData) {
        LaunchedEffect(export, model, dataVersion, nowMinute, window, filter, zone) {
            val snapshotNow = nowMinute * 60L
            slotPlans = withContext(Dispatchers.Default) {
                val data = MaxDataStore.get(context)
                val names = Names.get(context)
                val hasBb = Bedtime.builderHall(export) != null
                val busyRows = model.builderRows.filter { it.kind == RowKind.RUNNING && it.finishAt != null }
                val bbBusyRows = model.bbRows.filter { it.kind == RowKind.RUNNING && it.finishAt != null }
                PlanSlot.entries.mapNotNull { slot ->
                    if ((slot == PlanSlot.BB_BUILDER || slot == PlanSlot.STAR_LAB) && !hasBb) return@mapNotNull null
                    val freeAt: Long = when (slot) {
                        PlanSlot.BUILDER ->
                            if (model.buildersBusy < model.buildersTotal) snapshotNow else busyRows.minOfOrNull { it.finishAt!! } ?: snapshotNow
                        PlanSlot.LAB -> model.labRow.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: snapshotNow
                        PlanSlot.PET -> model.petRow.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: snapshotNow
                        PlanSlot.BB_BUILDER ->
                            if (bbBusyRows.size < 2) snapshotNow else bbBusyRows.minOfOrNull { it.finishAt!! } ?: snapshotNow
                        PlanSlot.STAR_LAB -> model.starLabRow?.takeIf { it.kind == RowKind.RUNNING }?.finishAt ?: snapshotNow
                    }
                    val key = if (freeAt <= snapshotNow) snapshotNow else freeAt
                    val items = Bedtime.plan(data, export, slot, key, window, zone, filter = filter)
                        ?.items.orEmpty()
                        .map { NamedPlanCandidate(names.name(it.id), it) }
                    SlotPlanState(
                        slot = slot,
                        waiting = freeAt - snapshotNow > 60L,
                        freeAt = freeAt,
                        items = items,
                    )
                }
            }
        }
    }

    if (page == 0 && export != null && model.hasData) {
        RoadToMaxCard(
            export = export,
            builders = model.buildersTotal,
            playerName = model.playerName,
            accountTag = model.accountTag,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.Top,
    ) {
        if (export == null || !model.hasData) {
            item(key = "plan_welcome") {
                TavernWoodBanner {
                    ClashOutlinedText(
                        text = stringResource(R.string.welcome_title),
                        fontSize = 19.sp,
                        color = TavernGoldLight,
                        strokeWidth = 5f,
                    )
                    Text(
                        stringResource(R.string.welcome_sub),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "sleep_card") {
            Box(Modifier.padding(bottom = 12.dp)) {
                SleepCard(
                    window = window,
                    alertEnabled = sleepAlert,
                    onChange = {
                        window = it
                        SleepStore.set(context, it)
                        if (sleepAlert) {
                            scope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                        }
                    },
                    onAlertChange = { enabled ->
                        sleepAlert = enabled
                        SleepStore.setAlertEnabled(context, enabled)
                        scope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                    },
                )
            }
        }
        item(key = "filter_card") {
            Box(Modifier.padding(bottom = 12.dp)) {
                FilterCard(filter) { filter = it }
            }
        }
        slotPlans.forEach { planState ->
            if (planState.items.isEmpty()) {
                item(key = "plan_solo_${planState.slot.name}") {
                    val freeText = clockFormat.format(Instant.ofEpochSecond(planState.freeAt).atZone(zone))
                    TavernStoneCard(
                        modifier = Modifier.padding(bottom = 12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        SlotHeaderContent(planState.slot, planState.waiting, freeText)
                        Text(
                            stringResource(R.string.plan_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                item(key = "plan_hdr_${planState.slot.name}") {
                    val freeText = clockFormat.format(Instant.ofEpochSecond(planState.freeAt).atZone(zone))
                    SlotSectionHeaderItem(planState.slot, planState.waiting, freeText)
                }
                itemsIndexed(
                    items = planState.items,
                    key = { idx, entry -> "plan_row_${planState.slot.name}_${idx}_${entry.item.id}_${entry.item.level}" },
                ) { idx, entry ->
                    val isLast = idx == planState.items.lastIndex
                    val doneAtText = stringResource(
                        R.string.plan_done_at,
                        clockFormat.format(Instant.ofEpochSecond(entry.item.finishAt).atZone(zone)),
                    )
                    CandidateSectionRow(
                        name = entry.name,
                        item = entry.item,
                        doneText = doneAtText,
                        dayUnit = dayUnit,
                        isLast = isLast,
                    )
                }
            }
        }
        item(key = "plan_note") {
            Text(
                stringResource(R.string.plan_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FilterCard(filter: PlanFilter, onChange: (PlanFilter) -> Unit) {
    fun <T> Set<T>.toggle(x: T) = if (x in this) this - x else this + x
    TavernStoneCard(contentPadding = PaddingValues(vertical = 12.dp)) {
        ChipRow {
            for ((c, label) in listOf(
                'd' to R.string.plan_filter_defence,
                'p' to R.string.plan_filter_permanent,
                'a' to R.string.plan_filter_army,
                'r' to R.string.plan_filter_resource,
            )) {
                TavernFilterPill(
                    label = stringResource(label),
                    selected = c in filter.categories,
                    onClick = { onChange(filter.copy(categories = filter.categories.toggle(c))) },
                )
            }
            TavernFilterPill(
                label = stringResource(R.string.plan_filter_new),
                selected = filter.onlyNew,
                onClick = { onChange(filter.copy(onlyNew = !filter.onlyNew)) },
            )
        }
        ChipRow {
            for ((r, label) in listOf(
                0 to R.string.plan_filter_gold,
                1 to R.string.plan_filter_elixir,
                2 to R.string.plan_filter_dark,
            )) {
                TavernFilterPill(
                    label = stringResource(label),
                    selected = r in filter.resources,
                    onClick = { onChange(filter.copy(resources = filter.resources.toggle(r))) },
                )
            }
        }
        ChipRow {
            for ((sort, label) in listOf(
                PlanSort.FINISH to R.string.plan_sort_time,
                PlanSort.COST_HIGH to R.string.plan_sort_cost_high,
                PlanSort.COST_LOW to R.string.plan_sort_cost_low,
            )) {
                TavernFilterPill(
                    label = stringResource(label),
                    selected = filter.sort == sort,
                    onClick = { onChange(filter.copy(sort = sort)) },
                )
            }
        }
        if (filter != PlanFilter()) {
            TextButton(onClick = { onChange(PlanFilter()) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.plan_filter_clear), color = TavernGoldLight)
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private fun timeText(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun SleepCard(
    window: SleepWindow,
    alertEnabled: Boolean,
    onChange: (SleepWindow) -> Unit,
    onAlertChange: (Boolean) -> Unit,
) {
    val pal = LocalChiefPalette.current
    var editing by remember { mutableStateOf<Boolean?>(null) }
    TavernWoodBanner {
        ClashOutlinedText(
            text = stringResource(R.string.plan_bedtime),
            fontSize = 19.sp,
            color = Color.White,
            strokeWidth = 5f,
        )
        Text(
            stringResource(R.string.plan_sleep_from),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFECC2),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeChip(timeText(window.startMin)) { editing = true }
            Text(stringResource(R.string.plan_sleep_to), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            TimeChip(timeText(window.endMin)) { editing = false }
        }
        Text(
            stringResource(R.string.plan_bedtime_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFECC2).copy(alpha = 0.9f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = alertEnabled,
                    role = Role.Switch,
                    onValueChange = onAlertChange,
                )
                .padding(top = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.bedtime_alert_enable),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFECC2),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = alertEnabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = pal.pillActiveText,
                    checkedTrackColor = pal.pillActiveTop,
                    checkedBorderColor = pal.accentLight,
                    uncheckedThumbColor = Color(0xFFD0C4B4),
                    uncheckedTrackColor = pal.barTrackBg,
                    uncheckedBorderColor = pal.cardBorder,
                ),
            )
        }
    }
    editing?.let { isStart ->
        TimeDialog(
            initialMin = if (isStart) window.startMin else window.endMin,
            onDone = { minutes ->
                onChange(if (isStart) window.copy(startMin = minutes) else window.copy(endMin = minutes))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(pal.chipFillTop, pal.chipFillBot)),
            )
            .border(1.4.dp, pal.accent, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ClashOutlinedText(
            text = text,
            fontSize = 18.sp,
            color = TavernGoldLight,
            strokeWidth = 4.5f,
            tabularNums = true,
        )
    }
}

@Composable
private fun TimeDialog(initialMin: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialMin / 60, initialMin % 60, DateFormat.is24HourFormat(LocalContext.current))
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClashOutlinedText(
                    text = stringResource(R.string.plan_pick_time),
                    fontSize = 18.sp,
                    color = TavernGold,
                    strokeWidth = 4.5f,
                    modifier = Modifier.fillMaxWidth(),
                )
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onDone(state.hour * 60 + state.minute) }) {
                        Text(stringResource(R.string.action_save), color = TavernGoldLight)
                    }
                }
            }
        }
    }
}

private val SlotTopShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val SlotBotShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)

@Composable
private fun SlotHeaderContent(slot: PlanSlot, waiting: Boolean, freeClockText: String) {
    val title = stringResource(
        when (slot) {
            PlanSlot.BUILDER -> R.string.plan_builders
            PlanSlot.LAB -> R.string.plan_lab
            PlanSlot.PET -> R.string.plan_pets
            PlanSlot.BB_BUILDER -> R.string.plan_bb_builders
            PlanSlot.STAR_LAB -> R.string.plan_star_lab
        },
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ClashOutlinedText(
            text = title,
            fontSize = 18.sp,
            color = TavernGold,
            strokeWidth = 4.8f,
        )
        Text(
            if (waiting) stringResource(R.string.plan_next_free, freeClockText) else stringResource(R.string.plan_free_now),
            style = MaterialTheme.typography.bodyMedium,
            color = if (waiting) MaterialTheme.colorScheme.onSurfaceVariant else TavernGreenLight,
        )
    }
}

@Composable
private fun SlotSectionHeaderItem(slot: PlanSlot, waiting: Boolean, freeClockText: String) {
    val pal = LocalChiefPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SlotTopShape)
            .drawWithCache {
                cacheChiseledStoneSurface(pal = pal, highlighted = false, part = StoneCardPart.TOP)
            }
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        SlotHeaderContent(slot, waiting, freeClockText)
    }
}

@Composable
private fun CandidateSectionRow(name: String, item: PlanCandidate, doneText: String, dayUnit: String, isLast: Boolean) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(item.id, null, 44.dp)
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(bottom = if (isLast) 12.dp else 0.dp)
        .let { m -> if (isLast) m.clip(SlotBotShape) else m }
        .drawWithCache {
            cacheChiseledStoneSurface(
                pal = pal,
                highlighted = false,
                part = if (isLast) StoneCardPart.BOTTOM else StoneCardPart.MIDDLE,
            )
        }

    Column(modifier = rowModifier.padding(bottom = if (isLast) 4.dp else 0.dp)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(TavernGoldDark.copy(alpha = 0.25f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TavernIconFrame(size = 48.dp) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(38.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ClashOutlinedText(
                    text = "$name ➛ ${item.level}" + if (item.count > 1) " ×${item.count}" else "",
                    fontSize = 15.sp,
                    color = Color.White,
                    strokeWidth = 4.2f,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Bedtime.durationLabel(item.seconds, dayUnit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(doneText, style = MaterialTheme.typography.bodyMedium, color = TavernGoldLight)
                }
                if (item.cost > 0) CostChip(item.resource, item.cost)
            }
        }
    }
}

@DrawableRes
private fun resourceIcon(resource: Int): Int = when (resource) {
    1, 5 -> R.drawable.road_elixir
    2 -> R.drawable.road_dark_elixir
    else -> R.drawable.road_gold
}

@Composable
private fun CostChip(resource: Int, cost: Long) {
    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(pal.barTrackBg)
            .border(1.dp, pal.barTrackRim, shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(resourceIcon(resource)), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(compact(cost), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
