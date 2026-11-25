package com.snkb.chieftimer.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.AlchemistPlan
import com.snkb.chieftimer.core.AppState
import com.snkb.chieftimer.core.BookCandidate
import com.snkb.chieftimer.core.BookKind
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.CwlConflict
import com.snkb.chieftimer.core.GameEvents
import com.snkb.chieftimer.core.GameIds
import com.snkb.chieftimer.core.HelperRole
import com.snkb.chieftimer.core.PotionSlotType
import com.snkb.chieftimer.core.RoadToMaxCalc
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.StrategyPlanner
import com.snkb.chieftimer.core.TimerEngine
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.formatTimestamp
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

/** Compact status row for the village's Helpers (Apprentice Builder, Lab Assistant, Alchemist) with live recharge timer. */
@Composable
internal fun HelperCooldownRow(export: VillageExport?, now: Long) {
    val pal = LocalChiefPalette.current
    val dayUnit = stringResource(R.string.time_day_short)
    // Only follow the 1s clock while at least one helper is actively recharging; freeze at rest once all are Ready!
    val maxReadyAt = remember(export) {
        export?.helpers?.filter { it.level > 0 && it.cooldown > 0 }?.maxOfOrNull { export.timestamp + it.cooldown } ?: 0L
    }
    val allReady = now >= maxReadyAt
    val effectiveNow = remember(export, allReady) { if (allReady) maxReadyAt else now }.let { if (allReady) it else now }
    val statuses = remember(export, effectiveNow) { StrategyPlanner.helperStatuses(export, effectiveNow) }
    if (statuses.isEmpty()) return

    TavernStoneCard(contentPadding = PaddingValues(12.dp)) {
        ClashOutlinedText(
            text = stringResource(R.string.helpers_status_title),
            fontSize = 14.sp,
            color = pal.accentLight,
            strokeWidth = 3.8f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            statuses.forEach { h ->
                val titleRes = when (h.role) {
                    HelperRole.APPRENTICE -> R.string.helper_apprentice_short
                    HelperRole.LAB_ASSISTANT -> R.string.helper_lab_short
                    HelperRole.ALCHEMIST -> R.string.helper_alchemist_short
                }
                val bmp = rememberGameIcon(h.dataId, null, 24.dp)
                val ready = h.isReady
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.barTrackBg)
                        .border(1.dp, if (ready) TavernGreenLight.copy(alpha = 0.7f) else pal.barTrackRim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        if (bmp != null) {
                            Image(bmp, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = stringResource(R.string.helper_level_n, h.level),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (ready) stringResource(R.string.helper_ready_now)
                        else stringResource(R.string.helper_ready_in, Countdown.text(h.remainingCooldownSec, dayUnit)),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                        color = if (ready) TavernGreenLight else TavernGoldLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Collapsible 3D strategy workbench on the Timers page:
 * - Builder release timeline & collision detector + ideal upgrade duration hint
 * - 10x Potion simulator (+1..+3 potions) & Book value ranking
 * - Clan War League (CWL) conflict checker for Heroes and Offense buildings
 */
@Composable
internal fun TimerStrategyCard(
    model: WidgetModel,
    export: VillageExport?,
    appState: AppState,
    now: Long,
    cwlConflicts: List<CwlConflict>,
) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val dayUnit = stringResource(R.string.time_day_short)
    val nowMinute = now / 60L
    val names = remember { Names.get(context) }

    val builderAnalysis = remember(model, nowMinute) {
        val finishes = model.builderRows
            .filter { it.kind == RowKind.RUNNING && !it.goblin && it.finishAt != null }
            .map { it.finishAt!! }
        StrategyPlanner.analyzeBuilderSchedule(finishes, model.buildersTotal, nowMinute * 60L)
    }

    var open by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 Timeline, 1 Magic, 2 CWL

    val summaryParts = buildList {
        if (builderAnalysis.collisions.isNotEmpty()) {
            add(stringResource(R.string.strategy_collisions_short, builderAnalysis.collisions.size))
        }
        if (cwlConflicts.isNotEmpty()) {
            add(stringResource(R.string.strategy_cwl_short, cwlConflicts.size))
        }
    }
    val subtitle = if (summaryParts.isNotEmpty()) {
        summaryParts.joinToString(" · ")
    } else {
        stringResource(R.string.strategy_card_subtitle)
    }

    TavernStoneCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.wiggleOnTap()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ClashOutlinedText(
                    text = stringResource(R.string.strategy_card_title),
                    fontSize = 16.sp,
                    color = if (cwlConflicts.isNotEmpty() || builderAnalysis.collisions.isNotEmpty()) TavernGoldLight else pal.accentLight,
                    strokeWidth = 4.2f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (open) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (cwlConflicts.isNotEmpty() || builderAnalysis.collisions.isNotEmpty()) TavernGold else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TavernFilterPill(
                            label = stringResource(R.string.strategy_tab_builders),
                            selected = tab == 0,
                            onClick = { tab = 0 },
                        )
                        TavernFilterPill(
                            label = stringResource(R.string.strategy_tab_magic),
                            selected = tab == 1,
                            onClick = { tab = 1 },
                        )
                        TavernFilterPill(
                            label = if (cwlConflicts.isNotEmpty()) "${stringResource(R.string.strategy_tab_cwl)} (${cwlConflicts.size})"
                            else stringResource(R.string.strategy_tab_cwl),
                            selected = tab == 2,
                            onClick = { tab = 2 },
                        )
                        TavernFilterPill(
                            label = stringResource(R.string.strategy_tab_alchemist),
                            selected = tab == 3,
                            onClick = { tab = 3 },
                        )
                    }

                    when (tab) {
                        0 -> BuilderScheduleContent(builderAnalysis, now, dayUnit)
                        1 -> MagicSimulatorContent(export, appState, nowMinute * 60L, names, dayUnit)
                        2 -> CwlConflictsContent(cwlConflicts, dayUnit)
                        else -> AlchemistContent(export, nowMinute * 60L)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderScheduleContent(
    analysis: com.snkb.chieftimer.core.BuilderScheduleAnalysis,
    now: Long,
    dayUnit: String,
) {
    val pal = LocalChiefPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (analysis.freeNow > 0) {
            Text(
                text = stringResource(R.string.strategy_builders_free_now, analysis.freeNow),
                style = MaterialTheme.typography.bodyMedium,
                color = TavernGreenLight,
            )
        }
        if (analysis.collisions.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x44C87D12))
                    .border(1.dp, TavernGold.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.strategy_collision_header, analysis.collisions.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = TavernGoldLight,
                    )
                    analysis.collisions.take(3).forEach { c ->
                        Text(
                            text = stringResource(
                                R.string.strategy_collision_item,
                                formatTimestamp(c.firstAt),
                                formatTimestamp(c.secondAt),
                                Countdown.text(c.gapSec.coerceAtLeast(60L), dayUnit),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
            }
        } else if (analysis.sortedFinishes.size >= 2) {
            Text(
                text = stringResource(R.string.strategy_no_collisions),
                style = MaterialTheme.typography.bodySmall,
                color = TavernGreenLight,
            )
        }

        if (analysis.idealMinSec != null && analysis.idealMaxSec != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.barTrackBg)
                    .border(1.dp, pal.barTrackRim, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.strategy_ideal_duration,
                        Countdown.text(analysis.idealMinSec, dayUnit),
                        Countdown.text(analysis.idealMaxSec, dayUnit),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.accentLight,
                )
            }
        }

        analysis.sortedFinishes.forEachIndexed { idx, fin ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.strategy_builder_slot_n, idx + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${Countdown.text((fin - now).coerceAtLeast(0L), dayUnit)} · ${formatTimestamp(fin)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MagicSimulatorContent(
    export: VillageExport?,
    appState: AppState,
    snapshotNow: Long,
    names: com.snkb.chieftimer.core.NameMap,
    dayUnit: String,
) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    var slot by rememberSaveable { mutableStateOf(PotionSlotType.BUILDER) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    val sleepVersion = SleepStore.version
    val sleepWindow = remember(context, sleepVersion) { SleepStore.get(context) }

    val sim = remember(export, appState, snapshotNow, slot, count, sleepWindow) {
        StrategyPlanner.simulatePotions(
            export = export,
            engine = TimerEngine(),
            state = appState,
            now = snapshotNow,
            slotType = slot,
            potionCount = count,
            names = names,
            sleepWindow = sleepWindow,
        )
    }
    val books = remember(export, appState, snapshotNow) {
        StrategyPlanner.rankBookCandidates(
            export = export,
            engine = TimerEngine(),
            state = appState,
            now = snapshotNow,
            names = names,
        ).take(4)
    }
    val helperRoi = remember(export, appState, snapshotNow) {
        StrategyPlanner.helperRoiAnalysis(
            export = export,
            engine = TimerEngine(),
            state = appState,
            now = snapshotNow,
            names = names,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.strategy_potion_sim_title),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TavernFilterPill(
                label = stringResource(R.string.potion_builder),
                selected = slot == PotionSlotType.BUILDER,
                onClick = { slot = PotionSlotType.BUILDER },
            )
            TavernFilterPill(
                label = stringResource(R.string.potion_research),
                selected = slot == PotionSlotType.LAB,
                onClick = { slot = PotionSlotType.LAB },
            )
            TavernFilterPill(
                label = stringResource(R.string.potion_pet),
                selected = slot == PotionSlotType.PET,
                onClick = { slot = PotionSlotType.PET },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(1, 2, 3).forEach { n ->
                TavernFilterPill(label = "+$n", selected = count == n, onClick = { count = n })
            }
        }
        if (sim.isEmpty()) {
            Text(
                text = stringResource(R.string.strategy_sim_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sim.forEach { item ->
                val finishesQuickly = item.afterRemainingSec <= 12 * 3600L
                val borderColor = when {
                    item.finishesDuringSleep -> TavernGold.copy(alpha = 0.85f)
                    finishesQuickly -> TavernGreenLight.copy(alpha = 0.65f)
                    else -> pal.barTrackRim
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pal.barTrackBg)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "${item.name} ➛ ${item.levelLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.finishesDuringSleep) {
                            Text(
                                text = stringResource(R.string.strategy_potion_sleep_warn),
                                style = MaterialTheme.typography.labelSmall,
                                color = TavernGoldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = "${Countdown.text(item.beforeRemainingSec, dayUnit)} → ${Countdown.text(item.afterRemainingSec, dayUnit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (finishesQuickly) TavernGreenLight else TavernGoldLight,
                    )
                }
            }
        }

        if (books.isNotEmpty()) {
            Text(
                text = stringResource(R.string.strategy_book_ranking_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            books.forEachIndexed { idx, b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pal.barTrackBg)
                        .border(1.dp, pal.barTrackRim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "#${idx + 1} ${b.name} ➛ ${b.targetLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(bookKindRes(b)),
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.accentLight,
                        )
                    }
                    Text(
                        text = stringResource(R.string.strategy_saves_time, Countdown.text(b.remainingSec, dayUnit)),
                        style = MaterialTheme.typography.labelMedium,
                        color = TavernGoldLight,
                    )
                }
            }
        }

        if (helperRoi.isNotEmpty()) {
            Text(
                text = stringResource(R.string.helper_roi_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            helperRoi.forEach { info ->
                val roleLabel = stringResource(
                    if (info.role == HelperRole.APPRENTICE) R.string.helper_apprentice_short else R.string.helper_lab_short,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pal.barTrackBg)
                        .border(1.dp, pal.barTrackRim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.helper_roi_line,
                            roleLabel,
                            info.level,
                            info.hoursSavedPerDay,
                            info.hoursSavedPerWeek,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    if (!info.bestJobName.isNullOrBlank() && info.bestJobSavedHours > 0) {
                        Text(
                            text = stringResource(R.string.helper_roi_best, info.bestJobName, info.bestJobSavedHours),
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.accentLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.helper_goblin_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun bookKindRes(b: BookCandidate): Int = when (b.bookKind) {
    BookKind.HEROES -> R.string.book_heroes
    BookKind.FIGHTING -> R.string.book_fighting
    BookKind.SPELLS -> R.string.book_spells
    BookKind.BUILDING -> R.string.book_building
}

@Composable
private fun CwlConflictsContent(conflicts: List<CwlConflict>, dayUnit: String) {
    val pal = LocalChiefPalette.current
    if (conflicts.isEmpty()) {
        Text(
            text = stringResource(R.string.strategy_cwl_clear),
            style = MaterialTheme.typography.bodySmall,
            color = TavernGreenLight,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.strategy_cwl_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        conflicts.forEach { c ->
            val msg = if (c.cwlActiveNow) {
                stringResource(R.string.cwl_badge_active, Countdown.text(c.overlapSec, dayUnit))
            } else {
                stringResource(R.string.cwl_badge_overlap, Countdown.text(c.overlapSec, dayUnit))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.barTrackBg)
                    .border(1.dp, if (c.isHero) TavernGold else pal.barTrackRim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "${c.name} ➛ ${c.targetLevel}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatTimestamp(c.finishAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (c.isHero) TavernGoldLight else pal.accentLight,
                )
            }
        }
    }
}

@Composable
internal fun rememberCwlConflicts(
    export: VillageExport?,
    appState: AppState,
    now: Long,
): List<CwlConflict> {
    val context = LocalContext.current
    val names = remember { Names.get(context) }
    val nowMinute = now / 60L
    val events = remember(nowMinute) {
        GameEvents.upcoming(nowMinute * 60L, EventsStore.config, ClashKingDatesStore.dates)
    }
    return remember(export, appState, events, nowMinute) {
        StrategyPlanner.findCwlConflicts(export, TimerEngine(), appState, nowMinute * 60L, events, names)
    }
}

@Composable
private fun AlchemistContent(export: VillageExport?, snapshotNow: Long) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val dataVersion = MaxDataStore.version
    val road = remember(export, dataVersion) {
        export?.let { RoadToMaxCalc.compute(MaxDataStore.get(context), it) }
    }
    val plan: AlchemistPlan? = remember(export, road, snapshotNow) {
        StrategyPlanner.alchemistAnalysis(export, road, snapshotNow)
    }
    if (plan == null) {
        Text(
            text = stringResource(R.string.alchemist_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bmp = rememberGameIcon(GameIds.HELPER_ALCHEMIST, null, 28.dp)
    val nf = remember { NumberFormat.getIntegerInstance() }
    var fromRes by rememberSaveable { mutableIntStateOf(if (plan.suggestedTargetResource == 0) 1 else 0) }
    var toRes by rememberSaveable { mutableIntStateOf(plan.suggestedTargetResource) }
    var fractionPct by rememberSaveable { mutableIntStateOf(100) }
    if (fromRes == toRes) toRes = (fromRes + 1) % 3

    fun resLabel(r: Int): Int = when (r) {
        1 -> R.string.resource_elixir
        2 -> R.string.resource_dark_elixir
        else -> R.string.resource_gold
    }

    val capForTarget = if (toRes == 2) plan.maxDarkElixir else plan.maxGoldElixir
    val simInput = (capForTarget * fractionPct) / 100L
    val displayedInput = when {
        fromRes == 2 && toRes != 2 -> (plan.maxDarkElixir * fractionPct) / 100L
        fromRes != 2 && toRes == 2 -> (plan.maxGoldElixir * fractionPct) / 100L
        else -> simInput
    }
    val outputAmount = plan.convertWithBonus(simInput)
    val freeBonus = (outputAmount - simInput).coerceAtLeast(0L)
    val targetLeft = when (plan.suggestedTargetResource) {
        1 -> plan.elixirLeft
        2 -> plan.darkLeft
        else -> plan.goldLeft
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bmp != null) Image(bmp, null, filterQuality = FilterQuality.High, modifier = Modifier.size(26.dp))
            Text(
                text = stringResource(R.string.alchemist_header, plan.level, plan.bonusPercent),
                style = MaterialTheme.typography.titleSmall,
                color = TavernGoldLight,
            )
        }
        Text(
            text = stringResource(R.string.alchemist_capacity_line, nf.format(plan.maxGoldElixir), nf.format(plan.maxDarkElixir)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (targetLeft > 0L) {
            Text(
                text = stringResource(
                    R.string.alchemist_advice,
                    stringResource(resLabel(plan.suggestedTargetResource)),
                    nf.format(targetLeft),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = pal.accentLight,
            )
        }
        Text(
            text = stringResource(R.string.alchemist_sim_title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alchemist_source_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0, 1, 2).forEach { r ->
                TavernFilterPill(
                    label = stringResource(resLabel(r)),
                    selected = fromRes == r,
                    onClick = {
                        fromRes = r
                        if (toRes == r) toRes = (r + 1) % 3
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alchemist_target_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0, 1, 2).filter { it != fromRes }.forEach { r ->
                TavernFilterPill(
                    label = stringResource(resLabel(r)),
                    selected = toRes == r,
                    onClick = { toRes = r },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(25, 50, 100).forEach { pct ->
                TavernFilterPill(
                    label = "$pct%",
                    selected = fractionPct == pct,
                    onClick = { fractionPct = pct },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(pal.barTrackBg)
                .border(1.dp, if (plan.isReady) TavernGreenLight.copy(alpha = 0.6f) else pal.barTrackRim, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.alchemist_result,
                    "${nf.format(displayedInput)} ${stringResource(resLabel(fromRes))}",
                    "${nf.format(outputAmount)} ${stringResource(resLabel(toRes))}",
                    nf.format(freeBonus),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (plan.isReady) TavernGreenLight else Color.White,
            )
        }
    }
}
