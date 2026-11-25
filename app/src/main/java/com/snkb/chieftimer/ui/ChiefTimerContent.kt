@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.snkb.chieftimer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.snkb.chieftimer.companion.CompanionPrefs
import com.snkb.chieftimer.companion.CompanionService
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.AccountMeta
import com.snkb.chieftimer.core.ActiveBoostState
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.AppState
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.EventSettings
import com.snkb.chieftimer.core.RowKind
import com.snkb.chieftimer.core.SyncLog
import com.snkb.chieftimer.core.SyncLogEntry
import com.snkb.chieftimer.core.VillageExport
import com.snkb.chieftimer.core.WidgetModel
import com.snkb.chieftimer.core.WidgetModelBuilder
import com.snkb.chieftimer.data.Names
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.data.Times
import com.snkb.chieftimer.data.WidgetTexts
import com.snkb.chieftimer.sync.GoldPassStore
import com.snkb.chieftimer.ui.theme.Chief3DPalette
import com.snkb.chieftimer.ui.theme.ChiefThemeMode
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class Destination(@StringRes val labelRes: Int, @DrawableRes val tavernIcon: Int) {
    TIMERS(R.string.nav_timers, R.drawable.ic_tavern_nav_timers),
    PLAN(R.string.nav_plan, R.drawable.ic_tavern_nav_plan),
    CLASH(R.string.nav_clash, R.drawable.ic_tavern_nav_clash),
    SETTINGS(R.string.nav_settings, R.drawable.ic_tavern_nav_settings),
}

/** Every swipeable page, in order. The first page of a tab is what its bottom-bar item opens. */
private enum class Page(val dest: Destination, @StringRes val labelRes: Int?) {
    TIMERS(Destination.TIMERS, null),
    PLAN_ROAD(Destination.PLAN, R.string.plan_tab_road),
    PLAN_BEDTIME(Destination.PLAN, R.string.plan_tab_bedtime),
    CLASH_EVENTS(Destination.CLASH, R.string.clash_tab_events),
    SETTINGS(Destination.SETTINGS, R.string.nav_settings),
    PERSONALIZATION(Destination.SETTINGS, R.string.nav_personalization),
    SUPPORT(Destination.SETTINGS, R.string.nav_support),
}

private val SubTabsHeight = 48.dp

/**
 * Slides to [target]. A neighbouring page slides on [spec]. A far page does not sweep past (or flash through)
 * the pages in between: the pager fades out, jumps, and fades back in.
 */
private suspend fun PagerState.goTo(
    target: Int,
    spec: AnimationSpec<Float>,
    fade: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    val from = currentPage
    if (target == from) return
    if (abs(target - from) > 1) {
        fade.animateTo(0f, tween(90))
        scrollToPage(target)
        fade.animateTo(1f, tween(160))
    } else {
        animateScrollToPage(target, animationSpec = spec)
    }
}

private fun Destination.firstPage(): Int = Page.entries.indexOfFirst { it.dest == this }

