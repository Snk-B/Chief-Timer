@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.snkb.chieftimer.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.BobProgress
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.EquipmentHero
import com.snkb.chieftimer.core.EquipmentItemStatus
import com.snkb.chieftimer.core.EquipmentOverviewSummary
import com.snkb.chieftimer.core.GoldPassReduction
import com.snkb.chieftimer.core.MaxData
import com.snkb.chieftimer.core.OffenseChecklist
import com.snkb.chieftimer.core.OreLeague
import com.snkb.chieftimer.core.ProgressDelta
import com.snkb.chieftimer.core.RoadGridFilter
import com.snkb.chieftimer.core.RoadItem
import com.snkb.chieftimer.core.RoadItemSort
import com.snkb.chieftimer.core.RoadBottleneck
import com.snkb.chieftimer.core.RoadForecast
import com.snkb.chieftimer.core.RoadToMax
import com.snkb.chieftimer.core.RoadToMaxCalc
import com.snkb.chieftimer.core.RoadTotals
import com.snkb.chieftimer.core.SpendRecommendation
import com.snkb.chieftimer.core.StrategyPlanner
import com.snkb.chieftimer.core.ThReadinessReport
import com.snkb.chieftimer.core.ThReadinessStatus
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.VillageHistory
import com.snkb.chieftimer.core.WallSimResult
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import com.snkb.chieftimer.widget.RoadWidgetUpdater
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_ROAD = "plan_road"
private const val KEY_INCLUDE_WALLS = "include_walls"
private const val KEY_APPLY_GOLD_PASS = "apply_gold_pass"
private const val KEY_ORE_LEAGUE = "ore_league"
private const val KEY_ORE_WARS = "ore_wars"

private fun readIncludeWalls(context: Context): Boolean =
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).getBoolean(KEY_INCLUDE_WALLS, true)

private fun writeIncludeWalls(context: Context, include: Boolean) {
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_INCLUDE_WALLS, include).apply()
}

private fun readApplyGoldPass(context: Context): Boolean =
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).getBoolean(KEY_APPLY_GOLD_PASS, true)

private fun writeApplyGoldPass(context: Context, apply: Boolean) {
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_APPLY_GOLD_PASS, apply).apply()
}

private fun readOreLeague(context: Context): OreLeague {
    val raw = context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).getString(KEY_ORE_LEAGUE, null)
    return OreLeague.entries.firstOrNull { it.name == raw } ?: OreLeague.TITAN
}

private fun writeOreLeague(context: Context, league: OreLeague) {
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).edit().putString(KEY_ORE_LEAGUE, league.name).apply()
}

private fun readOreWars(context: Context): Int =
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).getInt(KEY_ORE_WARS, 2).coerceIn(0, 3)

private fun writeOreWars(context: Context, wars: Int) {
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).edit().putInt(KEY_ORE_WARS, wars.coerceIn(0, 3)).apply()
}

private data class RoadScreenData(
    val road: RoadToMax,
    val data: MaxData,
    val spendByResource: Map<Int, List<SpendRecommendation>>,
    val progressDelta: ProgressDelta?,
    val history: VillageHistory?,
    val bob: BobProgress?,
    val offense: OffenseChecklist,
    val equipment: EquipmentOverviewSummary,
    val readiness: ThReadinessReport,
)

/**
 * "Road to max" styled across all 4 Chief Timer 3D themes: overall Town Hall banner with share card,
 * local evolution sparkline, Town Hall readiness & equipment overview, filterable search & item grids,
 * "where to spend now" guide, ore farming forecast, and 11 category cards.
 */
