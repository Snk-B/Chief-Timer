@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.SyncLog
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.sync.SyncRunner
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.snkb.chieftimer.sync.AppIcon
import com.snkb.chieftimer.sync.ClipboardAutoSync
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.notify.LiveCountdownService
import com.snkb.chieftimer.notify.Notifier
import com.snkb.chieftimer.sync.LiveUpdateAlert

/** Hidden developer options. Opened by tapping the Support tab 11 times in 4 seconds; leaving the page hides it again. */
@Composable
fun DeveloperOptionsScreen(selectedTag: String?, log: SyncLog, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Developer options") },
                    navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "For testing. Some options are experimental and can restart the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                SectionCard("Live notification") {
                    var liveStatus by remember { mutableStateOf("") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!Notifier.allowed(context)) {
                                    liveStatus = "Notifications are blocked in Android settings."
                                } else if (!LiveUpdateAlert.enabled(context)) {
                                    liveStatus = "Enable 'Live notification' in Settings first."
                                } else {
                                    val app = context.applicationContext
                                    val nowSec = System.currentTimeMillis() / 1000
                                    val lctx = LanguageManager.wrap(app)
                                    val queenName = Names.get(app).name(28000001)
                                    val sample = Notifier.LiveCountdownItem(
                                        accountTag = selectedTag ?: "#TEST",
                                        accountName = "Chief",
                                        jobKey = "dev_test_live",
                                        shortName = queenName,
                                        label = "$queenName → 63",
                                        categoryTitle = "${lctx.getString(R.string.filter_builders)} (6/6)",
                                        finishAt = nowSec + 25L,
                                        capturedLeftSec = 30L,
                                        progress = 0.80f,
                                        iconIds = listOf(28000001),
                                        iconLevel = null,
                                        accentColor = Notifier.LIVE_PILL_ORANGE,
                                    )
                                    LiveCountdownService.updateOrStop(app, listOf(sample), multipleAccounts = false)
                                    liveStatus = "Started 25s live countdown test — pull down shade or go Home to inspect!"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Test live (25s)") }
                        OutlinedButton(
                            onClick = {
                                LiveCountdownService.updateOrStop(context.applicationContext, emptyList(), false)
                                liveStatus = "Stopped."
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Stop test") }
                    }
                    if (liveStatus.isNotEmpty()) {
                        Text(liveStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }

                SectionCard("App icon") {
                    var themed by remember { mutableStateOf(AppIcon.themed(context)) }
                    fun pick(value: Boolean) {
                        if (value == themed) return
                        themed = value
                        runCatching { AppIcon.setThemed(context, value) }
                    }
                    IconChoice("Follow my phone (default)", "Uses your phone's themed icons setting (Android 13+).", themed) { pick(true) }
                    IconChoice("Always colour", "The full colour icon, never tinted.", !themed) { pick(false) }
                    Text(
                        "Your launcher may take a few seconds to update and can briefly show both icons or close the app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                SectionCard("Gold Pass") {
                    Text("Forget the saved Gold Pass answer for this village and ask again, as at the start of a new month.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { GoldPassStore.reset(context, selectedTag) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset Gold Pass popup")
                    }
                }

                SectionCard("Clipboard") {
                    var autoClip by remember { mutableStateOf(ClipboardAutoSync.enabled(context)) }
                    SwitchRow(stringResource(R.string.settings_auto_clipboard), autoClip) { on ->
                        autoClip = on
                        ClipboardAutoSync.setEnabled(context, on)
                    }
                    Text(stringResource(R.string.settings_auto_clipboard_desc), style = MaterialTheme.typography.bodyMedium)
                }

                SectionCard("Open a game link") {
                    var link by remember { mutableStateOf(CLASH_MORE_SETTINGS_LINK) }
                    var result by remember { mutableStateOf("") }
                    androidx.compose.material3.OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; result = "" },
                        label = { Text("link.clashofclans.com link") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { result = if (openGameLink(context, link)) "Opened" else "Nothing could open this link" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open") }
                    if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "To try other parameters, for example one that scrolls to Export Data. Only https links are opened.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                CollapsibleCard("${stringResource(R.string.support_recent_activity)} (${log.entries.size})", "dev_activity") {
                    if (log.entries.isEmpty()) {
                        Text(stringResource(R.string.support_no_activity))
                    } else {
                        val activityText = log.entries.joinToString("\n") { SyncRunner.plainText(it.line()) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { copyReport(context, activityText) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("Copy") }
                            TextButton(
                                onClick = { shareReport(context, activityText) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("Share") }
                            TextButton(
                                onClick = { scope.launch { StateRepository(context.applicationContext).clearLog() } },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(stringResource(R.string.support_clear_activity)) }
                        }
                    }
                    log.entries.forEach { entry ->
                        SelectionContainer {
                            Text(
                                SyncRunner.plainText(entry.line()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Hide developer options") }
            }
        }
    }
}

@Composable
private fun IconChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
