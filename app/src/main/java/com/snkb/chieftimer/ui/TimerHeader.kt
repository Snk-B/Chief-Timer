@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import kotlinx.coroutines.launch

private const val HINTS = "ui_hints"
private const val KEY_CALENDAR_TIP = "calendar_tip_seen"

private fun tipSeen(context: Context): Boolean =
    runCatching { context.getSharedPreferences(HINTS, Context.MODE_PRIVATE).getBoolean(KEY_CALENDAR_TIP, false) }.getOrDefault(true)

private fun markTipSeen(context: Context) {
    runCatching { context.getSharedPreferences(HINTS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CALENDAR_TIP, true).apply() }
}

/**
 * 3D header above the upgrade timers: title on the left, compact calendar toggle on the right (with first-use tooltip),
 * and the scrollable row of 3D [TavernFilterPill]s underneath.
 */
@Composable
fun UpgradesHeader(
    filter: TimerFilter,
    onFilter: (TimerFilter) -> Unit,
    calendar: Boolean,
    onCalendar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ClashOutlinedText(
                text = if (filter == TimerFilter.ALL) {
                    stringResource(R.string.village_upgrades)
                } else {
                    stringResource(R.string.upgrades_filtered, stringResource(filter.labelRes))
                },
                fontSize = 20.sp,
                color = Color.White,
                strokeWidth = 5.5f,
                modifier = Modifier.weight(1f),
            )
            CalendarSwitch(calendar, onCalendar)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerFilter.entries.forEach { entry ->
                TavernFilterPill(
                    label = stringResource(entry.labelRes),
                    selected = filter == entry,
                    onClick = { onFilter(entry) },
                )
            }
        }
    }
}

/** Calendar toggle styled in the active 3D Chief palette with a first-use RichTooltip explanation. */
@Composable
private fun CalendarSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val scope = rememberCoroutineScope()
    val tooltip = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(R.string.calendar_tip_title)) },
                action = {
                    TextButton(onClick = { scope.launch { tooltip.dismiss() } }) {
                        Text(stringResource(R.string.calendar_tip_ok), color = TavernGoldLight)
                    }
                },
            ) {
                Text(stringResource(R.string.calendar_tip_body))
            }
        },
        state = tooltip,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { on ->
                onChange(on)
                if (!tipSeen(context)) {
                    markTipSeen(context)
                    scope.launch { tooltip.show() }
                }
            },
            thumbContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_clash),
                    contentDescription = stringResource(R.string.calendar_toggle),
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = pal.pillActiveText,
                checkedIconColor = pal.pillActiveTop,
                checkedTrackColor = pal.pillActiveTop,
                checkedBorderColor = pal.accentLight,
                uncheckedThumbColor = if (pal.isDark) Color(0xFF9EA9BD) else Color(0xFFBDB0A2),
                uncheckedIconColor = pal.barTrackBg,
                uncheckedTrackColor = pal.barTrackBg,
                uncheckedBorderColor = pal.cardBorder,
            ),
        )
    }
}