@Composable
fun ChiefTimerContent(
    state: AppState,
    settings: AppSettings,
    buildModel: (Long) -> WidgetModel,
    log: SyncLog,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    nowState: State<Long>,
    timesCount: Int,
    busy: Boolean,
    onSync: () -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onStateChange: ((AppState) -> AppState) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    UpdatePrompt()
    PermissionPrompt()
    EventsAutoRefresh()
    GameDataAutoRefresh()

    val developerOpenState = rememberSaveable { mutableStateOf(false) }
    var developerOpen by developerOpenState
    val tapScope = rememberCoroutineScope()
    val pager = rememberPagerState { Page.entries.size }
    val pagerFade = remember { Animatable(1f) }
    val currentPage by remember { derivedStateOf { Page.entries[pager.currentPage] } }
    val destination by remember { derivedStateOf { currentPage.dest } }
    val reduced = rememberReducedMotion()
    val powerSave = rememberPowerSaveMode()
    val waveActive = !reduced && !settings.ecoAnimations && !powerSave && (currentPage == Page.TIMERS || currentPage == Page.PLAN_ROAD) && !pager.isScrollInProgress
    val sharedWavePhase = rememberSharedWavePhase(active = waveActive)
    val pageSpring = remember(reduced) {
        if (reduced) tween<Float>(180) else spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 560f)
    }
    val barState = rememberTavernBarScrollState(pager)
    val floatingOn = settings.floatingTabBar
    LaunchedEffect(floatingOn) {
        if (!floatingOn) barState.show()
    }
    val subHide = remember { Animatable(0f) }
    LaunchedEffect(barState.subVisible, reduced) {
        subHide.animateTo(
            targetValue = if (barState.subVisible || reduced) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        )
    }
    val supportTaps = remember { mutableListOf<Long>() }

    val onNavigate: (Destination) -> Unit = { entry ->
        barState.show()
        val targetPage = entry.firstPage()
        if (pager.currentPage != targetPage || pager.currentPageOffsetFraction != 0f) {
            tapScope.launch { pager.goTo(targetPage, pageSpring, pagerFade) }
        }
        if (entry == Destination.SETTINGS && !developerOpenState.value) {
            val t = System.currentTimeMillis()
            supportTaps.add(t)
            supportTaps.removeAll { t - it > 4_000L }
            val left = 11 - supportTaps.size
            when {
                left <= 0 -> {
                    supportTaps.clear()
                    developerOpenState.value = true
                    tapScope.launch {
                        snackbarHost.currentSnackbarData?.dismiss()
                        snackbarHost.showSnackbar("Developer options enabled")
                    }
                }
                left <= 4 -> tapScope.launch {
                    snackbarHost.currentSnackbarData?.dismiss()
                    snackbarHost.showSnackbar(
                        if (left == 1) "1 more tap to enable developer options" else "$left more taps to enable developer options",
                    )
                }
            }
        }
    }

    val screenState = rememberSaveableStateHolder()
    val backEnabled by remember { derivedStateOf { pager.currentPage != 0 } }
    BackHandler(enabled = backEnabled) {
        barState.show()
        tapScope.launch { pager.goTo(0, pageSpring, pagerFade) }
    }

    val pal = LocalChiefPalette.current
    val bgBrush = remember(pal) {
        Brush.verticalGradient(listOf(pal.screenBgTop, pal.screenBgMid, pal.screenBgBot))
    }

    val subPages by remember {
        derivedStateOf { Page.entries.filter { it.dest == destination && it.labelRes != null } }
    }
    val hasSubTabs by remember { derivedStateOf { subPages.size > 1 } }

    BoxWithConstraints(Modifier.fillMaxSize().background(bgBrush)) {
        val wide = maxWidth >= 600.dp
        val floating = floatingOn && !wide
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barReserve = when {
            floating -> 84.dp + navBarInset
            wide -> navBarInset
            else -> 0.dp
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TavernTopHeaderBar(
                    destination = destination,
                    reduced = reduced,
                    showAccountSwitcher = destination == Destination.TIMERS || destination == Destination.PLAN,
                    showOpenGame = destination == Destination.TIMERS,
                    accounts = accounts,
                    selectedTag = selectedTag,
                    onSelectAccount = onSelectAccount,
                    onRenameAccount = onRenameAccount,
                    onDeleteAccount = onDeleteAccount,
                    onSyncNewAccount = onSync,
                    onOpenGameFailed = { msg -> tapScope.launch { snackbarHost.showSnackbar(msg) } },
                )
            },
            bottomBar = {
                if (!floatingOn && !wide) {
                    TavernBottomNavBar(
                        current = destination,
                        barState = barState,
                        reduced = reduced,
                        docked = true,
                        onSelect = onNavigate,
                    )
                }
            },
            snackbarHost = {
                Box(Modifier.padding(bottom = barReserve)) {
                    SnackbarHost(snackbarHost)
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    TavernSideNavRail(current = destination, onSelect = onNavigate)
                }
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CompositionLocalProvider(
                        LocalBarInset provides barReserve,
                        LocalWavePhase provides sharedWavePhase,
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxSize()
                                .clipToBounds()
                                .then(if (!floating || reduced) Modifier else Modifier.nestedScroll(barState.connection)),
                        ) {
                            HorizontalPager(
                                state = pager,
                                key = { Page.entries[it].ordinal },
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pagerFade.value },
                                beyondViewportPageCount = 2,
                                flingBehavior = PagerDefaults.flingBehavior(
                                    state = pager,
                                    snapPositionalThreshold = 0.22f,
                                    snapAnimationSpec = pageSpring,
                                ),
                            ) { index ->
                                val page = Page.entries[index]
                                val pageHasSubTabs = page.dest == Destination.PLAN || page.dest == Destination.SETTINGS
                                val pageActiveState = remember(pager, index) {
                                    derivedStateOf { pager.currentPage == index && !pager.isScrollInProgress }
                                }
                                CompositionLocalProvider(
                                    LocalTopBarInset provides if (pageHasSubTabs) SubTabsHeight else 0.dp,
                                    LocalPageActiveState provides pageActiveState,
                                ) {
                                    Box(Modifier.fillMaxSize().pageScale(pager, index, enabled = !reduced)) {
                                        screenState.SaveableStateProvider(page) {
                                            when (page) {
                                                Page.TIMERS -> TimersPageHost(
                                                    state = state,
                                                    nowState = nowState,
                                                    buildModel = buildModel,
                                                    lastSync = log.lastResult,
                                                    activeBoostState = state.activeBoosts,
                                                    eventSettings = state.eventSettings,
                                                    accounts = accounts,
                                                    selectedTag = selectedTag,
                                                    busy = busy,
                                                    onSync = onSync,
                                                    onStateChange = onStateChange,
                                                    onSelectAccount = onSelectAccount,
                                                    onRenameAccount = onRenameAccount,
                                                    onDeleteAccount = onDeleteAccount,
                                                    wide = wide,
                                                    export = state.export,
                                                )
                                                Page.PLAN_ROAD -> {
                                                    val roadModel = remember(buildModel) { buildModel(0L) }
                                                    PlanPage(state.export, roadModel, 0L, page = 0)
                                                }
                                                Page.PLAN_BEDTIME -> BedtimePageHost(state.export, nowState, buildModel)
                                                Page.CLASH_EVENTS -> EventsPageHost(nowState)
                                                Page.SETTINGS -> LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = listPadding(),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                                ) {
                                                    settingsSectionItems(settings, state.export, onSettingsChange, selectedTag)
                                                    item(key = "game_data") { GameDataCard() }
                                                }
                                                Page.PERSONALIZATION -> LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = listPadding(),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                                ) {
                                                    personalizationSectionItems(settings, onSettingsChange)
                                                }
                                                Page.SUPPORT -> SupportScreen(state, buildModel, log, timesCount)
                                            }
                                        }
                                    }
                                }
                            }
                            var cachedSubPages by remember { mutableStateOf(subPages) }
                            if (hasSubTabs) cachedSubPages = subPages
                            val subVisibleAlpha by animateFloatAsState(
                                targetValue = if (hasSubTabs) 1f else 0f,
                                animationSpec = if (reduced) tween(100) else tween(160),
                                label = "subTabAlpha",
                            )
                            if (cachedSubPages.size > 1 && subVisibleAlpha > 0.01f) {
                                TavernSubTabStrip(
                                    subPages = cachedSubPages,
                                    currentSubPage = currentPage,
                                    hideFraction = if (!floating || reduced) (1f - subVisibleAlpha) else maxOf(subHide.value, 1f - subVisibleAlpha),
                                    interactive = hasSubTabs,
                                    onSelectSubPage = { p ->
                                        barState.show()
                                        tapScope.launch { pager.goTo(p.ordinal, pageSpring, pagerFade) }
                                    },
                                    modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (floating) {
            TavernBottomNavBar(
                current = destination,
                barState = barState,
                reduced = reduced,
                docked = false,
                onSelect = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (developerOpen) DeveloperOptionsScreen(selectedTag, log, onClose = { developerOpen = false })
    }
}

@Composable
private fun TimersPageHost(
    state: AppState,
    nowState: State<Long>,
    buildModel: (Long) -> WidgetModel,
    lastSync: SyncLogEntry?,
    activeBoostState: ActiveBoostState,
    eventSettings: EventSettings,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    busy: Boolean,
    onSync: () -> Unit,
    onStateChange: ((AppState) -> AppState) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    wide: Boolean,
    export: VillageExport?,
) {
    val active = LocalPageActiveState.current.value
    val displayNowState = remember { mutableLongStateOf(nowState.value) }
    var cachedModel by remember { mutableStateOf(buildModel(displayNowState.longValue)) }
    var modelBuiltAt by remember { mutableLongStateOf(displayNowState.longValue) }
    var lastBuilder by remember { mutableStateOf(buildModel) }
    LaunchedEffect(active, buildModel, nowState) {
        val currentNow = nowState.value
        displayNowState.longValue = currentNow
        if (lastBuilder !== buildModel ||
            currentNow < modelBuiltAt ||
            currentNow - modelBuiltAt >= 30L ||
            (cachedModel.nextChangeAt?.let { currentNow >= it } == true)
        ) {
            cachedModel = buildModel(currentNow)
            modelBuiltAt = currentNow
            lastBuilder = buildModel
        }
        if (active) {
            snapshotFlow { nowState.value }.collect { now ->
                displayNowState.longValue = now
                val nextEdge = cachedModel.nextChangeAt
                if (now < modelBuiltAt ||
                    now - modelBuiltAt >= 30L ||
                    (nextEdge != null && now >= nextEdge)
                ) {
                    cachedModel = buildModel(now)
                    modelBuiltAt = now
                }
            }
        }
    }
    val model = cachedModel
    TimerDashboard(
        model = model,
        lastSync = lastSync,
        activeBoostState = activeBoostState,
        eventSettings = eventSettings,
        accounts = accounts,
        selectedTag = selectedTag,
        nowState = displayNowState,
        busy = busy,
        onSync = onSync,
        onUpdateBoosts = { transform ->
            onStateChange { s -> s.copy(activeBoosts = transform(s.activeBoosts)) }
        },
        onUpdateEvents = { transform ->
            onStateChange { s -> s.copy(eventSettings = transform(s.eventSettings)) }
        },
        onSelectAccount = onSelectAccount,
        onRenameAccount = onRenameAccount,
        onDeleteAccount = onDeleteAccount,
        wide = wide,
        export = export,
        appState = state,
    )
}

@Composable
private fun BedtimePageHost(
    export: VillageExport?,
    nowState: State<Long>,
    buildModel: (Long) -> WidgetModel,
) {
    val nowMinute by remember(nowState) { derivedStateOf { nowState.value / 60L } }
    val ts = nowMinute * 60L
    val model = remember(buildModel, nowMinute) { buildModel(ts) }
    PlanPage(export, model, ts, page = 1)
}

@Composable
private fun EventsPageHost(nowState: State<Long>) {
    val nowMinute by remember(nowState) { derivedStateOf { nowState.value / 60L } }
    EventsPage(nowMinute * 60L)
}

@Composable
private fun rememberChromeBgBrush(pal: Chief3DPalette, fallbackColors: () -> List<Color>): Brush =
    when (pal.mode) {
        ChiefThemeMode.CLASH -> rememberWoodBrush()
        ChiefThemeMode.DARK -> rememberDarkWoodBrush()
        else -> remember(pal) { Brush.verticalGradient(fallbackColors()) }
    }

@Composable
private fun TavernTopHeaderBar(
    destination: Destination,
    reduced: Boolean,
    showAccountSwitcher: Boolean,
    showOpenGame: Boolean,
    accounts: List<AccountMeta>,
    selectedTag: String?,
    onSelectAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSyncNewAccount: () -> Unit,
    onOpenGameFailed: (String) -> Unit,
) {
    val pal = LocalChiefPalette.current
    val useTextures = pal.useTextures
    val barBg = rememberChromeBgBrush(pal) { listOf(pal.headerBgTop, pal.headerBgMid, pal.headerBgBot) }
    val topGlow = pal.accentLight.copy(alpha = if (useTextures) 0.16f else 0.09f)
    val context = LocalContext.current
    val density = LocalDensity.current
    val failedMsg = stringResource(R.string.open_game_failed)
    val latestOnOpenGameFailed by rememberUpdatedState(onOpenGameFailed)
    val showChip = showAccountSwitcher && accounts.isNotEmpty()
    val animSpec = remember(reduced) {
        if (reduced) tween<Float>(90) else spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 560f)
    }
    val chipProgress by animateFloatAsState(targetValue = if (showChip) 1f else 0f, animationSpec = animSpec, label = "headerChip")
    val openProgress by animateFloatAsState(targetValue = if (showOpenGame) 1f else 0f, animationSpec = animSpec, label = "headerOpen")
    var openWidthPx by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { 8.dp.toPx() }
    val slideOutPx = with(density) { 28.dp.toPx() }

    Column(
        Modifier
            .zIndex(2f)
            .fillMaxWidth()
            .shadow(6.dp)
            .background(barBg)
            .drawWithCache {
                val sheen = Brush.verticalGradient(
                    when (pal.mode) {
                        ChiefThemeMode.CLASH -> listOf(Color(0x22FFE29E), Color.Transparent, Color(0x3A120802))
                        ChiefThemeMode.DARK -> listOf(Color(0x168BA0C4), Color.Transparent, Color(0x3E05070B))
                        else -> listOf(topGlow, Color.Transparent, Color(0x66020306))
                    },
                )
                onDrawBehind { drawRect(brush = sheen) }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SlideSwitcher(
                index = destination.ordinal,
                reduced = reduced,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .titleEasterEgg(),
            ) { i ->
                val shown = Destination.entries[i]
                ClashOutlinedText(
                    text = if (shown == Destination.TIMERS) stringResource(R.string.app_name) else stringResource(shown.labelRes),
                    fontSize = 23.sp,
                    color = Color.White,
                    strokeWidth = 6f,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (accounts.isNotEmpty()) {
                    Box(
                        Modifier
                            .zIndex(if (showOpenGame) 0f else 1f)
                            .graphicsLayer {
                                alpha = chipProgress
                                val scale = 0.88f + 0.12f * chipProgress
                                scaleX = scale
                                scaleY = scale
                                translationX = ((1f - openProgress) * (openWidthPx + gapPx)) + ((1f - chipProgress) * slideOutPx)
                            },
                    ) {
                        HeaderAccountChip(
                            accounts = accounts,
                            selectedTag = selectedTag,
                            enabled = showChip,
                            onSelect = onSelectAccount,
                            onRename = onRenameAccount,
                            onDelete = onDeleteAccount,
                            onSyncNewAccount = onSyncNewAccount,
                            onOpenGameFailed = { latestOnOpenGameFailed(failedMsg) },
                        )
                    }
                }

                Box(
                    Modifier
                        .onSizeChanged { openWidthPx = it.width }
                        .graphicsLayer {
                            alpha = openProgress
                            val scale = 0.88f + 0.12f * openProgress
                            scaleX = scale
                            scaleY = scale
                            translationX = (1f - openProgress) * slideOutPx
                        },
                ) {
                    OpenGameButton(
                        enabled = showOpenGame,
                        onClick = {
                            if (!showOpenGame) return@OpenGameButton
                            if (CompanionPrefs.enabled(context) && CompanionPrefs.overlayAllowed(context)) {
                                CompanionService.activateManual(context, immediate = false)
                            }
                            if (!openGameLink(context, CLASH_MORE_SETTINGS_LINK)) {
                                latestOnOpenGameFailed(failedMsg)
                            }
                        },
                    )
                }
            }
        }

        // Beveled Metallic Bottom Trim Strip
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(pal.bannerOuter, pal.accentLight, pal.accent, pal.accentDark, pal.bannerOuter),
                    ),
                ),
        )
    }
}

