package com.snkb.chieftimer.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.RadioButton
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.sync.SyncRunner
import com.snkb.chieftimer.widget.WidgetPreviews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.snkb.chieftimer.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.snkb.chieftimer.sync.ClipboardAutoSync
import androidx.compose.runtime.rememberCoroutineScope
import com.snkb.chieftimer.sync.ClockTowerTracking
import com.snkb.chieftimer.sync.IdleAlert
import com.snkb.chieftimer.sync.LiveUpdateAlert
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.core.GoldPassLogic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.companion.CompanionPrefs
import com.snkb.chieftimer.companion.CompanionService
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.BoostSettings
import com.snkb.chieftimer.core.GameIds
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.notify.Notifier
import com.snkb.chieftimer.ui.theme.LocalChiefPalette

private const val H = 3600L

/** Virtualized Settings items for a LazyColumn; retains existing storage and ranges while avoiding eager composition and 1s polling. */
fun LazyListScope.settingsSectionItems(
    settings: AppSettings,
    export: VillageExport?,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    selectedTag: String? = null,
) {
    val b = settings.boosts
    fun boosts(f: (BoostSettings) -> BoostSettings) = onChange { it.copy(boosts = f(it.boosts)) }

    item(key = "settings_gold_pass") {
        val context = LocalContext.current
        SectionCard(stringResource(R.string.settings_section_gold_pass)) {
            val gpScope = rememberCoroutineScope()
            var gpOpen by remember { mutableStateOf(false) }
            val gp = remember(selectedTag, GoldPassStore.version) { GoldPassStore.answer(context, selectedTag) }
            Text(
                if (gp.month == null) stringResource(R.string.gold_pass_not_set)
                else if (gp.gold) stringResource(R.string.gold_pass_summary_gold, gp.builder, gp.research)
                else stringResource(R.string.gold_pass_silver_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Clash3DButton(onClick = { gpOpen = true }, style = ClashButtonStyle.GOLD) {
                ClashOutlinedText(stringResource(R.string.gold_pass_change), fontSize = 14.sp)
            }
            var gpAsk by remember { mutableStateOf(GoldPassStore.askMonthly(context)) }
            SwitchRow(stringResource(R.string.gold_pass_ask_monthly), gpAsk) { on -> gpAsk = on; GoldPassStore.setAskMonthly(context, on) }
            if (gpOpen) {
                GoldPassDialog(
                    initial = gp,
                    laterLabel = stringResource(android.R.string.cancel),
                    onSave = { gold, br, rr ->
                        GoldPassStore.save(context, selectedTag, GoldPassLogic.monthKey(System.currentTimeMillis() / 1000L), gold, br, rr)
                        gpOpen = false
                        gpScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                    },
                    onLater = { gpOpen = false },
                )
            }
        }
    }

    item(key = "settings_clock_tower") {
        val context = LocalContext.current
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.settings_section_clock_tower)) {
            val ctScope = rememberCoroutineScope()
            var ctTrack by remember { mutableStateOf(ClockTowerTracking.enabled(context)) }
            SwitchRow(stringResource(R.string.settings_clock_tower), ctTrack) { on ->
                ctTrack = on
                ClockTowerTracking.setEnabled(context, on)
                ctScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(stringResource(R.string.settings_clock_tower_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            var ctAlert by remember { mutableStateOf(ClockTowerTracking.alertEnabled(context)) }
            SwitchRow(stringResource(R.string.settings_clock_tower_alert), ctAlert) { on ->
                ctAlert = on
                ClockTowerTracking.setAlertEnabled(context, on)
                ctScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(stringResource(R.string.settings_clock_tower_alert_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
        }
    }

    item(key = "settings_sync") {
        val context = LocalContext.current
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.settings_section_sync)) {
            var autoClip by remember { mutableStateOf(ClipboardAutoSync.enabled(context)) }
            SwitchRow(stringResource(R.string.settings_auto_clipboard), autoClip) { on ->
                autoClip = on
                ClipboardAutoSync.setEnabled(context, on)
            }
            Text(
                stringResource(R.string.settings_auto_clipboard_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
        }
    }

    item(key = "settings_companion") {
        val context = LocalContext.current
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        val resumeTick = rememberResumeTick()
        var permissionTick by remember { mutableIntStateOf(0) }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
        val notificationsAllowed = remember(resumeTick, permissionTick) { Notifier.allowed(context) }
        SectionCard(stringResource(R.string.companion_section)) {
            var compOn by remember { mutableStateOf(CompanionPrefs.enabled(context)) }
            val overlayOn = remember(resumeTick, permissionTick) { CompanionPrefs.overlayAllowed(context) }
            val usageOn = remember(resumeTick, permissionTick) { CompanionPrefs.usageAccessAllowed(context) }
            LaunchedEffect(compOn, overlayOn, usageOn) {
                if (compOn) CompanionService.sync(context) else CompanionService.stop(context)
            }
            SwitchRow(stringResource(R.string.companion_enable), compOn) { on ->
                compOn = on
                CompanionPrefs.setEnabled(context, on)
                if (on) CompanionService.sync(context) else CompanionService.stop(context)
                CompanionService.refresh()
            }
            Text(
                stringResource(R.string.companion_enable_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            AnimatedVisibility(visible = compOn) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var barOn by remember { mutableStateOf(CompanionPrefs.bar(context)) }
                    SwitchRow(stringResource(R.string.companion_bar), barOn) { on ->
                        barOn = on
                        CompanionPrefs.setBar(context, on)
                        CompanionService.refresh()
                    }
                    Text(
                        stringResource(R.string.companion_bar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyColor,
                    )
                    var notifOn by remember { mutableStateOf(CompanionPrefs.notification(context)) }
                    SwitchRow(stringResource(R.string.companion_notification), notifOn) { on ->
                        notifOn = on
                        CompanionPrefs.setNotification(context, on)
                        CompanionService.refresh()
                    }
                    Text(
                        stringResource(if (overlayOn) R.string.companion_overlay_on else R.string.companion_overlay_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overlayOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Clash3DButton(
                        onClick = { openOverlaySettings(context); permissionTick++ },
                        style = if (overlayOn) ClashButtonStyle.STONE else ClashButtonStyle.GOLD,
                    ) {
                        ClashOutlinedText(stringResource(R.string.companion_open_overlay), fontSize = 13.5.sp)
                    }
                    Text(
                        stringResource(if (usageOn) R.string.companion_usage_on else R.string.companion_usage_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (usageOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Clash3DButton(
                        onClick = { openUsageAccessSettings(context); permissionTick++ },
                        style = if (usageOn) ClashButtonStyle.STONE else ClashButtonStyle.GOLD,
                    ) {
                        ClashOutlinedText(stringResource(R.string.companion_open_usage), fontSize = 13.5.sp)
                    }
                    if (!notificationsAllowed && notifOn) {
                        Text(stringResource(R.string.companion_notif_missing), color = MaterialTheme.colorScheme.error)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Clash3DButton(
                                onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                style = ClashButtonStyle.GOLD,
                            ) {
                                ClashOutlinedText(stringResource(R.string.companion_grant_notif), fontSize = 13.5.sp)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.companion_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyColor,
                    )
                    Clash3DButton(onClick = { openAppSettings(context) }, style = ClashButtonStyle.STONE) {
                        ClashOutlinedText(stringResource(R.string.companion_open_app_info), fontSize = 13.5.sp)
                    }
                }
            }
        }
    }

    item(key = "settings_notifications") {
        val context = LocalContext.current
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        val resumeTick = rememberResumeTick()
        var permissionTick by remember { mutableIntStateOf(0) }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
        val notificationsAllowed = remember(resumeTick, permissionTick) { Notifier.allowed(context) }
        SectionCard(stringResource(R.string.settings_section_notifications)) {
            SwitchRow(stringResource(R.string.settings_finish_alerts), settings.notificationsEnabled) { on ->
                onChange { it.copy(notificationsEnabled = on) }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.allowed(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Text(stringResource(R.string.settings_alert_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            var homeAlerts by remember { mutableStateOf(Notifier.homeAlerts(context)) }
            var bbAlerts by remember { mutableStateOf(Notifier.builderBaseAlerts(context)) }
            var helperAlerts by remember { mutableStateOf(Notifier.helperAlerts(context)) }
            if (settings.notificationsEnabled) {
                Text(stringResource(R.string.settings_alerts_villages), style = MaterialTheme.typography.bodySmall, color = bodyColor)
                TavernCheckRow(
                    label = stringResource(R.string.settings_alerts_home),
                    detail = stringResource(R.string.settings_alerts_home_desc),
                    checked = homeAlerts,
                ) { on ->
                    homeAlerts = on
                    Notifier.setHomeAlerts(context, on)
                }
                TavernCheckRow(
                    label = stringResource(R.string.settings_alerts_bb),
                    detail = stringResource(R.string.settings_alerts_bb_desc),
                    checked = bbAlerts,
                ) { on ->
                    bbAlerts = on
                    Notifier.setBuilderBaseAlerts(context, on)
                }
                TavernCheckRow(
                    label = stringResource(R.string.settings_alerts_helpers),
                    detail = stringResource(R.string.settings_alerts_helpers_desc),
                    checked = helperAlerts,
                ) { on ->
                    helperAlerts = on
                    Notifier.setHelperAlerts(context, on)
                }
            }

            var liveNotifOn by remember { mutableStateOf(LiveUpdateAlert.enabled(context)) }
            var liveNotifMins by remember { mutableIntStateOf(LiveUpdateAlert.minutes(context)) }
            val liveNotifScope = rememberCoroutineScope()
            val canPromoteLive = remember(resumeTick, permissionTick) { Notifier.canPromoteLive(context) }
            SwitchRow(stringResource(R.string.settings_live_notif), liveNotifOn) { on ->
                liveNotifOn = on
                LiveUpdateAlert.setEnabled(context, on)
                liveNotifScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(
                stringResource(R.string.settings_live_notif_desc, liveNotifMins),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            if (liveNotifOn) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    LiveUpdateAlert.CHOICES.forEach { mins ->
                        TavernFilterPill(
                            label = stringResource(R.string.unit_min_short, mins),
                            selected = liveNotifMins == mins,
                            onClick = {
                                liveNotifMins = mins
                                LiveUpdateAlert.setMinutes(context, mins)
                                liveNotifScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                            },
                        )
                    }
                }
                if (settings.notificationsEnabled && !canPromoteLive && Build.VERSION.SDK_INT >= 36) {
                    Text(stringResource(R.string.settings_live_notif_blocked), color = MaterialTheme.colorScheme.error)
                    Clash3DButton(
                        onClick = { openLivePromotionSettings(context); permissionTick++ },
                        style = ClashButtonStyle.GOLD,
                    ) {
                        ClashOutlinedText(stringResource(R.string.settings_allow_live_notif), fontSize = 13.5.sp)
                    }
                }
                if (Build.MANUFACTURER.equals("samsung", ignoreCase = true) && Build.VERSION.SDK_INT >= 35) {
                    Text(
                        stringResource(R.string.settings_samsung_live_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyColor,
                    )
                    Clash3DButton(
                        onClick = { openDeveloperOptions(context) },
                        style = ClashButtonStyle.STONE,
                    ) {
                        ClashOutlinedText(stringResource(R.string.settings_open_dev_options), fontSize = 13.sp)
                    }
                }
            }

            var respectDnd by remember { mutableStateOf(Notifier.respectDnd(context)) }
            SwitchRow(stringResource(R.string.settings_follow_dnd), respectDnd) { on ->
                respectDnd = on
                Notifier.setRespectDnd(context, on)
            }
            Text(stringResource(R.string.settings_follow_dnd_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            var idleOn by remember { mutableStateOf(IdleAlert.enabled(context)) }
            var idleHours by remember { mutableIntStateOf(IdleAlert.hours(context)) }
            val idleScope = rememberCoroutineScope()
            SwitchRow(stringResource(R.string.settings_idle_alert), idleOn) { on ->
                idleOn = on
                IdleAlert.setEnabled(context, on)
                idleScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
            }
            Text(stringResource(R.string.settings_idle_alert_desc, idleHours), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            if (idleOn) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    IdleAlert.CHOICES.forEach { h ->
                        TavernFilterPill(
                            label = "$h h",
                            selected = idleHours == h,
                            onClick = {
                                idleHours = h
                                IdleAlert.setHours(context, h)
                                idleScope.launch { SyncRunner.refreshWidgets(context.applicationContext, "settings") }
                            },
                        )
                    }
                }
            }
            if (settings.notificationsEnabled && !notificationsAllowed) {
                Text(stringResource(R.string.settings_notif_blocked), color = MaterialTheme.colorScheme.error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Clash3DButton(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, style = ClashButtonStyle.GOLD) {
                        ClashOutlinedText(stringResource(R.string.settings_allow_notif), fontSize = 13.5.sp)
                    }
                }
                Clash3DButton(onClick = { openNotificationSettings(context); permissionTick++ }, style = ClashButtonStyle.STONE) {
                    ClashOutlinedText(stringResource(R.string.settings_notif_settings), fontSize = 13.5.sp)
                }
            }
            Text(
                stringResource(R.string.settings_battery_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            Clash3DButton(onClick = { openAppSettings(context) }, style = ClashButtonStyle.STONE) {
                ClashOutlinedText(stringResource(R.string.settings_battery_btn), fontSize = 13.5.sp)
            }
        }
    }

    item(key = "settings_event_alerts") {
        SectionCard(stringResource(R.string.ev_settings_title)) {
            EventAlertsControlsContent()
        }
    }

    item(key = "settings_builders") {
        SectionCard(stringResource(R.string.settings_section_builders)) {
            val derived = export?.derivedTotalBuilders ?: AppSettings.DEFAULT_TOTAL_BUILDERS
            NumberSetting(
                title = stringResource(R.string.settings_total_builders),
                value = settings.totalBuildersOverride,
                automatic = derived,
                range = 1..10,
                unit = stringResource(R.string.unit_builders),
                description = stringResource(R.string.settings_builders_desc),
                automaticLabel = stringResource(R.string.settings_auto_builders),
            ) { value -> onChange { it.copy(totalBuildersOverride = value) } }
            SwitchRow(stringResource(R.string.settings_boost_goblin), b.boostsApplyToGoblin) { on ->
                boosts { it.copy(boostsApplyToGoblin = on) }
            }
        }
    }

    item(key = "settings_potions") {
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.settings_section_potions)) {
            Text(stringResource(R.string.settings_potions_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            HoursSetting(stringResource(R.string.potion_builder), b.builderPotionSavingSec, 1..47) { value -> boosts { it.copy(builderPotionSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_bite), b.builderBiteSavingSec, 1..23) { value -> boosts { it.copy(builderBiteSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_soup), b.studySoupSavingSec, 1..23) { value -> boosts { it.copy(studySoupSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_research), b.researchPotionSavingSec, 1..47) { value -> boosts { it.copy(researchPotionSavingSec = value) } }
            HoursSetting(stringResource(R.string.potion_pet), b.petPotionSavingSec, 1..47) { value -> boosts { it.copy(petPotionSavingSec = value) } }
        }
    }

    item(key = "settings_helpers") {
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.settings_section_helpers)) {
            Text(stringResource(R.string.settings_helpers_desc), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            NumberSetting(
                title = stringResource(R.string.helper_apprentice),
                value = b.apprenticeSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_BUILDERS_APPRENTICE)?.level,
                range = 1..24,
                unit = stringResource(R.string.unit_hours_per_session),
                automaticLabel = stringResource(R.string.settings_auto_helper),
            ) { value -> boosts { it.copy(apprenticeSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = stringResource(R.string.helper_lab_assistant),
                value = b.labAssistantSavingSecOverride?.div(H)?.toInt(),
                automatic = export?.helper(GameIds.HELPER_LAB_ASSISTANT)?.level,
                range = 1..24,
                unit = stringResource(R.string.unit_hours_per_session),
                automaticLabel = stringResource(R.string.settings_auto_helper),
            ) { value -> boosts { it.copy(labAssistantSavingSecOverride = value?.times(H)) } }
            NumberSetting(
                title = stringResource(R.string.settings_helper_interval),
                value = (b.helperPeriodSec / H).toInt(),
                range = 2..48,
                unit = stringResource(R.string.unit_hours),
                description = stringResource(R.string.settings_interval_desc),
            ) { value -> value?.let { hours -> boosts { it.copy(helperPeriodSec = hours * H) } } }
        }
    }

    item(key = "settings_reset") {
        var confirmReset by rememberSaveable { mutableStateOf(false) }
        Clash3DButton(onClick = { confirmReset = true }, style = ClashButtonStyle.STONE, modifier = Modifier.fillMaxWidth()) {
            ClashOutlinedText(stringResource(R.string.settings_reset), fontSize = 14.sp)
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text(stringResource(R.string.settings_reset_title)) },
                text = { Text(stringResource(R.string.settings_reset_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        onChange {
                            AppSettings(
                                liveCountdown = it.liveCountdown,
                                playerName = it.playerName,
                                widgetTheme = it.widgetTheme,
                                appTheme = it.appTheme,
                                widgetFollowsAppTheme = it.widgetFollowsAppTheme,
                                floatingTabBar = it.floatingTabBar,
                                ecoAnimations = it.ecoAnimations,
                            )
                        }
                        confirmReset = false
                    }) { Text(stringResource(R.string.action_reset)) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** Virtualized visual preferences items for a LazyColumn: 3D app theme, optional widget theme, tab bar, and language. */
fun LazyListScope.personalizationSectionItems(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    item(key = "pers_app_look") {
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.personalization_app_look)) {
            ThemeSelectorGrid(
                selectedTheme = settings.effectiveAppTheme,
                onSelect = { key ->
                    onChange {
                        it.copy(
                            appTheme = key,
                            widgetTheme = if (it.widgetFollowsAppTheme) key else it.widgetTheme,
                        )
                    }
                },
            )
            Text(
                stringResource(R.string.personalization_app_look_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
        }
    }

    item(key = "pers_widgets") {
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.personalization_widgets)) {
            SwitchRow(stringResource(R.string.personalization_widget_follow_app), settings.widgetFollowsAppTheme) { follow ->
                onChange {
                    if (follow) {
                        it.copy(
                            widgetFollowsAppTheme = true,
                            appTheme = it.effectiveAppTheme,
                            widgetTheme = it.effectiveAppTheme,
                        )
                    } else {
                        it.copy(
                            widgetFollowsAppTheme = false,
                            appTheme = it.effectiveAppTheme,
                            widgetTheme = it.effectiveWidgetTheme,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.personalization_widget_follow_app_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            AnimatedVisibility(visible = !settings.widgetFollowsAppTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.personalization_widget_theme),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ThemeSelectorGrid(
                        selectedTheme = settings.effectiveWidgetTheme,
                        onSelect = { key ->
                            onChange {
                                it.copy(
                                    appTheme = it.effectiveAppTheme,
                                    widgetTheme = key,
                                    widgetFollowsAppTheme = false,
                                )
                            }
                        },
                    )
                }
            }
            SwitchRow(stringResource(R.string.settings_live_countdown), settings.liveCountdown) { on ->
                onChange { it.copy(liveCountdown = on) }
            }
            Text(
                stringResource(R.string.settings_live_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
        }
    }

    item(key = "pers_tabbar") {
        val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
        SectionCard(stringResource(R.string.tabbar_title)) {
            SwitchRow(stringResource(R.string.tabbar_floating), settings.floatingTabBar) { on ->
                onChange { it.copy(floatingTabBar = on) }
            }
            Text(
                stringResource(R.string.tabbar_floating_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            SwitchRow(stringResource(R.string.settings_eco_animations), settings.ecoAnimations) { on ->
                onChange { it.copy(ecoAnimations = on) }
            }
            Text(
                stringResource(R.string.settings_eco_animations_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
        }
    }

    item(key = "pers_language") {
        SectionCard(stringResource(R.string.settings_language)) {
            LanguageSetting()
        }
    }
}

@Composable
private fun HoursSetting(title: String, seconds: Long, range: IntRange, onSet: (Long) -> Unit) {
    NumberSetting(
        title = title,
        value = (seconds / H).toInt(),
        range = range,
        unit = stringResource(R.string.unit_hours_saved),
        description = stringResource(R.string.speed_x, seconds / H + 1),
    ) { value -> value?.let { onSet(it * H) } }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int?,
    range: IntRange,
    unit: String,
    description: String? = null,
    automatic: Int? = null,
    automaticLabel: String? = null,
    onSet: (Int?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val valueLabel = when {
        value != null -> stringResource(R.string.value_unit, value, unit.lowercase())
        automatic != null -> stringResource(R.string.automatic_value, automatic, unit.lowercase())
        else -> stringResource(R.string.automatic_missing)
    }
    SettingRow(title, valueLabel, description) { editing = true }
    if (editing) {
        SettingValueDialog(
            title = title,
            initialValue = value ?: automatic ?: range.first,
            range = range,
            unit = unit,
            automaticLabel = automaticLabel,
            onSave = { onSet(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.edit_title, title), onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalChiefPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
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

@Composable
private fun TavernCheckRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalChiefPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = pal.pillActiveTop,
                checkmarkColor = pal.pillActiveText,
                uncheckedColor = pal.cardBorder,
            ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

private fun openLivePromotionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 36) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openNotificationSettings(context) }
    } else {
        openNotificationSettings(context)
    }
}

private fun openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openNotificationSettings(context) }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

private fun openUsageAccessSettings(context: Context) {
    val pkgIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(pkgIntent) }.onFailure {
        val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }.onFailure { openAppSettings(context) }
    }
}


/** Language picker: "System default" plus every language listed in res/xml/locales_config.xml. */
@Composable
private fun LanguageSetting() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.language_system)
    val languages = remember { LanguageManager.available(context) }
    val options = listOf(LanguageManager.SYSTEM to systemLabel) + languages
    val currentLabel = options.firstOrNull { LanguageManager.isSelected(context, it.first) }?.second ?: systemLabel
    SettingRow(stringResource(R.string.language_row), currentLabel, stringResource(R.string.language_desc)) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    options.forEach { (tag, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) {
                                    open = false
                                    chooseLanguage(context, tag)
                                },
                        ) {
                            RadioButton(selected = LanguageManager.isSelected(context, tag), onClick = null)
                            Text(name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun chooseLanguage(context: Context, tag: String) {
    val app = context.applicationContext
    LanguageManager.set(context, tag)
    // Android 13+ recreates the activity itself; older versions need it done here.
    if (Build.VERSION.SDK_INT < 33) {
        var c: Context = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.recreate()
    }
    // Redraw the home-screen widgets in the new language (after the new locale is in effect).
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        runCatching { WidgetPreviews.sync(app, force = true) }
        delay(600)
        runCatching { SyncRunner.refreshWidgets(app, "language") }
    }
}
