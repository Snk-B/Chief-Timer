@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.AccountMeta
import com.snkb.chieftimer.core.ActiveBoostState
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.EventSettings
import com.snkb.chieftimer.core.GoldPassLogic
import com.snkb.chieftimer.core.RowButton
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.SyncLogEntry
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.formatTimestamp
import com.snkb.chieftimer.sync.ClipboardAutoSync
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.sync.SyncRunner
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import kotlinx.coroutines.launch

@Composable
fun TimerDashboard(
    model: WidgetModel,
    lastSync: SyncLogEntry?,
    activeBoostState: ActiveBoostState,
    eventSettings: EventSettings,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    nowState: State<Long>,
    busy: Boolean,
    onSync: () -> Unit,
    onUpdateBoosts: ((ActiveBoostState) -> ActiveBoostState) -> Unit,
    onUpdateEvents: ((EventSettings) -> EventSettings) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    export: VillageExport? = null,
    appState: com.snkb.chieftimer.core.AppState = com.snkb.chieftimer.core.AppState(),
) {
    var filter by rememberSaveable { mutableStateOf(TimerFilter.ALL) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val maxData = remember(com.snkb.chieftimer.data.MaxDataStore.version) {
        com.snkb.chieftimer.data.MaxDataStore.get(context)
    }
    val minuteNow by remember(nowState) { derivedStateOf { (nowState.value / 60L) * 60L } }
    val timers = remember(model, filter) { model.timers(filter) }
    val cwlConflicts = rememberCwlConflicts(export, appState, minuteNow)
    val cwlConflictByKey = remember(cwlConflicts) { cwlConflicts.associateBy { it.jobKey } }
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (wide) 2 else 1),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (model.hasData) {
            item(key = "village_command_banner", span = { GridItemSpan(maxLineSpan) }) {
                TavernVillageCommandBanner(
                    model = model,
                    nowState = nowState,
                    lastSync = lastSync,
                    lastDiff = appState.lastDiff,
                    busy = busy,
                    onSync = onSync,
                )
            }
        } else {
            item(key = "welcome_tavern", span = { GridItemSpan(maxLineSpan) }) {
                TavernWelcomeBanner(
                    lastSync = lastSync,
                    busy = busy,
                    onSync = onSync,
                    onShowHelp = { showHelp = true },
                )
            }
        }

        item(key = "gold_pass_banner", span = { GridItemSpan(maxLineSpan) }) {
            GoldPassBanner(selectedTag, minuteNow / 3600L * 3600L)
        }

        if (model.hasData) {
            item(key = "stat_shields", span = { GridItemSpan(maxLineSpan) }) {
                TavernStatShieldsRow(model, nowState)
            }

            if (export != null && export.helpers.any { it.level > 0 }) {
                item(key = "helper_cooldowns", span = { GridItemSpan(maxLineSpan) }) {
                    HelperCooldownRow(export, nowState.value)
                }
            }

            item(key = "timer_strategy", span = { GridItemSpan(maxLineSpan) }) {
                TimerStrategyCard(
                    model = model,
                    export = export,
                    appState = appState,
                    now = minuteNow,
                    cwlConflicts = cwlConflicts,
                )
            }

            item(key = "upgrades_header", span = { GridItemSpan(maxLineSpan) }) {
                UpgradesHeader(
                    filter = filter,
                    onFilter = { filter = it },
                    calendar = calendar,
                    onCalendar = { calendar = it },
                )
            }

            if (calendar) {
                item(key = "calendar_timeline", span = { GridItemSpan(maxLineSpan) }) {
                    TimerCalendar(model = model, export = export, now = minuteNow, filter = filter)
                }
            } else {
                if (timers.isEmpty()) {
                    item(key = "empty_filter", span = { GridItemSpan(maxLineSpan) }) {
                        TavernStoneCard {
                            Text(
                                stringResource(R.string.no_timers_in, stringResource(filter.labelRes)),
                                modifier = Modifier.padding(6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(timers, key = { it.row.jobKey ?: "${it.category}_${it.row.name}" }) { timer ->
                    TavernTimerCard(timer, nowState, model.townHall, maxData, timer.row.jobKey?.let { cwlConflictByKey[it] })
                }
            }
        }
    }

    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
}

private fun com.snkb.chieftimer.core.WidgetRow.liveRemaining(now: Long): Long =
    if (kind == RowKind.RUNNING && finishAt != null) (finishAt - now).coerceAtLeast(0L) else remaining

private fun com.snkb.chieftimer.core.WidgetRow.liveProgress(liveRem: Long): Float {
    if (kind != RowKind.RUNNING || finishAt == null) return progress
    if (remaining <= 0L || progress >= 0.999f) return progress
    val estTotal = remaining.toFloat() / (1f - progress)
    if (estTotal <= 1f) return progress
    return (1f - (liveRem.toFloat() / estTotal)).coerceIn(progress, 0.999f)
}

@Composable
private fun TavernVillageCommandBanner(
    model: WidgetModel,
    nowState: State<Long>,
    lastSync: SyncLogEntry?,
    lastDiff: com.snkb.chieftimer.core.SyncDiff?,
    busy: Boolean,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    val failed = lastSync?.ok == false
    var showDetails by rememberSaveable { mutableStateOf(false) }

    val pal = LocalChiefPalette.current
    val thBitmap = rememberTownHallIcon(model.townHall, 80.dp, minPx = 256)

    val dayUnit = stringResource(R.string.time_day_short)
    val now = nowState.value
    val builderTotal = model.builderRows.filter { it.kind == RowKind.RUNNING }.sumOf { it.liveRemaining(now) }
    val labTotal = (listOf(model.labRow) + model.extraLabRows).filter { it.kind == RowKind.RUNNING }.sumOf { it.liveRemaining(now) }

    TavernWoodBanner {
        // Top Row: 3D Town Hall Icon + Village Title & Total Work Left
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (thBitmap != null) {
                Image(
                    bitmap = thBitmap,
                    contentDescription = null,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val titleText = buildString {
                    append(model.playerName)
                    model.townHall?.let {
                        append(" · ")
                        append(stringResource(R.string.town_hall, it))
                    }
                }
                ClashOutlinedText(
                    text = titleText,
                    fontSize = 19.sp,
                    color = Color.White,
                    strokeWidth = 5.5f,
                    maxLines = 1,
                )
                val workSummary = listOfNotNull(
                    builderTotal.takeIf { it > 0 }?.let { stringResource(R.string.total_builder_time, Countdown.text(it, dayUnit)) },
                    labTotal.takeIf { it > 0 }?.let { stringResource(R.string.total_lab_time, Countdown.text(it, dayUnit)) },
                    if (model.goblinWorking) stringResource(R.string.goblin_working) else null,
                ).joinToString(" · ")
                if (workSummary.isNotEmpty()) {
                    ClashOutlinedText(
                        text = workSummary,
                        fontSize = 12.sp,
                        color = pal.bannerSubText,
                        strokeWidth = 3.6f,
                        maxLines = 2,
                    )
                }
                if (lastDiff != null && lastDiff.hasChanges) {
                    val parts = listOfNotNull(
                        lastDiff.started.takeIf { it > 0 }?.let { stringResource(R.string.diff_started, it) },
                        lastDiff.finished.takeIf { it > 0 }?.let { stringResource(R.string.diff_finished, it) },
                        lastDiff.levelsGained.takeIf { it > 0 }?.let { stringResource(R.string.diff_levels, it) },
                        lastDiff.wallsUpgraded.takeIf { it > 0 }?.let { stringResource(R.string.diff_walls, it) },
                    ).joinToString(" · ")
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TavernGreenLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Middle Metallic Ribbon: Next Finish
        model.nextRunningTimer()?.let { next ->
            val ribbonShape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ribbonShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(pal.ribbonEdge, pal.ribbonCenter, pal.ribbonEdge),
                        ),
                    )
                    .border(1.dp, TavernGold.copy(alpha = 0.78f), ribbonShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                ClashOutlinedText(
                    text = stringResource(
                        R.string.next_finish,
                        next.row.name,
                        Countdown.text(next.row.liveRemaining(now), dayUnit),
                    ),
                    fontSize = 13.sp,
                    color = pal.ribbonText,
                    strokeWidth = 4f,
                    maxLines = 1,
                )
            }
        }

        // Bottom Row: Glossy 3D Green Sync Button (hidden when Auto-Sync is active without error) + Beveled Stone "Detalhes" Button
        val showSync = !ClipboardAutoSync.enabled(context) || failed
        if (showSync || lastSync != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSync) {
                    Clash3DButton(
                        onClick = onSync,
                        enabled = !busy,
                        style = ClashButtonStyle.GREEN,
                        minHeight = 46.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy) {
                            LoadingIndicator(Modifier.size(20.dp), color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_sync),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        ClashOutlinedText(
                            text = stringResource(
                                if (busy) R.string.syncing else if (failed) R.string.sync_retry else R.string.sync_from_clipboard,
                            ),
                            fontSize = 14.sp,
                            color = Color.White,
                            strokeWidth = 4.5f,
                            maxLines = 2,
                        )
                    }
                }

                if (lastSync != null) {
                    Clash3DButton(
                        onClick = { showDetails = !showDetails },
                        style = ClashButtonStyle.STONE,
                        minHeight = 46.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        modifier = if (showSync) Modifier else Modifier.weight(1f),
                    ) {
                        AnimatedContent(
                            targetState = showDetails,
                            transitionSpec = {
                                (slideInVertically(tween(200)) { h -> if (targetState) h / 2 else -h / 2 } + fadeIn(tween(200))) togetherWith
                                    (slideOutVertically(tween(160)) { h -> if (targetState) -h / 2 else h / 2 } + fadeOut(tween(150)))
                            },
                            label = "detailsBtnText",
                        ) { open ->
                            ClashOutlinedText(
                                text = stringResource(if (open) R.string.hide_details else R.string.sync_details),
                                fontSize = 13.5.sp,
                                color = Color.White,
                                strokeWidth = 4.2f,
                            )
                        }
                    }
                }
            }
        }

        // Sync error or smoothly animated sync log details
        AnimatedVisibility(
            visible = failed,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f)) + fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(160)),
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA4D1010))
                    .border(1.dp, Color(0xFFFF7A7A), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.sync_failed),
                        fontSize = 13.sp,
                        color = Color(0xFFFFB4B4),
                        strokeWidth = 3.8f,
                    )
                    lastSync?.let {
                        SelectionContainer {
                            Text(SyncRunner.plainText(it.message), style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                    Text(stringResource(R.string.sync_retry_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFE2E2))
                }
            }
        }

        AnimatedVisibility(
            visible = !failed && lastSync != null && showDetails,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f)) + fadeIn(tween(230)),
            exit = shrinkVertically(animationSpec = tween(210)) + fadeOut(tween(160)),
        ) {
            if (lastSync != null) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pal.barTrackBg.copy(alpha = 0.72f))
                        .border(1.dp, pal.accent.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${stringResource(R.string.sync_last)}: ${formatTimestamp(lastSync.at)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TavernGoldLight,
                        )
                        SelectionContainer {
                            Text(SyncRunner.plainText(lastSync.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(R.string.sync_copy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TavernWelcomeBanner(
    lastSync: SyncLogEntry?,
    busy: Boolean,
    onSync: () -> Unit,
    onShowHelp: () -> Unit,
) {
    val failed = lastSync?.ok == false
    TavernWoodBanner {
        ClashOutlinedText(
            text = stringResource(R.string.welcome_title),
            fontSize = 21.sp,
            color = TavernGoldLight,
            strokeWidth = 5.5f,
        )
        Text(stringResource(R.string.welcome_sub), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text(stringResource(R.string.welcome_step1), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF0D4))
        Text(stringResource(R.string.welcome_step2), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF0D4))
        Text(stringResource(R.string.welcome_step3), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF0D4))
        Text(stringResource(R.string.welcome_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (lastSync != null && lastSync.ok == false) {
            Text(SyncRunner.plainText(lastSync.message), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4B4))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clash3DButton(
                onClick = onSync,
                enabled = !busy,
                style = ClashButtonStyle.GREEN,
                modifier = Modifier.weight(1f),
            ) {
                ClashOutlinedText(
                    text = stringResource(if (busy) R.string.syncing else R.string.sync_from_clipboard),
                    fontSize = 14.sp,
                    color = Color.White,
                    strokeWidth = 4.5f,
                )
            }
            Clash3DButton(
                onClick = onShowHelp,
                style = ClashButtonStyle.STONE,
            ) {
                ClashOutlinedText(
                    text = stringResource(R.string.help_link),
                    fontSize = 13.sp,
                    color = Color.White,
                    strokeWidth = 4f,
                )
            }
        }
    }
}

@Composable
private fun TavernStatShieldsRow(model: WidgetModel, nowState: State<Long>) {
    val now = nowState.value
    val dayUnit = stringResource(R.string.time_day_short)
    val nextFree = if (model.buildersBusy < model.buildersTotal) {
        stringResource(R.string.stat_free_now)
    } else {
        model.builderRows.filter { it.kind == RowKind.RUNNING && !it.goblin }.minOfOrNull { it.liveRemaining(now) }
            ?.let { Countdown.text(it, dayUnit) } ?: "—"
    }
    val labRunning = (listOf(model.labRow) + model.extraLabRows).filter { it.kind == RowKind.RUNNING }
    val nextLab = if (labRunning.isEmpty()) {
        stringResource(R.string.stat_free_now)
    } else {
        Countdown.text(labRunning.minOf { it.liveRemaining(now) }, dayUnit)
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TavernStatShield(
            crestRes = R.drawable.ic_tavern_crest_hammer,
            label = "${stringResource(R.string.stat_builders)}:",
            value = "${model.buildersBusy}/${model.buildersTotal}",
            modifier = Modifier.weight(1f),
        )
        TavernStatShield(
            crestRes = R.drawable.ic_tavern_crest_clock,
            label = "${stringResource(R.string.stat_next_free)}:",
            value = nextFree,
            modifier = Modifier.weight(1f),
        )
        TavernStatShield(
            crestRes = R.drawable.ic_tavern_crest_elixir,
            label = "${stringResource(R.string.stat_next_lab)}:",
            value = nextLab,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TavernTimerCard(
    timer: DashboardTimer,
    nowState: State<Long>,
    townHall: Int?,
    maxData: com.snkb.chieftimer.core.MaxData,
    cwlConflict: com.snkb.chieftimer.core.CwlConflict? = null,
) {
    val pal = LocalChiefPalette.current
    val row = timer.row

    val icon = rememberFirstGameIcon(row.iconIds, row.iconLevel, 52.dp)
    val goblinBmp = rememberNamedGameIcon("goblin", 18.dp, enabled = row.goblin)
    // Highest level this item can reach at the current Town Hall (home village items only).
    val maxLevel = remember(row.iconIds, row.iconLevel, townHall, maxData) {
        val level = row.iconLevel
        val th = townHall
        if (level == null || th == null || timer.category == TimerFilter.BUILDER_BASE) null
        else row.iconIds.firstNotNullOfOrNull { maxData.items[it] }
            ?.let { item -> item.steps.indexOfLast { it.townHall <= th } + 1 }
            ?.takeIf { it > 0 }
    }
    val finishDateText = remember(row.finishAt) { row.finishAt?.let { formatTimestamp(it) } }

    val categoryLabel = when (timer.category) {
        TimerFilter.BUILDER_BASE -> stringResource(R.string.filter_bb)
        TimerFilter.BUILDERS -> stringResource(if (row.goblin) R.string.cat_goblin_builder else R.string.cat_home_builder)
        TimerFilter.LAB -> stringResource(R.string.cat_lab)
        TimerFilter.PETS -> stringResource(R.string.cat_pets)
        else -> stringResource(timer.category.labelRes)
    }

    val barTone = when (timer.category) {
        TimerFilter.LAB -> TavernBarTone.ELIXIR
        TimerFilter.PETS -> TavernBarTone.GOLD
        else -> TavernBarTone.GREEN
    }

    TavernStoneCard(contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left: Beveled Golden Square Frame with 3D Building/Troop/Pet Icon
            TavernIconFrame(size = 62.dp) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                    )
                } else {
                    ClashOutlinedText(
                        text = row.initials,
                        fontSize = 18.sp,
                        color = TavernGoldLight,
                    )
                }
            }

            // Right: Category, Title + Countdown, Wavy Tube Progress Bar, % & Completion Time
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Category label (+ Goblin icon if goblin builder)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (row.goblin) TavernGreenLight else TavernGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (goblinBmp != null) {
                        Image(bitmap = goblinBmp, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                // Upgrade Name -> Level (left) + Bold Countdown (right, isolated 1s leaf)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ClashOutlinedText(
                        text = row.label.replace(" → ", " ➛ "),
                        fontSize = 16.sp,
                        color = Color.White,
                        strokeWidth = 4.6f,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                    )
                    LiveCardCountdownText(row, nowState)
                }

                if (maxLevel != null && row.iconLevel != null) {
                    Text(
                        text = stringResource(
                            if (row.iconLevel >= maxLevel) R.string.timer_reaches_max else R.string.timer_max_level,
                            row.iconLevel,
                            maxLevel,
                            townHall ?: 0,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Glossy Recessed Wavy Tube Bar + % complete (isolated 1s leaf)
                if (row.kind != RowKind.IDLE) {
                    LiveCardProgressBlock(
                        row = row,
                        nowState = nowState,
                        barTone = barTone,
                        finishDateText = finishDateText,
                    )
                }

                if (row.kind != RowKind.RUNNING) {
                    Text(
                        stringResource(if (row.kind == RowKind.COMPLETED) R.string.row_completed_hint else R.string.row_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Assigned Helper Badge + CWL Overlap Warning aligned cleanly at bottom-end
        if (row.kind == RowKind.RUNNING) {
            val helper = when (row.button) {
                RowButton.APPRENTICE -> stringResource(R.string.apprentice_assigned)
                RowButton.LAB_ASSISTANT -> stringResource(R.string.assistant_assigned)
                else -> null
            }
            if (helper != null || cwlConflict != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    if (cwlConflict != null) {
                        val badgeShape = RoundedCornerShape(9.dp)
                        val dayUnit = stringResource(R.string.time_day_short)
                        val msg = if (cwlConflict.cwlActiveNow) {
                            stringResource(R.string.cwl_badge_active, Countdown.text(cwlConflict.overlapSec, dayUnit))
                        } else {
                            stringResource(R.string.cwl_badge_overlap, Countdown.text(cwlConflict.overlapSec, dayUnit))
                        }
                        Box(
                            modifier = Modifier
                                .clip(badgeShape)
                                .background(Color(0x55C87D12))
                                .border(1.dp, TavernGold, badgeShape)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TavernGoldLight,
                                maxLines = 1,
                            )
                        }
                    }
                    if (helper != null) {
                        val badgeShape = RoundedCornerShape(9.dp)
                        Box(
                            modifier = Modifier
                                .clip(badgeShape)
                                .background(Brush.horizontalGradient(listOf(pal.ribbonEdge, pal.ribbonCenter)))
                                .border(1.dp, TavernGold.copy(alpha = 0.72f), badgeShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = helper,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TavernGoldLight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCardCountdownText(row: com.snkb.chieftimer.core.WidgetRow, nowState: State<Long>) {
    val text = if (row.kind == RowKind.RUNNING) {
        Countdown.text(row.liveRemaining(nowState.value), stringResource(R.string.time_day_short))
    } else {
        row.staticStatus
    }
    ClashOutlinedText(
        text = text,
        fontSize = 17.sp,
        color = Color.White,
        strokeWidth = 5f,
        maxLines = 1,
        tabularNums = true,
    )
}

@Composable
private fun LiveCardProgressBlock(
    row: com.snkb.chieftimer.core.WidgetRow,
    nowState: State<Long>,
    barTone: TavernBarTone,
    finishDateText: String?,
) {
    val pal = LocalChiefPalette.current
    val liveProgress = if (row.kind == RowKind.RUNNING) {
        row.liveProgress(row.liveRemaining(nowState.value))
    } else {
        row.progress
    }
    TavernWavyTubeBar(
        progress = liveProgress,
        tone = barTone,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.percent_complete, (liveProgress.coerceIn(0f, 1f) * 100).toInt()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (row.kind == RowKind.RUNNING && row.finishAt != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.barTrackBg)
                    .border(1.dp, pal.barTrackRim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = stringResource(R.string.finishes_at, finishDateText ?: ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A dismissible nudge on Day 4 / Day 8 when a Gold Pass player may have unlocked a bigger builder boost. */
@Composable
private fun GoldPassBanner(tag: String?, now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    val month = GoldPassLogic.monthKey(now)
    val day = GoldPassLogic.dayOfSeason(now)
    val answer = remember(tag, GoldPassStore.version) { GoldPassStore.answer(context, tag) }
    val dismissed = remember(tag, month, GoldPassStore.version) {
        GoldPassStore.dismissedTarget(context, tag, month)
    }
    val target = if (answer.month == month) {
        GoldPassLogic.bannerTarget(day, answer.gold, answer.builder)?.takeIf { it > dismissed }
    } else null
    if (target == null) return

    TavernStoneCard(highlighted = true) {
        Text(
            stringResource(R.string.gold_pass_banner, day, target),
            style = MaterialTheme.typography.bodyMedium,
            color = TavernGoldLight,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Clash3DButton(onClick = { open = true }, style = ClashButtonStyle.GOLD, minHeight = 42.dp) {
                ClashOutlinedText(stringResource(R.string.gold_pass_banner_update), fontSize = 13.5.sp)
            }
            TextButton(onClick = { GoldPassStore.dismissBanner(context, tag, month, target) }) {
                Text(stringResource(R.string.gold_pass_banner_dismiss), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (open) {
        GoldPassDialog(
            initial = answer,
            laterLabel = stringResource(android.R.string.cancel),
            onSave = { gold, b, r ->
                GoldPassStore.save(context, tag, month, gold, b, r)
                open = false
                scope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            },
            onLater = { open = false },
        )
    }
}
