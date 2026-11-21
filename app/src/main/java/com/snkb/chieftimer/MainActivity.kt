@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import com.snkb.chieftimer.ui.SplashOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.AppState
import com.snkb.chieftimer.core.SyncLog
import com.snkb.chieftimer.core.WidgetModelBuilder
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.data.Times
import com.snkb.chieftimer.data.WidgetTexts
import com.snkb.chieftimer.sync.SyncRunner
import com.snkb.chieftimer.ui.ChiefTimerContent
import com.snkb.chieftimer.ui.PlayerNameDialog
import com.snkb.chieftimer.ui.GoldPassDialog
import com.snkb.chieftimer.core.GoldPassLogic
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.sync.ClipboardAutoSync
import androidx.lifecycle.lifecycleScope
import com.snkb.chieftimer.ui.theme.ChiefTimerTheme
import com.snkb.chieftimer.widget.WidgetPreviews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.snkb.chieftimer.locale.LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        syncRequestHandled = savedInstanceState != null
        val playSplash = savedInstanceState == null
        com.snkb.chieftimer.update.UpdateCheckJob.sync(this)
        if (savedInstanceState == null) {
            if (!com.snkb.chieftimer.update.Connectivity.isOnline(this)) {
                android.widget.Toast.makeText(this, R.string.offline_toast, android.widget.Toast.LENGTH_LONG).show()
            }
            lifecycleScope.launch(Dispatchers.Default) {
                runCatching { com.snkb.chieftimer.widget.WidgetUpdater.updateAll(applicationContext, "app") }
            }
        }
        val rootRepo = StateRepository(this)
        setContent {
            val rootSettings by rootRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            ChiefTimerTheme(themeKey = rootSettings.effectiveAppTheme) {
                var splash by rememberSaveable { mutableStateOf(playSplash) }
                Box(Modifier.fillMaxSize()) {
                    ChiefTimerApp()
                    AnimatedVisibility(
                        visible = splash,
                        exit = fadeOut(tween(320)) + scaleOut(targetScale = 1.12f, animationSpec = tween(320)),
                    ) { SplashOverlay { splash = false } }
                }
            }
        }
    }

    private var syncRequested = false
    private var syncRequestHandled = false

    override fun onResume() {
        super.onResume()
        com.snkb.chieftimer.companion.CompanionService.onHostResumed(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        syncRequested = intent.getBooleanExtra(EXTRA_SYNC_NOW, false)
    }

    /** Android 10+ only lets the focused app read the clipboard, so the check waits for window focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (intent?.getBooleanExtra(EXTRA_SYNC_NOW, false) == true && !syncRequestHandled) {
            syncRequested = true
            syncRequestHandled = true
        }
        if (syncRequested) {
            syncRequested = false
            val text = SyncRunner.readClipboard(this)
            ClipboardAutoSync.markUsed(this, text)
            lifecycleScope.launch {
                val r = runCatching { SyncRunner.sync(this@MainActivity, text, "widget") }.getOrNull()
                if (r != null && !r.ok) android.widget.Toast.makeText(this@MainActivity, r.message, android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!ClipboardAutoSync.enabled(this)) return
        val clip = ClipboardAutoSync.pending(this, SyncRunner.readClipboard(this)) ?: return
        ClipboardAutoSync.markUsed(this, clip)
        lifecycleScope.launch { runCatching { SyncRunner.sync(this@MainActivity, clip, "auto") } }
    }

    companion object {
        const val EXTRA_SYNC_NOW = "sync_now"
    }
}

private data class ScreenData(val state: AppState, val settings: AppSettings, val log: SyncLog)
private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: ScreenData) : LoadState
    data class Failed(val message: String) : LoadState
}

@Composable
private fun ChiefTimerApp() {
    val context = LocalContext.current
    val repo = remember { StateRepository(context) }
    val dataVersion = com.snkb.chieftimer.data.MaxDataStore.version
    val names = remember(dataVersion) { Names.get(context) }
    val times = remember(dataVersion) { Times.get(context) }
    val widgetText = remember { WidgetTexts.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var retry by remember { mutableIntStateOf(0) }

    // Accounts synced before the Town Hall picture existed get their level filled in once.
    LaunchedEffect(Unit) { repo.backfillTownHall() }
    LaunchedEffect(Unit) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching { com.snkb.chieftimer.data.MaxDataStore.get(app) }
            runCatching { com.snkb.chieftimer.update.EventsStore.load(app) }
            runCatching { com.snkb.chieftimer.update.ClashKingDatesStore.load(app) }
            runCatching { com.snkb.chieftimer.widget.WidgetIcons.get(app).warmUp() }
        }
    }
    val accounts by repo.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedTag by repo.selectedAccountTag.collectAsStateWithLifecycle(initialValue = null)

    val dataFlow = remember(repo, retry) {
        combine(repo.state, repo.settings, repo.syncLog, ::ScreenData)
            .map<ScreenData, LoadState> { LoadState.Ready(it) }
            .catch { emit(LoadState.Failed(it.message ?: "Could not read saved village data.")) }
    }
    val loadState by dataFlow.collectAsStateWithLifecycle(initialValue = LoadState.Loading)
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowState.longValue = System.currentTimeMillis() / 1000
                delay(1_000)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }

    when (val loaded = loadState) {
        LoadState.Loading -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingIndicator()
                Text("Loading your village…", Modifier.padding(top = 16.dp))
            }
        }
        is LoadState.Failed -> Surface {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Could not load your village", style = MaterialTheme.typography.titleLarge)
                Text(loaded.message)
                Button(onClick = { retry++ }) { Text("Try again") }
            }
        }
        is LoadState.Ready -> {
            val (state, settings, log) = loaded.data
            val previewLang = com.snkb.chieftimer.locale.LanguageManager.language(context)
            LaunchedEffect(settings.effectiveWidgetTheme, previewLang) {
                withContext(Dispatchers.Default) {
                    runCatching { WidgetPreviews.update(context, settings.effectiveWidgetTheme) }
                }
            }
            val goldPass = remember(selectedTag, GoldPassStore.version) { GoldPassStore.reduction(context, selectedTag) }
            val buildModel = remember(state, settings, names, times, selectedTag, widgetText, goldPass) {
                { ts: Long -> WidgetModelBuilder.build(state, settings, names, times, ts, selectedTag ?: "", text = widgetText, goldPass = goldPass) }
            }
            val changeSettings: ((AppSettings) -> AppSettings) -> Unit = { change ->
                scope.launch {
                    try {
                        repo.updateSettings(change)
                        SyncRunner.refreshWidgets(context, "app")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Could not finish updating settings: ${e.message}")
                    }
                }
            }
            val changeState: ((AppState) -> AppState) -> Unit = { change ->
                scope.launch {
                    try {
                        repo.modifyState(change)
                        SyncRunner.refreshWidgets(context, "app")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Could not update boosts or events: ${e.message}")
                    }
                }
            }
            val month by remember { derivedStateOf { GoldPassLogic.monthKey(nowState.longValue) } }
            var goldPassOpen by remember { mutableStateOf(false) }
            LaunchedEffect(selectedTag, month, GoldPassStore.version, settings.needsPlayerName, state.export != null) {
                if (!settings.needsPlayerName && state.export != null &&
                    GoldPassLogic.needsAsk(GoldPassStore.answer(context, selectedTag).month, month, GoldPassStore.askMonthly(context))
                ) goldPassOpen = true
            }
            if (goldPassOpen) {
                GoldPassDialog(
                    initial = GoldPassStore.answer(context, selectedTag),
                    onSave = { gold, b, r ->
                        GoldPassStore.save(context, selectedTag, month, gold, b, r)
                        goldPassOpen = false
                        scope.launch { SyncRunner.refreshWidgets(context, "app") }
                    },
                    onLater = { goldPassOpen = false },
                )
            }
            if (settings.needsPlayerName && state.export != null) {
                androidx.compose.runtime.key(selectedTag) {
                    val answer = { name: String -> changeSettings { it.copy(playerName = name) } }
                    PlayerNameDialog(null, true, onDone = answer, onDismiss = { answer("") })
                }
            }
            ChiefTimerContent(
                state = state,
                settings = settings,
                buildModel = buildModel,
                log = log,
                accounts = accounts,
                selectedTag = selectedTag,
                nowState = nowState,
                timesCount = times.size,
                busy = busy,
                onSync = {
                    if (!busy) {
                        busy = true
                        val clipboard = SyncRunner.readClipboard(context)
                        ClipboardAutoSync.markUsed(context, clipboard)
                        scope.launch {
                            try {
                                SyncRunner.sync(context, clipboard, "app")
                                nowState.longValue = System.currentTimeMillis() / 1000
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not finish syncing: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                onSettingsChange = changeSettings,
                onStateChange = changeState,
                onSelectAccount = { tag -> scope.launch { repo.selectAccount(tag) } },
                onRenameAccount = { tag, name -> scope.launch { repo.renameAccount(tag, name) } },
                onDeleteAccount = { tag -> scope.launch { repo.deleteAccount(tag); SyncRunner.refreshWidgets(context, "app") } },
                snackbarHost = snackbar,
            )
        }
    }
}