@Composable
internal fun RoadToMaxCard(
    export: VillageExport?,
    builders: Int,
    playerName: String = "Chief",
    accountTag: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val scope = rememberCoroutineScope()
    val version = MaxDataStore.version
    val gpVersion = GoldPassStore.version
    var includeWalls by remember { mutableStateOf(readIncludeWalls(context)) }
    var applyGoldPass by remember { mutableStateOf(readApplyGoldPass(context)) }
    var screenData by remember { mutableStateOf<RoadScreenData?>(null) }

    LaunchedEffect(export, version, accountTag) {
        screenData = if (export == null) null else withContext(Dispatchers.Default) {
            val data = MaxDataStore.get(context)
            val road = RoadToMaxCalc.compute(data, export) ?: return@withContext null
            val names = Names.get(context)
            val spend = StrategyPlanner.topSpendRecommendations(road, data, names)
            val tag = accountTag.ifBlank { export.tag }
            val history = runCatching { StateRepository(context.applicationContext).getVillageHistory(tag) }.getOrNull()
            val delta = history?.delta(daysBack = 30)
            val bob = StrategyPlanner.bobProgress(export, data, names)
            val offense = StrategyPlanner.offenseChecklist(road)
            val equipment = StrategyPlanner.equipmentOverview(road, data, names)
            val readiness = StrategyPlanner.thReadinessAnalysis(road)
            RoadScreenData(road, data, spend, delta, history, bob, offense, equipment, readiness)
        }
    }

    val loaded = screenData ?: return
    val gpTag = accountTag.ifBlank { export?.tag.orEmpty() }
    val gp: GoldPassReduction = remember(gpTag, gpVersion) { GoldPassStore.reduction(context, gpTag) }
    val hasGoldPass = gp.builderPercent > 0 || gp.researchPercent > 0
    val road = remember(loaded.road, gp, applyGoldPass) {
        if (applyGoldPass && (gp.builderPercent > 0 || gp.researchPercent > 0)) loaded.road.withGoldPass(gp) else loaded.road
    }
    val data = loaded.data
    val doneFraction = road.overallDone(includeWalls)
    val dayUnit = stringResource(R.string.time_day_short)
    val n = builders.coerceAtLeast(1)
    val timed = road.builderWork + road.lab + road.pets
    var query by rememberSaveable { mutableStateOf("") }
    var hideMaxed by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(RoadItemSort.WORK_LEFT) }
    val gridFilter = remember(hideMaxed, sortMode) { RoadGridFilter(hideMaxed = hideMaxed, sort = sortMode) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = selectedId?.let { id -> road.items.firstOrNull { it.id == id } }
    var sharingCard by remember { mutableStateOf(false) }

    val thBmp = rememberTownHallIcon(road.townHall, 64.dp, minPx = 192)
    fun items(group: Char) = road.items.filter { it.group == group }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "road_banner") {
        TavernWoodBanner {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (thBmp != null) {
                    Image(
                        bitmap = thBmp,
                        contentDescription = null,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.size(58.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.road_title, road.townHall),
                        fontSize = 18.sp,
                        color = Color.White,
                        strokeWidth = 5f,
                        maxLines = 2,
                    )
                }
                ClashOutlinedText(
                    text = percentText(doneFraction),
                    fontSize = 22.sp,
                    color = pal.accentLight,
                    strokeWidth = 5.5f,
                    tabularNums = true,
                )
            }
            TavernWavyTubeBar(
                progress = (doneFraction ?: 1.0).toFloat(),
                tone = TavernBarTone.GOLD,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Clash3DButton(
                    enabled = !sharingCard,
                    onClick = {
                        sharingCard = true
                        scope.launch {
                            runCatching {
                                shareVillageCard(context, playerName, road, n, doneFraction, pal)
                            }
                            sharingCard = false
                        }
                    },
                    style = ClashButtonStyle.GOLD,
                    minHeight = 36.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    ClashOutlinedText(
                        text = stringResource(R.string.road_share_card),
                        fontSize = 12.5.sp,
                        color = Color.White,
                        strokeWidth = 3.8f,
                    )
                }
            }
        }
        }

        val entries = loaded.history?.entries.orEmpty()
        val showHistory = entries.size >= 2 || loaded.progressDelta != null
        if (showHistory) {
            item(key = "road_history") {
                TavernStoneCard(contentPadding = PaddingValues(14.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.road_history_title),
                        fontSize = 15.5.sp,
                        color = pal.accentLight,
                        strokeWidth = 4f,
                    )
                    loaded.progressDelta?.let { d ->
                        val signPct = if (d.overallPctDelta >= 0) "+${d.overallPctDelta}%" else "${d.overallPctDelta}%"
                        val daysTxt = if (d.builderDaysDelta >= 0) "-${d.builderDaysDelta} $dayUnit" else "+${-d.builderDaysDelta} $dayUnit"
                        Text(
                            text = stringResource(R.string.road_history_summary, d.spanDays, signPct, daysTxt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (entries.size >= 2) {
                        val chartPts = remember(entries) { entries.takeLast(14) }
                        HistorySparkline(chartPts, pal.accentLight, TavernGreenLight)
                        val first = chartPts.first()
                        val last = chartPts.last()
                        val spanDaysFloat = ((last.at - first.at).coerceAtLeast(3600L)) / 86_400f
                        val paceHoursPerDay = ((first.builderHoursLeft - last.builderHoursLeft).coerceAtLeast(0L) / spanDaysFloat).toInt()
                        if (paceHoursPerDay > 0) {
                            Text(
                                text = stringResource(R.string.road_pace_daily, paceHoursPerDay, chartPts.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item(key = "road_search_bar") {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.road_search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Text(
                        "✕",
                        color = pal.accentLight,
                        modifier = Modifier.clickable { query = "" }.padding(12.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = pal.cardBot,
                unfocusedContainerColor = pal.cardTop,
                focusedBorderColor = pal.accent,
                unfocusedBorderColor = pal.cardBorder,
                cursorColor = pal.accentLight,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        }

        item(key = "road_filter_pills") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TavernFilterPill(
                label = stringResource(R.string.road_filter_hide_maxed),
                selected = hideMaxed,
                onClick = { hideMaxed = !hideMaxed },
            )
            TavernFilterPill(
                label = stringResource(R.string.road_sort_left),
                selected = sortMode == RoadItemSort.WORK_LEFT,
                onClick = { sortMode = RoadItemSort.WORK_LEFT },
            )
            TavernFilterPill(
                label = stringResource(R.string.road_sort_lowest),
                selected = sortMode == RoadItemSort.LOWEST_LEVEL,
                onClick = { sortMode = RoadItemSort.LOWEST_LEVEL },
            )
            TavernFilterPill(
                label = stringResource(R.string.road_sort_fastest),
                selected = sortMode == RoadItemSort.FASTEST,
                onClick = { sortMode = RoadItemSort.FASTEST },
            )
        }
        }

        val q = query.trim()
        if (q.isNotEmpty()) {
            item(key = "road_search_results") {
            val found = remember(q, road.items, data, gridFilter) {
                val matched = road.items.filter { it.name?.contains(q, ignoreCase = true) == true }
                StrategyPlanner.filterAndSortRoadItems(matched, data, gridFilter)
            }
            TavernStoneCard {
                ClashOutlinedText(
                    text = if (found.isEmpty()) stringResource(R.string.road_search_none)
                    else stringResource(R.string.road_search_results, found.size),
                    fontSize = 16.sp,
                    color = pal.accentLight,
                    strokeWidth = 4.2f,
                )
                if (found.isNotEmpty()) {
                    ItemGrid(found, data, gridFilter, prefiltered = true, onItem = { selectedId = it.id })
                }
            }
            }
        }

        if (loaded.spendByResource.isNotEmpty()) {
            item(key = "road_spend_now") {
            SpendNowCard(
                recommendations = loaded.spendByResource,
                dayUnit = dayUnit,
                onSelectItem = { selectedId = it },
            )
            }
        }

        item(key = "road_time_left") {
        TavernStoneCard {
            ClashOutlinedText(
                text = stringResource(R.string.road_time_left),
                fontSize = 17.sp,
                color = pal.accent,
                strokeWidth = 4.5f,
            )
            if (applyGoldPass && hasGoldPass) {
                Text(
                    text = stringResource(R.string.road_gold_pass_active, gp.builderPercent, gp.researchPercent),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = pal.accentLight,
                )
            }
            RoadRow(
                label = stringResource(R.string.road_builders_x, n),
                value = formatDuration(road.builderSeconds(n), dayUnit),
                detail = if (n > 1 && road.builderWork.seconds > 0) {
                    stringResource(R.string.road_builders_work, n, formatDuration(road.builderWork.seconds, dayUnit))
                } else null,
                buildingId = 1000015,
                iconRes = R.drawable.road_builders,
            )
            RoadRow(
                label = stringResource(R.string.road_lab),
                value = formatDuration(road.lab.seconds, dayUnit),
                buildingId = 1000007,
                iconRes = R.drawable.road_lab,
            )
            RoadRow(
                label = stringResource(R.string.road_pets),
                value = formatDuration(road.pets.seconds, dayUnit),
                buildingId = 1000068,
                iconRes = R.drawable.road_pets,
            )
            val forecastNow = remember(road, export, n) { (System.currentTimeMillis() / 60_000L) * 60L }
            val forecast = remember(road, export, n, forecastNow) {
                StrategyPlanner.roadForecast(road, export, n, forecastNow)
            }
            val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
            if (forecast.builderDaysWithHelpers > 0 || forecast.labDaysWithHelper > 0) {
                HorizontalDivider(
                    color = pal.barTrackRim.copy(alpha = 0.65f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                val bDate = dateFmt.format(Instant.ofEpochSecond(forecast.builderFinishEpoch))
                val lDate = dateFmt.format(Instant.ofEpochSecond(forecast.labFinishEpoch))
                Text(
                    text = stringResource(R.string.road_forecast_dates, bDate, lDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val bottleneckText = when (forecast.bottleneck) {
                    RoadBottleneck.LAB -> stringResource(R.string.road_bottleneck_lab, forecast.bottleneckGapDays)
                    RoadBottleneck.HEROES -> stringResource(R.string.road_bottleneck_heroes, forecast.bottleneckGapDays)
                    RoadBottleneck.PETS -> stringResource(R.string.road_bottleneck_pets, forecast.bottleneckGapDays)
                    RoadBottleneck.BUILDERS -> stringResource(R.string.road_bottleneck_builders, forecast.bottleneckGapDays)
                    RoadBottleneck.BALANCED -> stringResource(R.string.road_bottleneck_balanced)
                }
                Text(
                    text = bottleneckText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (forecast.bottleneck == RoadBottleneck.BALANCED) TavernGreenLight else TavernGoldLight,
                )
            }
        }
        }

        item(key = "road_resources_left") {
        TavernStoneCard {
            ClashOutlinedText(
                text = stringResource(R.string.road_resources_left),
                fontSize = 17.sp,
                color = pal.accent,
                strokeWidth = 4.5f,
            )
            RoadRow(stringResource(R.string.resource_gold), compact(timed.gold), iconRes = R.drawable.road_gold)
            RoadRow(stringResource(R.string.resource_elixir), compact(timed.elixir), iconRes = R.drawable.road_elixir)
            RoadRow(stringResource(R.string.resource_dark_elixir), compact(timed.darkElixir), iconRes = R.drawable.road_dark_elixir)
            if (includeWalls && road.walls.goldOrElixir > 0) {
                RoadRow(stringResource(R.string.road_gold_or_elixir), compact(road.walls.goldOrElixir), iconRes = R.drawable.road_gold)
            }
            HorizontalDivider(
                color = pal.barTrackRim.copy(alpha = 0.65f),
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = includeWalls,
                        role = Role.Switch,
                        onValueChange = { next ->
                            includeWalls = next
                            writeIncludeWalls(context, next)
                        },
                    )
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.road_include_walls),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = includeWalls,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = pal.pillActiveText,
                        checkedTrackColor = pal.pillActiveTop,
                        checkedBorderColor = pal.accentLight,
                        uncheckedThumbColor = if (pal.isDark) Color(0xFF9EA9BD) else Color(0xFFBDB0A2),
                        uncheckedTrackColor = pal.barTrackBg,
                        uncheckedBorderColor = pal.cardBorder,
                    ),
                )
            }
            if (hasGoldPass) {
                HorizontalDivider(
                    color = pal.barTrackRim.copy(alpha = 0.65f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = applyGoldPass,
                            role = Role.Switch,
                            onValueChange = { next ->
                                applyGoldPass = next
                                writeApplyGoldPass(context, next)
                                scope.launch {
                                    runCatching { RoadWidgetUpdater.updateAll(context.applicationContext, force = true) }
                                }
                            },
                        )
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.road_apply_gold_pass, gp.builderPercent, gp.researchPercent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = applyGoldPass,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pal.pillActiveText,
                            checkedTrackColor = pal.pillActiveTop,
                            checkedBorderColor = pal.accentLight,
                            uncheckedThumbColor = if (pal.isDark) Color(0xFF9EA9BD) else Color(0xFFBDB0A2),
                            uncheckedTrackColor = pal.barTrackBg,
                            uncheckedBorderColor = pal.cardBorder,
                        ),
                    )
                }
            }
        }
        }

        item(key = "road_readiness") {
            ThReadinessCard(loaded.readiness)
        }
        loaded.bob?.let { bob ->
            item(key = "road_bob") { BobTrackerCard(bob) }
        }
        if (loaded.offense.items.isNotEmpty()) {
            item(key = "road_offense") {
            OffenseChecklistCard(
                offense = loaded.offense,
                data = data,
                filter = gridFilter,
                onItem = { selectedId = it.id },
            )
            }
        }
        if (loaded.equipment.items.isNotEmpty()) {
            item(key = "road_equipment_overview") {
                EquipmentOverviewCard(
                    overview = loaded.equipment,
                    onSelectItem = { selectedId = it },
                )
            }
        }

        item(key = "road_cat_buildings") {
        CategoryCard(
            title = stringResource(R.string.road_buildings),
            buildingId = 1000015,
            fallbackIconRes = R.drawable.road_builders,
            done = road.work.buildings.done,
            seconds = road.buildings.seconds / n,
            remaining = road.buildings,
            tone = TavernBarTone.GREEN,
            dayUnit = dayUnit,
            items = items('b'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_defences") {
        CategoryCard(
            title = stringResource(R.string.road_defences),
            buildingId = 1000008,
            fallbackIconRes = R.drawable.road_builders,
            done = road.work.defences.done,
            seconds = road.defences.seconds / n,
            remaining = road.defences,
            tone = TavernBarTone.GREEN,
            dayUnit = dayUnit,
            items = items('d'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        if (road.items.any { it.group == 'c' }) {
            item(key = "road_cat_crafted") {
            CategoryCard(
                title = stringResource(R.string.road_crafted),
                buildingId = 1000097,
                fallbackIconRes = R.drawable.road_builders,
                done = road.work.crafted.done,
                seconds = road.crafted.seconds / n,
                remaining = road.crafted,
                tone = TavernBarTone.GOLD,
                dayUnit = dayUnit,
                items = items('c'),
                data = data,
                filter = gridFilter,
                onItem = { selectedId = it.id },
            )
            }
        }
        if (road.items.any { it.group == 't' }) {
            item(key = "road_cat_traps") {
            CategoryCard(
                title = stringResource(R.string.road_traps),
                buildingId = 12000002,
                fallbackIconRes = R.drawable.road_builders,
                done = road.work.traps.done,
                seconds = road.traps.seconds / n,
                remaining = road.traps,
                tone = TavernBarTone.GREEN,
                dayUnit = dayUnit,
                items = items('t'),
                data = data,
                filter = gridFilter,
                onItem = { selectedId = it.id },
            )
            }
        }
        if (includeWalls) {
            item(key = "road_cat_walls") {
            val wallItems = items('w')
            val wallBp = if (applyGoldPass && hasGoldPass) gp.builderPercent else 0
            CategoryCard(
                title = stringResource(R.string.road_walls),
                buildingId = 1000010,
                fallbackIconRes = R.drawable.road_gold,
                done = road.work.walls.done,
                seconds = null,
                remaining = road.walls,
                tone = TavernBarTone.GOLD,
                dayUnit = dayUnit,
                items = wallItems,
                data = data,
                filter = gridFilter,
                onItem = { selectedId = it.id },
                extraContent = if (wallItems.any { !it.maxed }) {
                    { WallLootSimulator(road = road, data = data, builderPercent = wallBp) }
                } else null,
            )
            }
        }
        item(key = "road_cat_heroes") {
        CategoryCard(
            title = stringResource(R.string.road_heroes),
            buildingId = 1000071,
            fallbackIconRes = R.drawable.road_dark_elixir,
            done = road.work.heroes.done,
            seconds = road.heroSeconds(n),
            remaining = road.heroes,
            tone = TavernBarTone.GOLD,
            dayUnit = dayUnit,
            items = items('h'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_troops") {
        CategoryCard(
            title = stringResource(R.string.road_troops),
            buildingId = 1000007,
            fallbackIconRes = R.drawable.road_lab,
            done = road.work.troops.done,
            seconds = road.troops.seconds,
            remaining = road.troops,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
            items = items('l'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_spells") {
        CategoryCard(
            title = stringResource(R.string.road_spells),
            buildingId = 1000020,
            fallbackIconRes = R.drawable.road_lab,
            done = road.work.spells.done,
            seconds = road.spells.seconds,
            remaining = road.spells,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
            items = items('x'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_siege") {
        CategoryCard(
            title = stringResource(R.string.road_siege),
            buildingId = 1000059,
            fallbackIconRes = R.drawable.road_lab,
            done = road.work.siege.done,
            seconds = road.siege.seconds,
            remaining = road.siege,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
            items = items('m'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_equipment") {
        CategoryCard(
            title = stringResource(R.string.road_equipment),
            buildingId = 1000070,
            fallbackIconRes = R.drawable.road_dark_elixir,
            done = road.work.equipment.done,
            seconds = null,
            remaining = road.equipment,
            tone = TavernBarTone.GOLD,
            dayUnit = dayUnit,
            items = items('e'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }
        item(key = "road_cat_pets") {
        CategoryCard(
            title = stringResource(R.string.road_pets),
            buildingId = 1000068,
            fallbackIconRes = R.drawable.road_pets,
            done = road.work.pets.done,
            seconds = road.pets.seconds,
            remaining = road.pets,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
            items = items('p'),
            data = data,
            filter = gridFilter,
            onItem = { selectedId = it.id },
        )
        }

        item(key = "road_footer_note") {
        Text(
            stringResource(R.string.road_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        }
    }

    if (selected != null) {
        ItemSheet(selected, data, road.townHall, dayUnit) { selectedId = null }
    }
}

@Composable
private fun SpendNowCard(
    recommendations: Map<Int, List<SpendRecommendation>>,
    dayUnit: String,
    onSelectItem: (Int) -> Unit,
) {
    val pal = LocalChiefPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    var resTab by rememberSaveable { mutableIntStateOf(0) }
    val activeList = recommendations[resTab].orEmpty()

    TavernStoneCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.wiggleOnTap()) {
        Column(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ClashOutlinedText(
                    text = stringResource(R.string.road_spend_title),
                    fontSize = 16.5.sp,
                    color = pal.accentLight,
                    strokeWidth = 4.4f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (open) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.road_spend_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TavernFilterPill(
                            label = stringResource(R.string.resource_gold),
                            selected = resTab == 0,
                            onClick = { resTab = 0 },
                        )
                        TavernFilterPill(
                            label = stringResource(R.string.resource_elixir),
                            selected = resTab == 1,
                            onClick = { resTab = 1 },
                        )
                        TavernFilterPill(
                            label = stringResource(R.string.resource_dark_elixir),
                            selected = resTab == 2,
                            onClick = { resTab = 2 },
                        )
                    }
                    if (activeList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.road_spend_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val iconRes = when (resTab) {
                            1 -> R.drawable.road_elixir
                            2 -> R.drawable.road_dark_elixir
                            else -> R.drawable.road_gold
                        }
                        activeList.forEach { rec ->
                            val bmp = rememberGameIcon(rec.id, rec.nextLevel, 36.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(pal.barTrackBg)
                                    .border(1.dp, pal.barTrackRim, RoundedCornerShape(10.dp))
                                    .clickable { onSelectItem(rec.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TavernIconFrame(size = 38.dp) {
                                    if (bmp != null) Image(bmp, null, filterQuality = FilterQuality.High, modifier = Modifier.size(30.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "${rec.name} ➛ ${rec.nextLevel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (rec.seconds > 0) {
                                        Text(
                                            text = formatDuration(rec.seconds, dayUnit),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                RoadCostChip(iconRes, rec.cost)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BobTrackerCard(bob: BobProgress) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(1000015, null, 40.dp, minPx = 128)
    var open by rememberSaveable { mutableStateOf(!bob.unlocked) }
    val doneCount = listOf(bob.troop.done, bob.defense.done, bob.heroes.done, bob.gearUps.done).count { it }

    TavernStoneCard(
        contentPadding = PaddingValues(0.dp),
        modifier = if (!bob.unlocked) Modifier.wiggleOnTap() else Modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (!bob.unlocked) Modifier.clickable { open = !open } else Modifier)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TavernIconFrame(size = 44.dp) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(34.dp))
                    } else {
                        Image(painterResource(R.drawable.road_builders), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                ClashOutlinedText(
                    text = stringResource(R.string.bob_title),
                    fontSize = 16.5.sp,
                    color = if (bob.unlocked) TavernGreenLight else Color.White,
                    strokeWidth = 4.5f,
                    modifier = Modifier.weight(1f),
                )
                ClashOutlinedText(
                    text = if (bob.unlocked) stringResource(R.string.bob_unlocked) else "$doneCount/4",
                    fontSize = 14.sp,
                    color = if (bob.unlocked) TavernGreenLight else pal.accentLight,
                    strokeWidth = 4f,
                    tabularNums = true,
                )
                if (!bob.unlocked) {
                    Text(
                        text = if (open) "▴" else "▾",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!bob.unlocked) {
                AnimatedVisibility(open) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BobRequirementRow(
                            label = stringResource(R.string.bob_troop),
                            detail = bob.troop.itemName,
                            current = bob.troop.current,
                            target = bob.troop.target,
                            done = bob.troop.done,
                        )
                        BobRequirementRow(
                            label = stringResource(R.string.bob_defense),
                            detail = bob.defense.itemName,
                            current = bob.defense.current,
                            target = bob.defense.target,
                            done = bob.defense.done,
                        )
                        BobRequirementRow(
                            label = stringResource(R.string.bob_heroes),
                            detail = null,
                            current = bob.heroes.current,
                            target = bob.heroes.target,
                            done = bob.heroes.done,
                        )
                        BobRequirementRow(
                            label = stringResource(R.string.bob_gearups),
                            detail = null,
                            current = bob.gearUps.current,
                            target = bob.gearUps.target,
                            done = bob.gearUps.done,
                        )
                        if (!bob.gearUps.done) {
                            Text(
                                text = stringResource(R.string.bob_gearup_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySparkline(
    entries: List<com.snkb.chieftimer.core.VillageSnapshot>,
    overallColor: Color,
    wallsColor: Color,
) {
    val pal = LocalChiefPalette.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(pal.barTrackBg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (entries.size < 2) return@Canvas
        val minPct = entries.minOf { minOf(it.overallPct, it.wallsPct) }.coerceAtLeast(0)
        val maxPct = entries.maxOf { maxOf(it.overallPct, it.wallsPct) }.coerceAtLeast(minPct + 1)
        val span = (maxPct - minPct).toFloat()
        fun yFor(pct: Int): Float = size.height - ((pct - minPct) / span) * size.height

        val overallPath = Path()
        val wallsPath = Path()
        entries.forEachIndexed { idx, s ->
            val x = (idx.toFloat() / (entries.size - 1)) * size.width
            val yo = yFor(s.overallPct)
            val yw = yFor(s.wallsPct)
            if (idx == 0) {
                overallPath.moveTo(x, yo)
                wallsPath.moveTo(x, yw)
            } else {
                overallPath.lineTo(x, yo)
                wallsPath.lineTo(x, yw)
            }
        }
        drawPath(wallsPath, color = wallsColor.copy(alpha = 0.65f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(overallPath, color = overallColor, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lastX = size.width
        drawCircle(overallColor, radius = 3.2.dp.toPx(), center = Offset(lastX, yFor(entries.last().overallPct)))
    }
}

@Composable
private fun ThReadinessCard(report: ThReadinessReport) {
    val pal = LocalChiefPalette.current
    val (statusRes, badgeColor) = when (report.status) {
        ThReadinessStatus.READY -> R.string.road_readiness_ready to TavernGreenLight
        ThReadinessStatus.ALMOST_READY -> R.string.road_readiness_almost to pal.accentLight
        ThReadinessStatus.RUSHED_OFFENSE -> R.string.road_readiness_rush_offense to Color(0xFFFF8A80)
        ThReadinessStatus.RUSHED_DEFENSE -> R.string.road_readiness_rush_defense to TavernGoldLight
    }
    var open by rememberSaveable { mutableStateOf(false) }

    TavernStoneCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.wiggleOnTap()) {
        Column(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ClashOutlinedText(
                    text = stringResource(R.string.road_readiness_title),
                    fontSize = 15.5.sp,
                    color = pal.accentLight,
                    strokeWidth = 4f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${report.overallPct}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = if (open) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = badgeColor,
            )
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadinessMeterRow(stringResource(R.string.road_readiness_offense), report.offensePct, TavernBarTone.ELIXIR)
                    ReadinessMeterRow(stringResource(R.string.road_readiness_defense), report.defensePct, TavernBarTone.GREEN)
                    ReadinessMeterRow(stringResource(R.string.road_readiness_walls), report.wallsPct, TavernBarTone.GOLD)
                }
            }
        }
    }
}

@Composable
private fun ReadinessMeterRow(label: String, pct: Int, tone: TavernBarTone) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text("$pct%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TavernGoldLight)
        }
        TavernWavyTubeBar(progress = (pct / 100f).coerceIn(0f, 1f), tone = tone)
    }
}

@Composable
private fun EquipmentOverviewCard(
    overview: EquipmentOverviewSummary,
    onSelectItem: (Int) -> Unit,
) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(1000070, null, 40.dp, minPx = 128)
    var open by rememberSaveable { mutableStateOf(false) }
    var heroFilter by rememberSaveable { mutableStateOf<EquipmentHero?>(null) }
    val availableHeroes = remember(overview) {
        EquipmentHero.entries.filter { h -> overview.items.any { it.hero == h } }
    }
    val visibleItems = remember(overview, heroFilter) {
        val list = if (heroFilter == null) overview.items else overview.items.filter { it.hero == heroFilter }
        list.take(10)
    }
    val totalOres = remember(overview) {
        RoadTotals(shinyOre = overview.totalShinyLeft, glowyOre = overview.totalGlowyLeft, starryOre = overview.totalStarryLeft)
    }

    TavernStoneCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.wiggleOnTap()) {
        Column(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TavernIconFrame(size = 44.dp) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(34.dp))
                    } else {
                        Image(painterResource(R.drawable.road_dark_elixir), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.equip_overview_title),
                        fontSize = 16.5.sp,
                        color = Color.White,
                        strokeWidth = 4.5f,
                    )
                    Text(
                        text = stringResource(R.string.equip_overview_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (open) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (totalOres.shinyOre + totalOres.glowyOre + totalOres.starryOre > 0L) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoadOreChips(totalOres)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TavernFilterPill(
                            label = stringResource(R.string.equip_hero_all),
                            selected = heroFilter == null,
                            onClick = { heroFilter = null },
                        )
                        availableHeroes.forEach { h ->
                            TavernFilterPill(
                                label = stringResource(heroLabelRes(h)),
                                selected = heroFilter == h,
                                onClick = { heroFilter = h },
                            )
                        }
                    }
                    visibleItems.forEach { eq ->
                        EquipmentOverviewRow(eq = eq, onClick = { onSelectItem(eq.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentOverviewRow(eq: EquipmentItemStatus, onClick: () -> Unit) {
    val pal = LocalChiefPalette.current
    val bmp = rememberGameIcon(eq.id, eq.level, 36.dp)
    val subText = if (eq.maxed) {
        stringResource(R.string.equip_maxed_badge)
    } else {
        val oreParts = buildList {
            if (eq.milestoneShiny > 0L) add("${eq.milestoneShiny} S")
            if (eq.milestoneGlowy > 0L) add("${eq.milestoneGlowy} G")
            if (eq.milestoneStarry > 0L) add("${eq.milestoneStarry} ★")
        }.joinToString(" · ")
        val milestoneText = stringResource(R.string.equip_next_breakpoint, eq.nextMilestone)
        if (oreParts.isNotEmpty()) "$milestoneText ($oreParts)" else milestoneText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pal.barTrackBg)
            .border(1.dp, if (eq.isEpic && !eq.maxed) TavernGold.copy(alpha = 0.55f) else pal.barTrackRim, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TavernIconFrame(size = 38.dp) {
            if (bmp != null) Image(bmp, null, filterQuality = FilterQuality.High, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = eq.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(if (eq.isEpic) R.string.equip_rarity_epic else R.string.equip_rarity_common),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = if (eq.isEpic) TavernGoldLight else Color(0xFF90CAF9),
                )
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = if (eq.maxed) TavernGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${eq.level}/${eq.maxLevel}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (eq.maxed) TavernGreenLight else pal.accentLight,
        )
    }
}

private fun heroLabelRes(hero: EquipmentHero): Int = when (hero) {
    EquipmentHero.KING -> R.string.equip_hero_king
    EquipmentHero.QUEEN -> R.string.equip_hero_queen
    EquipmentHero.PRINCE -> R.string.equip_hero_prince
    EquipmentHero.WARDEN -> R.string.equip_hero_warden
    EquipmentHero.CHAMPION -> R.string.equip_hero_champion
    EquipmentHero.DUKE -> R.string.equip_hero_duke
    EquipmentHero.OTHER -> R.string.equip_hero_other
}

@Composable
private fun BobRequirementRow(
    label: String,
    detail: String?,
    current: Int,
    target: Int,
    done: Boolean,
) {
    val pal = LocalChiefPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(pal.barTrackBg)
            .border(1.dp, if (done) TavernGreenLight.copy(alpha = 0.6f) else pal.barTrackRim, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (done) "✓ $current/$target" else "$current/$target",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (done) TavernGreenLight else pal.accentLight,
        )
    }
}

@Composable
private fun OffenseChecklistCard(
    offense: OffenseChecklist,
    data: MaxData,
    filter: RoadGridFilter,
    onItem: (RoadItem) -> Unit,
) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(1000000, null, 40.dp, minPx = 128)
    var open by rememberSaveable { mutableStateOf(false) }
    val done = offense.share.done

    TavernStoneCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.wiggleOnTap(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TavernIconFrame(size = 44.dp) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(34.dp))
                    } else {
                        Image(painterResource(R.drawable.road_builders), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.offense_title),
                        fontSize = 16.5.sp,
                        color = Color.White,
                        strokeWidth = 4.5f,
                    )
                    Text(
                        text = stringResource(R.string.offense_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ClashOutlinedText(
                    text = percentText(done),
                    fontSize = 16.5.sp,
                    color = pal.accentLight,
                    strokeWidth = 4.5f,
                    tabularNums = true,
                )
                Text(
                    text = if (open) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TavernWavyTubeBar(
                progress = (done ?: 1.0).toFloat(),
                tone = TavernBarTone.ELIXIR,
            )
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemGrid(offense.items, data, filter, onItem = onItem)
                }
            }
        }
    }
}

/** One expandable group of upgrades in 3D style: framed icon, title, % done, chevron, wavy tube bar, levels left, time & cost chips, and item grid. */
@Composable
private fun CategoryCard(
    title: String,
    buildingId: Int,
    @DrawableRes fallbackIconRes: Int,
    done: Double?,
    /** Null for upgrades that take no time (walls, equipment). */
    seconds: Long?,
    remaining: RoadTotals,
    tone: TavernBarTone,
    dayUnit: String,
    items: List<RoadItem> = emptyList(),
    data: MaxData = MaxData.EMPTY,
    filter: RoadGridFilter = RoadGridFilter(),
    onItem: (RoadItem) -> Unit = {},
    extraContent: (@Composable () -> Unit)? = null,
) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(buildingId, null, 40.dp, minPx = 128)
    var open by rememberSaveable(title) { mutableStateOf(false) }

    TavernStoneCard(
        contentPadding = PaddingValues(0.dp),
        modifier = if (items.isNotEmpty()) Modifier.wiggleOnTap() else Modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (items.isNotEmpty()) Modifier.clickable { open = !open } else Modifier)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TavernIconFrame(size = 44.dp) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(34.dp))
                    } else {
                        Image(painterResource(fallbackIconRes), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                ClashOutlinedText(
                    text = title,
                    fontSize = 16.5.sp,
                    color = Color.White,
                    strokeWidth = 4.5f,
                    modifier = Modifier.weight(1f),
                )
                ClashOutlinedText(
                    text = percentText(done),
                    fontSize = 16.5.sp,
                    color = pal.accentLight,
                    strokeWidth = 4.5f,
                    tabularNums = true,
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = if (open) "▴" else "▾",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TavernWavyTubeBar(
                progress = (done ?: 1.0).toFloat(),
                tone = tone,
            )
            if (items.isNotEmpty()) {
                val left = items.sumOf { it.levelsLeft }
                Text(
                    text = stringResource(R.string.road_levels_left, left),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.road_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                ClashOutlinedText(
                    text = if (seconds != null && remaining.seconds > 0) formatDuration(seconds, dayUnit) else stringResource(R.string.road_no_timer),
                    fontSize = 14.5.sp,
                    color = pal.accentLight,
                    strokeWidth = 4f,
                    tabularNums = true,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.road_cost_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoadCostChip(R.drawable.road_gold, remaining.gold)
                    RoadCostChip(R.drawable.road_elixir, remaining.elixir)
                    RoadCostChip(R.drawable.road_dark_elixir, remaining.darkElixir)
                    RoadGoldOrElixirChip(remaining.goldOrElixir)
                    RoadOreChips(remaining)
                    if (remaining.gold + remaining.elixir + remaining.darkElixir + remaining.goldOrElixir +
                        remaining.shinyOre + remaining.glowyOre + remaining.starryOre == 0L
                    ) {
                        Text("—", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AnimatedVisibility(open && items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    extraContent?.invoke()
                    ItemGrid(items, data, filter, onItem = onItem)
                }
            }
        }
    }
}

/** The group's items as beveled tiles: lowest owned level badge, "×N" copy badge, initials for Crafted Defenses. */
@Composable
private fun WallLootSimulator(road: RoadToMax, data: MaxData, builderPercent: Int) {
    val pal = LocalChiefPalette.current
    var millions by rememberSaveable { mutableIntStateOf(10) }
    val sim = remember(road, data, millions, builderPercent) {
        StrategyPlanner.simulateWallsForLoot(road, data, millions * 1_000_000L, builderPercent)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pal.barTrackBg)
            .border(1.dp, pal.barTrackRim, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.wall_sim_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = TavernGoldLight,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(5, 10, 20, 40, 80).forEach { m ->
                TavernFilterPill(
                    label = "${m}M",
                    selected = millions == m,
                    onClick = { millions = m },
                )
            }
        }
        Text(
            text = stringResource(R.string.wall_sim_result, millions, sim.upgradedWalls, sim.wallsLeftAfter),
            style = MaterialTheme.typography.bodySmall,
            color = if (sim.upgradedWalls > 0) TavernGreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemGrid(
    items: List<RoadItem>,
    data: MaxData,
    filter: RoadGridFilter,
    prefiltered: Boolean = false,
    onItem: (RoadItem) -> Unit,
) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val names = remember { Names.get(context) }
    val sorted = remember(items, data, filter, prefiltered) {
        if (prefiltered) items else StrategyPlanner.filterAndSortRoadItems(items, data, filter)
    }
    if (sorted.isEmpty()) {
        Text(
            text = stringResource(R.string.road_all_maxed),
            style = MaterialTheme.typography.bodySmall,
            color = pal.accentLight,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val gap = 8.dp
        val columns = ((maxWidth + gap) / (58.dp + gap)).toInt().coerceAtLeast(1)
        val tile = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (item in sorted) {
                val picture = rememberGameIcon(item.id, item.lowest.coerceAtLeast(1), tile, minPx = 96)
                val edge = if (item.maxed) pal.accent else pal.cardBorder
                Box(
                    Modifier
                        .size(tile)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.barTrackBg)
                        .border(1.5.dp, edge, RoundedCornerShape(12.dp))
                        .clickable { onItem(item) },
                ) {
                    if (picture != null) {
                        Image(
                            bitmap = picture,
                            contentDescription = item.name,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    }
                    if (item.group == 'c') {
                        val badgeRes = craftedStatBadgeRes(names.name(item.id), item.name)
                        val badgeText = if (badgeRes != null) {
                            stringResource(badgeRes)
                        } else {
                            val fallback = names.name(item.id).takeUnless { it.startsWith("#") } ?: item.name.orEmpty()
                            fallback.substringAfterLast(' ').take(4).uppercase()
                        }
                        if (badgeText.isNotBlank()) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                                color = pal.accentLight,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(pal.cardBot.copy(alpha = 0.88f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (item.count > 1) {
                        Text(
                            text = "×${item.count}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    Text(
                        text = item.lowest.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (item.maxed) pal.pillActiveText else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (item.maxed) pal.pillActiveTop else pal.cardBot.copy(alpha = 0.92f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Bottom sheet with per-item remaining levels, copies, builder time, cost/ore chips, and equipment ore forecast. */
@Composable
private fun ItemSheet(item: RoadItem, data: MaxData, townHall: Int, dayUnit: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val steps = data.items[item.id]?.steps.orEmpty()
    val left = remember(item, data) {
        var t = RoadTotals()
        val floor = if (item.group in setOf('l', 'x', 'm', 'e')) 1 else 0
        for (level in item.levels) {
            for (i in level.coerceAtLeast(floor) until item.maxLevel) {
                steps.getOrNull(i)?.let { t = t.add(it) }
            }
        }
        t
    }
    val picture = rememberGameIcon(item.id, item.lowest.coerceAtLeast(1), 72.dp, minPx = 160)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pal.cardBot,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TavernIconFrame(size = 76.dp) {
                    if (picture != null) {
                        Image(picture, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(64.dp))
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                }
                val sheetNames = remember { Names.get(context) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ClashOutlinedText(
                        text = craftedItemTitle(item, sheetNames),
                        fontSize = 19.sp,
                        color = Color.White,
                        strokeWidth = 4.8f,
                        maxLines = 2,
                    )
                    Text(
                        text = if (item.maxed) stringResource(R.string.road_item_maxed, item.maxLevel, townHall)
                        else stringResource(R.string.road_item_level, item.lowest, item.maxLevel, townHall),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.maxed) pal.accentLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.count > 1) {
                val breakdown = remember(item.levels) {
                    item.levels.groupingBy { it }.eachCount().toSortedMap(reverseOrder())
                        .entries.joinToString(", ") { "${it.key} ×${it.value}" }
                }
                Text(
                    text = stringResource(R.string.road_item_copies, breakdown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.road_levels_left, item.levelsLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (left.seconds > 0) {
                Text(
                    text = stringResource(R.string.road_item_time, formatDuration(left.seconds, dayUnit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.accentLight,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RoadCostChip(R.drawable.road_gold, left.gold)
                RoadCostChip(R.drawable.road_elixir, left.elixir)
                RoadCostChip(R.drawable.road_dark_elixir, left.darkElixir)
                RoadGoldOrElixirChip(left.goldOrElixir)
                RoadOreChips(left)
            }
            if (item.group == 'e' && !item.maxed && (left.shinyOre + left.glowyOre + left.starryOre) > 0L) {
                HorizontalDivider(color = pal.barTrackRim.copy(alpha = 0.65f))
                var league by remember { mutableStateOf(readOreLeague(context)) }
                var wars by remember { mutableIntStateOf(readOreWars(context)) }
                val estimate = remember(left, league, wars) {
                    StrategyPlanner.estimateOreDays(left.shinyOre, left.glowyOre, left.starryOre, league, wars)
                }
                ClashOutlinedText(
                    text = stringResource(R.string.ore_forecast_title),
                    fontSize = 15.sp,
                    color = pal.accentLight,
                    strokeWidth = 4f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OreLeague.entries.forEach { lg ->
                        TavernFilterPill(
                            label = stringResource(oreLeagueLabel(lg)),
                            selected = league == lg,
                            onClick = { league = lg; writeOreLeague(context, lg) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ore_wars_week),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(0, 2, 3).forEach { w ->
                        TavernFilterPill(
                            label = "$w",
                            selected = wars == w,
                            onClick = { wars = w; writeOreWars(context, w) },
                        )
                    }
                }
                val daysText = if (estimate.totalDays >= 999) {
                    stringResource(R.string.ore_forecast_need_war)
                } else {
                    stringResource(R.string.ore_forecast_days, estimate.totalDays)
                }
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
        }
    }
}

private fun oreLeagueLabel(lg: OreLeague): Int = when (lg) {
    OreLeague.GOLD -> R.string.league_gold
    OreLeague.CRYSTAL -> R.string.league_crystal
    OreLeague.MASTER -> R.string.league_master
    OreLeague.CHAMPION -> R.string.league_champion
    OreLeague.TITAN -> R.string.league_titan
    OreLeague.LEGEND -> R.string.league_legend
}

@Composable
private fun RoadCostChip(@DrawableRes icon: Int, amount: Long) {
    if (amount <= 0) return
    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(pal.barTrackBg)
            .border(1.dp, pal.barTrackRim, shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(compact(amount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RoadGoldOrElixirChip(amount: Long) {
    if (amount <= 0) return
    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(pal.barTrackBg)
            .border(1.dp, pal.barTrackRim, shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(R.drawable.road_gold), contentDescription = null, modifier = Modifier.size(16.dp))
        Image(painterResource(R.drawable.road_elixir), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(compact(amount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RoadOreChips(t: RoadTotals) {
    val shiny = stringResource(R.string.ore_shiny)
    val glowy = stringResource(R.string.ore_glowy)
    val starry = stringResource(R.string.ore_starry)
    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(10.dp)
    for ((label, amount, tint) in listOf(
        Triple(shiny, t.shinyOre, Color(0xFF81D4FA)),
        Triple(glowy, t.glowyOre, Color(0xFFCE93D8)),
        Triple(starry, t.starryOre, Color(0xFFFFD54F)),
    )) {
        if (amount <= 0) continue
        Row(
            Modifier
                .clip(shape)
                .background(pal.barTrackBg)
                .border(1.dp, pal.barTrackRim, shape)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = tint)
            Text(compact(amount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Whole percent, rounded down so 99.6% never reads as finished. */
private fun percentText(done: Double?): String = "${((done ?: 1.0) * 100).toInt().coerceIn(0, 100)}%"

@Composable
private fun RoadRow(
    label: String,
    value: String,
    detail: String? = null,
    buildingId: Int? = null,
    @DrawableRes iconRes: Int? = null,
) {
    val pal = LocalChiefPalette.current
    val building = rememberGameIcon(buildingId, null, 36.dp)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TavernIconFrame(size = 42.dp) {
            when {
                building != null -> Image(building, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(32.dp))
                iconRes != null -> Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(30.dp).padding(1.dp))
                else -> Spacer(Modifier.size(32.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ClashOutlinedText(
            text = value,
            fontSize = 15.5.sp,
            color = pal.accentLight,
            strokeWidth = 4.2f,
            tabularNums = true,
        )
    }
}

private fun formatDuration(seconds: Long, dayUnit: String): String {
    val days = seconds / 86_400
    return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
}

/** 2,924,000,000 -> "2.92B", 47,000,000 -> "47.0M", 12,500 -> "12.5K". */
internal fun compact(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}

private fun craftedParentId(moduleId: Int): Int? = when (moduleId) {
    in 102000024..102000026 -> 103000008
    in 102000027..102000029 -> 103000009
    in 102000030..102000032 -> 103000010
    in 102000033..102000035 -> 103000011
    in 102000036..102000038 -> 103000012
    in 102000039..102000041 -> 103000013
    else -> null
}

private fun craftedItemTitle(item: RoadItem, names: com.snkb.chieftimer.core.NameMap): String {
    if (item.group != 'c') return item.name ?: "#${item.id}"
    val stat = names.name(item.id).takeUnless { it.startsWith("#") }
    val parent = craftedParentId(item.id)?.let { names.name(it) }?.takeUnless { it.startsWith("#") }
    return when {
        parent != null && stat != null -> "$parent · $stat"
        stat != null -> stat
        else -> item.name ?: "#${item.id}"
    }
}

private fun craftedStatBadgeRes(localizedName: String, rawName: String?): Int? {
    val probe = "${localizedName.lowercase()} ${rawName.orEmpty().lowercase()}"
    return when {
        "hitpoint" in probe || "vida" in probe -> R.string.crafted_stat_hp
        "explosion" in probe || "área" in probe || "area" in probe -> R.string.crafted_stat_exp
        "burst" in probe || "rajada" in probe -> R.string.crafted_stat_burst
        "cooldown" in probe || "tempo de ataque" in probe -> R.string.crafted_stat_cd
        "range" in probe || "alcance" in probe -> R.string.crafted_stat_rng
        "seconds" in probe || "segundos" in probe || "ativo" in probe -> R.string.crafted_stat_dur
        "poison" in probe || "veneno" in probe -> R.string.crafted_stat_psn
        "damage" in probe || "dano" in probe -> R.string.crafted_stat_dmg
        else -> null
    }
}
