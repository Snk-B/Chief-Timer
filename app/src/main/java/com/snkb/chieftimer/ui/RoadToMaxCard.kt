@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.snkb.chieftimer.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.RoadToMax
import com.snkb.chieftimer.core.RoadToMaxCalc
import com.snkb.chieftimer.core.RoadTotals
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_ROAD = "plan_road"
private const val KEY_INCLUDE_WALLS = "include_walls"

private fun readIncludeWalls(context: Context): Boolean =
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).getBoolean(KEY_INCLUDE_WALLS, true)

private fun writeIncludeWalls(context: Context, include: Boolean) {
    context.getSharedPreferences(PREFS_ROAD, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_INCLUDE_WALLS, include).apply()
}

/**
 * "Road to max" styled across all 4 Chief Timer 3D themes: overall Town Hall completion banner,
 * remaining builder/lab/pet time card, total resources card, and 5 individual category cards.
 */
@Composable
internal fun RoadToMaxCard(export: VillageExport?, builders: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val version = MaxDataStore.version
    var includeWalls by remember { mutableStateOf(readIncludeWalls(context)) }
    var computedRoad by remember { mutableStateOf<RoadToMax?>(null) }
    LaunchedEffect(export, version) {
        computedRoad = if (export == null) null else withContext(Dispatchers.Default) {
            val data = MaxDataStore.get(context)
            RoadToMaxCalc.compute(data, export)
        }
    }
    val road = computedRoad ?: return
    val doneFraction = road.overallDone(includeWalls)
    val dayUnit = stringResource(R.string.time_day_short)
    val n = builders.coerceAtLeast(1)
    val timed = road.builderWork + road.lab + road.pets

    val thBmp = rememberTownHallIcon(road.townHall, 64.dp, minPx = 192)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }

        TavernStoneCard {
            ClashOutlinedText(
                text = stringResource(R.string.road_time_left),
                fontSize = 17.sp,
                color = pal.accent,
                strokeWidth = 4.5f,
            )
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
        }

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
        }

        CategoryCard(
            title = stringResource(R.string.road_buildings),
            buildingId = 1000015,
            fallbackIconRes = R.drawable.road_builders,
            done = road.levels.buildings.done,
            seconds = road.buildings.seconds / n,
            remaining = road.buildings,
            tone = TavernBarTone.GREEN,
            dayUnit = dayUnit,
        )
        if (includeWalls) {
            CategoryCard(
                title = stringResource(R.string.road_walls),
                buildingId = 1000010,
                fallbackIconRes = R.drawable.road_gold,
                done = road.levels.walls.done,
                seconds = null,
                remaining = road.walls,
                tone = TavernBarTone.GOLD,
                dayUnit = dayUnit,
            )
        }
        CategoryCard(
            title = stringResource(R.string.road_heroes),
            buildingId = 1000071,
            fallbackIconRes = R.drawable.road_dark_elixir,
            done = road.levels.heroes.done,
            seconds = road.heroSeconds(n),
            remaining = road.heroes,
            tone = TavernBarTone.GOLD,
            dayUnit = dayUnit,
        )
        CategoryCard(
            title = stringResource(R.string.road_lab),
            buildingId = 1000007,
            fallbackIconRes = R.drawable.road_lab,
            done = road.levels.lab.done,
            seconds = road.lab.seconds,
            remaining = road.lab,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
        )
        CategoryCard(
            title = stringResource(R.string.road_pets),
            buildingId = 1000068,
            fallbackIconRes = R.drawable.road_pets,
            done = road.levels.pets.done,
            seconds = road.pets.seconds,
            remaining = road.pets,
            tone = TavernBarTone.ELIXIR,
            dayUnit = dayUnit,
        )

        Text(
            stringResource(R.string.road_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** One group of upgrades in 3D style: framed icon, title, % done, wavy tube bar, time left (or "No timer"), and 3D cost chips. */
@Composable
private fun CategoryCard(
    title: String,
    buildingId: Int,
    @DrawableRes fallbackIconRes: Int,
    done: Double?,
    /** Null for upgrades that take no time (walls). */
    seconds: Long?,
    remaining: RoadTotals,
    tone: TavernBarTone,
    dayUnit: String,
) {
    val pal = LocalChiefPalette.current
    val icon = rememberGameIcon(buildingId, null, 40.dp, minPx = 128)
    TavernStoneCard {
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
        }
        TavernWavyTubeBar(
            progress = (done ?: 1.0).toFloat(),
            tone = tone,
        )
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
                if (remaining.goldOrElixir > 0) {
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
                        Text(compact(remaining.goldOrElixir), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (remaining.gold + remaining.elixir + remaining.darkElixir + remaining.goldOrElixir == 0L) {
                    Text("—", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