@Composable
private fun TavernSubTabStrip(
    subPages: List<Page>,
    currentSubPage: Page,
    hideFraction: Float,
    interactive: Boolean = true,
    onSelectSubPage: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalChiefPalette.current
    val hasWood = pal.mode == ChiefThemeMode.CLASH || pal.mode == ChiefThemeMode.DARK
    val stripBg = rememberChromeBgBrush(pal) { listOf(pal.headerBgMid, pal.headerBgBot) }
    Column(
        modifier
            .fillMaxWidth()
            .height(SubTabsHeight)
            .graphicsLayer {
                translationY = -SubTabsHeight.toPx() * hideFraction
                alpha = 1f - hideFraction
            }
            .shadow(if (hideFraction < 0.99f) 5.dp else 0.dp)
            .background(stripBg)
            .drawBehind {
                if (hasWood) {
                    drawRect(if (pal.mode == ChiefThemeMode.DARK) Color(0x2A06080D) else Color(0x2E120802))
                }
            },
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            subPages.forEach { p ->
                TavernFilterPill(
                    label = stringResource(p.labelRes!!),
                    selected = currentSubPage == p,
                    onClick = { if (interactive) onSelectSubPage(p) },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(pal.accentDark.copy(alpha = 0.7f)),
        )
    }
}

@Composable
internal fun HeaderAccountChip(
    accounts: List<AccountMeta>,
    selectedTag: String?,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSyncNewAccount: () -> Unit = {},
    onOpenGameFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sheetOpen by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.tag == selectedTag } ?: accounts.firstOrNull()
    val thShort = selectedAccount?.townHall?.let { " · ${stringResource(R.string.widget_th_tag, it)}" } ?: ""
    val label = "${selectedAccount?.displayName ?: ""}$thShort ▾"

    val pal = LocalChiefPalette.current
    val shape = RoundedCornerShape(9.dp)
    val chipOuter = pal.chipOuter
    val rimTop = pal.chipRimTop
    val rimBot = pal.chipRimBot
    val fillTop = pal.chipFillTop
    val fillBot = pal.chipFillBot
    Box(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .drawWithCache {
                val r9 = 9.dp.toPx()
                val r8 = 8.dp.toPx()
                val r7 = 7.dp.toPx()
                val p1 = 1.dp.toPx()
                val p2 = 2.dp.toPx()
                val rimBrush = Brush.verticalGradient(listOf(rimTop, rimBot))
                val fillBrush = Brush.verticalGradient(listOf(fillTop, fillBot))
                onDrawBehind {
                    drawRoundRect(chipOuter, cornerRadius = CornerRadius(r9, r9))
                    drawRoundRect(
                        brush = rimBrush,
                        topLeft = Offset(p1, p1),
                        size = Size(size.width - p1 * 2, size.height - p1 * 2),
                        cornerRadius = CornerRadius(r8, r8),
                    )
                    drawRoundRect(
                        brush = fillBrush,
                        topLeft = Offset(p2, p2),
                        size = Size(size.width - p2 * 2, size.height - p2 * 2),
                        cornerRadius = CornerRadius(r7, r7),
                    )
                }
            }
            .let { m -> if (enabled) m.clickable(role = Role.Button) { sheetOpen = true } else m }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        ClashOutlinedText(
            text = label,
            fontSize = 13.sp,
            color = Color.White,
            strokeWidth = 4f,
            maxLines = 1,
        )
    }

    if (sheetOpen) {
        var overviewOpen by rememberSaveable { mutableStateOf(false) }
        var summaries by remember { mutableStateOf<Map<String, AccountLiveSummary>>(emptyMap()) }
        LaunchedEffect(sheetOpen, overviewOpen, accounts) {
            if (!sheetOpen) return@LaunchedEffect
            summaries = withContext(Dispatchers.Default) {
                val repo = StateRepository(context.applicationContext)
                val names = Names.get(context)
                val times = Times.get(context)
                val wtexts = WidgetTexts.get(context)
                val global = repo.currentSettings()
                val now = System.currentTimeMillis() / 1000L
                accounts.associate { acct ->
                    val st = repo.getAccountState(acct.tag)
                    val aset = repo.getAccountSettings(acct.tag)
                    val s = AppSettings(
                        totalBuildersOverride = aset.totalBuildersOverride,
                        boosts = aset.boosts,
                        notificationsEnabled = global.notificationsEnabled,
                        liveCountdown = global.liveCountdown,
                        playerName = aset.playerName,
                    )
                    val m = WidgetModelBuilder.build(
                        st, s, names, times, now, acct.tag,
                        global.effectiveWidgetTheme, wtexts, GoldPassStore.reduction(context, acct.tag),
                    )
                    val nextFree = m.builderRows.filter { it.kind == RowKind.RUNNING && !it.goblin }.minOfOrNull { it.remaining }
                    val labBusy = (listOf(m.labRow) + m.extraLabRows).filter { it.kind == RowKind.RUNNING }.minOfOrNull { it.remaining }
                    val exp = st.export
                    val helpersReady = exp?.helpers?.count { h -> h.level > 0 && (exp.timestamp + h.cooldown - now) <= 0L } ?: 0
                    acct.tag to AccountLiveSummary(
                        buildersBusy = m.buildersBusy,
                        buildersTotal = m.buildersTotal,
                        nextBuilderSec = nextFree,
                        labSec = labBusy,
                        helpersReady = helpersReady,
                    )
                }
            }
        }
        val dayUnit = stringResource(R.string.time_day_short)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClashOutlinedText(
                        text = stringResource(R.string.accounts_title),
                        fontSize = 20.sp,
                        color = TavernGold,
                        strokeWidth = 5f,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (accounts.size >= 2) {
                            TavernFilterPill(
                                label = stringResource(R.string.accounts_overview_btn),
                                selected = overviewOpen,
                                onClick = { overviewOpen = !overviewOpen },
                            )
                        }
                        Text(
                            text = stringResource(R.string.accounts_count, accounts.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (overviewOpen && summaries.isNotEmpty()) {
                    TavernStoneCard(contentPadding = PaddingValues(12.dp)) {
                        ClashOutlinedText(
                            text = stringResource(R.string.accounts_overview_title),
                            fontSize = 15.5.sp,
                            color = TavernGoldLight,
                            strokeWidth = 4f,
                        )
                        accounts.forEach { acct ->
                            val s = summaries[acct.tag] ?: return@forEach
                            val freeB = s.buildersBusy < s.buildersTotal
                            val builderStatus = if (freeB) stringResource(R.string.stat_free_now)
                            else s.nextBuilderSec?.let { stringResource(R.string.accounts_overview_free_in, Countdown.text(it, dayUnit)) } ?: "—"
                            val labStatus = s.labSec?.let { Countdown.text(it, dayUnit) } ?: stringResource(R.string.stat_free_now)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        sheetOpen = false
                                        onSelect(acct.tag)
                                    }
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "${acct.displayName}${acct.townHall?.let { " · CV$it" }.orEmpty()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "🔨 ${stringResource(R.string.accounts_overview_builders, s.buildersBusy, s.buildersTotal)} · 🧪 $labStatus" +
                                            if (s.helpersReady > 0) " · ${stringResource(R.string.accounts_overview_helpers, s.helpersReady)}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = builderStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (freeB) TavernGreenLight else TavernGoldLight,
                                )
                            }
                        }
                    }
                }

                accounts.forEach { acct ->
                    val isCurrent = acct.tag == selectedAccount?.tag
                    val thBmp = rememberTownHallIcon(acct.townHall, 48.dp, minPx = 160)
                    val summary = summaries[acct.tag]
                    TavernStoneCard(
                        highlighted = isCurrent,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.clickable {
                            sheetOpen = false
                            onSelect(acct.tag)
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thBmp != null) {
                                    Image(
                                        bitmap = thBmp,
                                        contentDescription = null,
                                        filterQuality = FilterQuality.High,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                ClashOutlinedText(
                                    text = acct.displayName,
                                    fontSize = 16.sp,
                                    color = if (isCurrent) TavernGoldLight else Color.White,
                                    strokeWidth = 4.2f,
                                )
                                Text(
                                    text = listOfNotNull(
                                        acct.townHall?.let { stringResource(R.string.town_hall, it) },
                                        acct.tag,
                                        summary?.let { "🔨 ${it.buildersBusy}/${it.buildersTotal}" },
                                    ).joinToString(" \u00B7 "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = TavernGold,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        if (isCurrent) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Clash3DButton(
                                    onClick = { sheetOpen = false; showRenameDialog = true },
                                    style = ClashButtonStyle.STONE,
                                    minHeight = 38.dp,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    ClashOutlinedText(stringResource(R.string.action_rename), fontSize = 13.sp)
                                }
                                if (accounts.size > 1) {
                                    Clash3DButton(
                                        onClick = { sheetOpen = false; showDeleteConfirm = true },
                                        style = ClashButtonStyle.STONE,
                                        minHeight = 38.dp,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        ClashOutlinedText(
                                            text = stringResource(R.string.action_delete),
                                            fontSize = 13.sp,
                                            color = Color(0xFFFF9B9B),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ClashOutlinedText(
                    text = stringResource(R.string.accounts_add),
                    fontSize = 15.5.sp,
                    color = TavernGold,
                    strokeWidth = 4f,
                )
                Text(
                    stringResource(R.string.accounts_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Clash3DButton(
                        onClick = {
                            sheetOpen = false
                            showAddAccountDialog = true
                        },
                        style = ClashButtonStyle.GOLD,
                        modifier = Modifier.weight(1f),
                    ) {
                        ClashOutlinedText(stringResource(R.string.action_add_account), fontSize = 13.sp, maxLines = 1)
                    }
                    Clash3DButton(
                        onClick = {
                            sheetOpen = false
                            onSyncNewAccount()
                        },
                        style = ClashButtonStyle.GREEN,
                        modifier = Modifier.weight(1f),
                    ) {
                        ClashOutlinedText(stringResource(R.string.shortcut_sync_short), fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showAddAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = {
                ClashOutlinedText(
                    text = stringResource(R.string.add_account_title),
                    fontSize = 19.sp,
                    color = TavernGold,
                    strokeWidth = 5f,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.add_account_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.add_account_step1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.add_account_step2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Clash3DButton(
                        onClick = {
                            if (!openGameLink(context, CLASH_MORE_SETTINGS_LINK)) {
                                showAddAccountDialog = false
                                onOpenGameFailed()
                            }
                        },
                        style = ClashButtonStyle.GOLD,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ClashOutlinedText(stringResource(R.string.open_game_settings), fontSize = 13.5.sp)
                    }
                    Clash3DButton(
                        onClick = {
                            showAddAccountDialog = false
                            onSyncNewAccount()
                        },
                        style = ClashButtonStyle.GREEN,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ClashOutlinedText(stringResource(R.string.sync_from_clipboard), fontSize = 13.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text(stringResource(R.string.action_close), color = TavernGold)
                }
            },
        )
    }

    if (showRenameDialog && selectedAccount != null) {
        PlayerNameDialog(
            current = selectedAccount.name,
            firstLaunch = false,
            onDone = { newName ->
                onRename(selectedAccount.tag, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm && selectedAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_account_title, selectedAccount.tag)) },
            text = { Text(stringResource(R.string.delete_account_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(selectedAccount.tag)
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private data class AccountLiveSummary(
    val buildersBusy: Int,
    val buildersTotal: Int,
    val nextBuilderSec: Long?,
    val labSec: Long?,
    val helpersReady: Int,
)

@Composable
private fun TavernBottomNavBar(
    current: Destination,
    barState: TavernBarScrollState,
    reduced: Boolean,
    docked: Boolean = false,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalChiefPalette.current
    val latestOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val travel = with(density) { 96.dp.toPx() } + navBottom
    val hide = remember { Animatable(0f) }
    LaunchedEffect(barState, reduced, docked) {
        if (docked) {
            hide.snapTo(0f)
        } else {
            snapshotFlow { barState.visible }.collectLatest { visible ->
                val target = if (visible || reduced) 0f else 1f
                if (reduced) hide.snapTo(target)
                else hide.animateTo(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
            }
        }
    }

    val hasWood = pal.mode == ChiefThemeMode.CLASH || pal.mode == ChiefThemeMode.DARK
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dockBg = rememberChromeBgBrush(pal) { listOf(pal.dockBgTop, pal.dockBgBot) }
    val accentLight = pal.accentLight
    val accent = pal.accent
    val accentDark = pal.accentDark
    val navIndicatorTop = pal.navIndicatorTop
    val navIndicatorBot = pal.navIndicatorBot
    Column(
        modifier
            .graphicsLayer { translationY = hide.value * travel }
            .fillMaxWidth()
            .shadow(12.dp)
            .background(dockBg)
            .drawBehind {
                if (hasWood) {
                    drawRect(if (pal.mode == ChiefThemeMode.DARK) Color(0x3606080D) else Color(0x3E1A0E05))
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(pal.bannerOuter, accentLight, accent, accentDark),
                    ),
                ),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val spacing = 6.dp
            val count = Destination.entries.size
            val tabWidth = (maxWidth - spacing * (count - 1)) / count
            val indicatorX by animateDpAsState(
                targetValue = (tabWidth + spacing) * current.ordinal,
                animationSpec = if (reduced) tween(120) else spring(dampingRatio = 0.75f, stiffness = 430f),
                label = "navPillX",
            )

            Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = indicatorX.toPx() }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind {
                            val r = 12.dp.toPx()
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(accentLight, accent, accentDark)),
                                cornerRadius = CornerRadius(r, r),
                            )
                            val p = 1.5.dp.toPx()
                            val innerH = (size.height - p * 2).coerceAtLeast(2f)
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(navIndicatorTop, navIndicatorBot)),
                                topLeft = Offset(p, p),
                                size = Size(size.width - p * 2, innerH),
                                cornerRadius = CornerRadius(r - p, r - p),
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.10f),
                                topLeft = Offset(p + 3.dp.toPx(), p + 1.5.dp.toPx()),
                                size = Size((size.width - p * 2 - 6.dp.toPx()).coerceAtLeast(2f), innerH * 0.36f),
                                cornerRadius = CornerRadius(r - p, r - p),
                            )
                        },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Destination.entries.forEach { entry ->
                        val selected = current == entry
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.15f else 0.94f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "navIconScale",
                        )
                        val iconLift by animateDpAsState(
                            targetValue = if (selected) (-1.5).dp else 1.dp,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                            label = "navIconLift",
                        )
                        val iconAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.68f,
                            animationSpec = tween(220),
                            label = "navIconAlpha",
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (selected) TavernGoldLight else secondaryColor,
                            animationSpec = tween(220),
                            label = "navLabelColor",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                ) { latestOnSelect(entry) }
                                .padding(vertical = 7.dp, horizontal = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Image(
                                    painter = painterResource(entry.tavernIcon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(27.dp)
                                        .graphicsLayer {
                                            translationY = iconLift.toPx()
                                            scaleX = iconScale
                                            scaleY = iconScale
                                            alpha = iconAlpha
                                        },
                                )
                                ClashOutlinedText(
                                    text = stringResource(entry.labelRes),
                                    fontSize = 11.5.sp,
                                    color = labelColor,
                                    strokeWidth = if (selected) 4.2f else 3.4f,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TavernSideNavRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    val pal = LocalChiefPalette.current
    val latestOnSelect by rememberUpdatedState(onSelect)
    val hasWood = pal.mode == ChiefThemeMode.CLASH || pal.mode == ChiefThemeMode.DARK
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val railBg = rememberChromeBgBrush(pal) { listOf(pal.dockBgTop, pal.dockBgBot) }
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(railBg)
                .drawBehind {
                    if (hasWood) {
                        drawRect(if (pal.mode == ChiefThemeMode.DARK) Color(0x3606080D) else Color(0x3E1A0E05))
                    }
                }
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Destination.entries.forEach { entry ->
                val selected = current == entry
                val shape = RoundedCornerShape(12.dp)
                val bgColor by animateColorAsState(
                    targetValue = if (selected) pal.accent.copy(alpha = 0.30f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "railBg",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.14f else 0.94f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "railScale",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { latestOnSelect(entry) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(
                        painter = painterResource(entry.tavernIcon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                alpha = if (selected) 1f else 0.72f
                            },
                    )
                    ClashOutlinedText(
                        text = stringResource(entry.labelRes),
                        fontSize = 11.5.sp,
                        color = if (selected) TavernGoldLight else secondaryColor,
                        strokeWidth = 3.5f,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(pal.accentLight, pal.accent, pal.accentDark))),
        )
    }
}
