package com.snkb.chieftimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Bedtime
import com.snkb.chieftimer.core.GameEvents
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.notify.EventAlerts
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import com.snkb.chieftimer.update.ClashKingDatesStore
import com.snkb.chieftimer.update.DataUpdater
import com.snkb.chieftimer.update.EventsStore
import com.snkb.chieftimer.update.RemoteIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The picture for an event card. Events that came from the downloaded list (not one of ours) get the trophy medal. */
internal fun eventIcon(id: String): Int = when (id) {
    GameEvents.CLAN_GAMES -> R.drawable.ev_clan_games
    GameEvents.RAID_WEEKEND -> R.drawable.ev_raid
    GameEvents.CWL_SIGNUP -> R.drawable.ev_cwl_signup
    GameEvents.CWL_WARS -> R.drawable.ev_cwl_wars
    GameEvents.LEAGUE_RESET -> R.drawable.ev_league
    else -> R.drawable.ev_season
}

/** Clash > Events styled in Taverna Clash / Taverna Escura (AMOLED): live local-time countdowns + ClashKing dates. */
@Composable
internal fun EventsPage(now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            EventsStore.load(context)
            ClashKingDatesStore.load(context)
        }
    }
    val config = EventsStore.config
    val dates = ClashKingDatesStore.dates
    val events = remember(now / 30, config, dates) { GameEvents.upcoming(now, config, dates) }
    val syncedAt = maxOf(EventsStore.updatedAt, ClashKingDatesStore.updatedAt)
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(EventsStore.auto(context)) }
    val zone = remember { ZoneId.systemDefault() }
    val format = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    fun local(epoch: Long) = format.format(Instant.ofEpochSecond(epoch).atZone(zone))
    val dayUnit = stringResource(R.string.time_day_short)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { e ->
            val name = e.name ?: stringResource(eventName(e.id))
            val left = Bedtime.durationLabel((e.target - now).coerceAtLeast(0), dayUnit)
            TavernStoneCard(highlighted = e.active) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TavernIconFrame(size = 58.dp) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(eventIcon(e.id)),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).fillMaxSize(),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ClashOutlinedText(
                            text = name,
                            fontSize = 15.5.sp,
                            color = if (e.active) TavernGoldLight else Color.White,
                            strokeWidth = 4.2f,
                            maxLines = 2,
                        )
                        ClashOutlinedText(
                            text = when {
                                e.point -> stringResource(R.string.clash_ev_in, left)
                                e.active -> stringResource(R.string.clash_ev_ends_in, left)
                                else -> stringResource(R.string.clash_ev_starts_in, left)
                            },
                            fontSize = 21.sp,
                            color = if (e.active) TavernGreenLight else TavernGold,
                            strokeWidth = 5f,
                            tabularNums = true,
                            maxLines = 1,
                        )
                        Text(
                            if (e.point) local(e.endsAt) else stringResource(R.string.clash_ev_range, local(e.startsAt), local(e.endsAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            TavernStoneCard {
                Text(
                    stringResource(R.string.clash_ev_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (syncedAt > 0) stringResource(R.string.clash_ev_updated, local(syncedAt / 1000))
                    else stringResource(R.string.clash_ev_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                refreshError?.let {
                    Text(
                        stringResource(R.string.update_failed, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SwitchRow(stringResource(R.string.clash_ev_auto), auto) {
                    auto = it
                    EventsStore.setAuto(context, it)
                }
                Text(
                    stringResource(R.string.ev_settings_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Clash3DButton(
                    enabled = !refreshing,
                    onClick = {
                        refreshing = true
                        refreshError = null
                        scope.launch {
                            val schedule = runCatching { EventsStore.refresh(context) }
                            val clashKing = runCatching { ClashKingDatesStore.refresh(context) }
                            EventAlerts.schedule(context)
                            if (schedule.isFailure && clashKing.isFailure) {
                                val err = clashKing.exceptionOrNull() ?: schedule.exceptionOrNull()
                                refreshError = err?.message ?: err?.javaClass?.simpleName
                            }
                            refreshing = false
                        }
                    },
                    style = ClashButtonStyle.GOLD,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ClashOutlinedText(
                        text = stringResource(if (refreshing) R.string.update_checking else R.string.clash_ev_refresh),
                        fontSize = 14.sp,
                        strokeWidth = 4.2f,
                    )
                }
            }
        }
    }
}

/** Keeps the event schedule fresh: refreshes quietly when the app opens. */
@Composable
internal fun EventsAutoRefresh() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            EventsStore.refreshIfDue(context)
            ClashKingDatesStore.refreshIfDue(context)
        }
    }
}

internal fun eventName(id: String): Int = when (id) {
    GameEvents.CLAN_GAMES -> R.string.clash_ev_clan_games
    GameEvents.RAID_WEEKEND -> R.string.clash_ev_raid
    GameEvents.CWL_SIGNUP -> R.string.clash_ev_cwl_signup
    GameEvents.CWL_WARS -> R.string.clash_ev_cwl_wars
    GameEvents.SEASON_END -> R.string.clash_ev_season_end
    else -> R.string.clash_ev_league_reset
}

private sealed interface DataCheck {
    data object Idle : DataCheck
    data object Checking : DataCheck
    data object Same : DataCheck
    data class Newer(val text: String, val townHall: Int, val items: Int) : DataCheck
    data class Failed(val message: String) : DataCheck
}

/** Settings card for ClashKing game data updates & automatic HD icon sync. */
@Composable
internal fun GameDataCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = MaxDataStore.version
    var loadedMeta by remember { mutableStateOf<Pair<com.snkb.chieftimer.core.MaxData, Boolean>?>(null) }
    LaunchedEffect(version) {
        loadedMeta = withContext(Dispatchers.IO) {
            MaxDataStore.get(context) to MaxDataStore.isDownloaded(context)
        }
    }
    val data = loadedMeta?.first
    val downloaded = loadedMeta?.second == true
    var check by remember { mutableStateOf<DataCheck>(DataCheck.Idle) }
    var auto by remember { mutableStateOf(DataUpdater.auto(context)) }

    SectionCard(stringResource(R.string.clash_data_title)) {
        Text(
            stringResource(R.string.clash_data_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data != null) {
            Text(
                stringResource(
                    if (downloaded) R.string.clash_data_current_downloaded else R.string.clash_data_current_builtin,
                    data.maxTownHall,
                    data.items.size,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = TavernGoldLight,
            )
        }
        when (val c = check) {
            is DataCheck.Newer -> {
                Text(
                    stringResource(R.string.clash_data_newer, c.townHall, c.items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TavernGreenLight,
                )
                Clash3DButton(
                    onClick = {
                        if (MaxDataStore.install(context, c.text)) {
                            check = DataCheck.Idle
                            scope.launch { com.snkb.chieftimer.sync.SyncRunner.refreshWidgets(context, "app") }
                        } else {
                            check = DataCheck.Failed(context.getString(R.string.clash_data_install_failed))
                        }
                    },
                    style = ClashButtonStyle.GREEN,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ClashOutlinedText(stringResource(R.string.clash_data_install), fontSize = 14.sp)
                }
            }
            DataCheck.Same -> Text(stringResource(R.string.clash_data_same), style = MaterialTheme.typography.bodyMedium, color = TavernGreenLight)
            DataCheck.Checking -> Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
            is DataCheck.Failed -> Text(
                stringResource(R.string.update_failed, c.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            DataCheck.Idle -> Unit
        }
        Clash3DButton(
            enabled = data != null && check !is DataCheck.Checking,
            onClick = {
                val current = data ?: return@Clash3DButton
                check = DataCheck.Checking
                scope.launch {
                    check = try {
                        val r = withContext(Dispatchers.IO) { DataUpdater.fetch(context) }
                        val curText = withContext(Dispatchers.IO) { DataUpdater.currentText(context) }
                        if (r == null || !DataUpdater.isBetter(r, current, curText)) DataCheck.Same
                        else DataCheck.Newer(r.text, r.data.maxTownHall, r.data.items.size)
                    } catch (e: Exception) {
                        DataCheck.Failed(e.message ?: e.javaClass.simpleName)
                    }
                }
            },
            style = ClashButtonStyle.GOLD,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ClashOutlinedText(stringResource(R.string.clash_data_check), fontSize = 14.sp)
        }
        if (downloaded) {
            Clash3DButton(
                onClick = {
                    MaxDataStore.useBundled(context)
                    scope.launch { com.snkb.chieftimer.sync.SyncRunner.refreshWidgets(context, "app") }
                },
                style = ClashButtonStyle.STONE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ClashOutlinedText(stringResource(R.string.clash_data_use_builtin), fontSize = 13.5.sp)
            }
        }
        SwitchRow(stringResource(R.string.clash_data_auto), auto) {
            auto = it
            DataUpdater.setAuto(context, it)
        }
        Text(
            stringResource(R.string.clash_data_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Keeps game data and ClashKing icons fresh quietly in background when the app opens. */
@Composable
internal fun GameDataAutoRefresh() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DataUpdater.refreshIfDue(context)
            runCatching { RemoteIcons.sync(context) }
        }
    }
}
