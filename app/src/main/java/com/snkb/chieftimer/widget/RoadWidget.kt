@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.AppSettings
import com.snkb.chieftimer.core.Countdown
import com.snkb.chieftimer.core.RoadToMax
import com.snkb.chieftimer.core.RoadToMaxCalc
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.sync.SyncActivity
import com.snkb.chieftimer.ui.Clash3DButton
import com.snkb.chieftimer.ui.ClashButtonStyle
import com.snkb.chieftimer.ui.ClashOutlinedText
import com.snkb.chieftimer.ui.TavernFilterPill
import com.snkb.chieftimer.ui.TavernStoneCard
import com.snkb.chieftimer.ui.TavernWoodBanner
import com.snkb.chieftimer.ui.ThemeSelectorGrid
import com.snkb.chieftimer.ui.compact
import com.snkb.chieftimer.ui.rememberStoneBrush
import com.snkb.chieftimer.ui.theme.ChiefThemeMode
import com.snkb.chieftimer.ui.theme.ChiefTimerTheme
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen "Road to max" widget styled in Taverna Clash / Taverna Escura (AMOLED):
 * builder, lab and pet time (and Gold / Elixir / Dark Elixir when wide enough).
 */
class RoadWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                RoadWidgetUpdater.updateAll(context, force = true)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

object RoadWidgetUpdater {
    private const val WIDE_DP = 200
    private val lastDrawn = ConcurrentHashMap<Int, Int>()

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, RoadWidgetReceiver::class.java))

    suspend fun updateAll(context: Context, force: Boolean = false): Int {
        val app = context.applicationContext
        val ids = ids(app)
        if (ids.isEmpty()) return 0
        val repo = StateRepository(app)
        val manager = AppWidgetManager.getInstance(app)
        val accounts = repo.accounts.first()
        val fallback = repo.selectedAccountTag.first() ?: accounts.firstOrNull()?.tag
        val data = MaxDataStore.get(app)
        val renderer = WidgetRenderer(app)
        val applyGp = app.getSharedPreferences("plan_road", Context.MODE_PRIVATE).getBoolean("apply_gold_pass", true)
        val multi = accounts.size > 1
        for (id in ids) {
            val tag = repo.getWidgetAccount(id) ?: fallback
            val name = accounts.firstOrNull { it.tag == tag }?.displayName
            val export = tag?.let { repo.getAccountState(it).export }
            val builders = if (tag == null) 1 else
                AppSettings(totalBuildersOverride = repo.getAccountSettings(tag).totalBuildersOverride).totalBuilders(export)
            val baseRoad = export?.let { RoadToMaxCalc.compute(data, it) }
            val gp = if (applyGp && tag != null) com.snkb.chieftimer.sync.GoldPassStore.reduction(app, tag) else com.snkb.chieftimer.core.GoldPassReduction.NONE
            val road = baseRoad?.withGoldPass(gp)
            val theme = repo.getWidgetTheme(id)
            val widthDp = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val pal = renderer.palette(theme)
            val signature = Objects.hash(road, theme, pal.gold, pal.sub, widthDp >= WIDE_DP, tag, name, builders, multi, Locale.getDefault())
            if (!force && lastDrawn[id] == signature) continue
            runCatching { manager.updateAppWidget(id, render(app, renderer, road, theme, widthDp >= WIDE_DP, name, builders, id, multi)) }
                .onSuccess { lastDrawn[id] = signature }
        }
        return ids.size
    }

    private fun render(
        context: Context,
        renderer: WidgetRenderer,
        road: RoadToMax?,
        theme: String,
        wide: Boolean,
        name: String?,
        builders: Int,
        appWidgetId: Int,
        multipleAccounts: Boolean,
    ): RemoteViews {
        val ctx = LanguageManager.wrap(context)
        val t = renderer.palette(theme)
        val icons = WidgetIcons.get(context)
        val density = context.resources.displayMetrics.density
        val iconPx = (26 * density).toInt() * 2
        val rv = RemoteViews(context.packageName, R.layout.widget_road)
        rv.setInt(R.id.panel, "setBackgroundResource", t.panel)
        rv.setInt(R.id.header, "setBackgroundResource", t.headerBg)
        rv.setTextColor(R.id.who, t.text)
        rv.setTextColor(R.id.empty, t.text)

        val thBmp = road?.let { icons.bitmapForLevel(1000001, it.townHall, iconPx) }
        if (thBmp != null) {
            rv.setImageViewBitmap(R.id.who_th_icon, thBmp)
            rv.setViewVisibility(R.id.who_th_icon, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.who_th_icon, View.GONE)
        }

        if (road == null) {
            rv.setTextViewText(R.id.who, listOfNotNull(name, ctx.getString(R.string.road_widget_title)).joinToString(" · "))
            rv.setViewVisibility(R.id.body, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setTextViewText(R.id.empty, ctx.getString(R.string.road_widget_empty))
            rv.setOnClickPendingIntent(R.id.panel, syncIntent(context))
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.body, View.VISIBLE)
        val thPrefix = ctx.getString(R.string.widget_th_tag, road.townHall)
        val switchTag = if (multipleAccounts) " ⇄" else null
        val head = listOfNotNull(name, ctx.getString(R.string.road_widget_title), thPrefix).joinToString(" · ") + (switchTag ?: "")
        rv.setTextViewText(R.id.who, head)
        rv.setOnClickPendingIntent(R.id.panel, openAppIntent(context))
        if (multipleAccounts && appWidgetId > 0) {
            val sw = switchRoadAccountIntent(context, appWidgetId)
            rv.setOnClickPendingIntent(R.id.who, sw)
            rv.setOnClickPendingIntent(R.id.who_th_icon, sw)
        }

        fun row(label: String, value: String, building: Int?, drawable: Int?): RemoteViews {
            val r = RemoteViews(context.packageName, R.layout.widget_road_row)
            r.setInt(R.id.row, "setBackgroundResource", t.rowSlot)
            r.setTextViewText(R.id.label, label)
            r.setTextColor(R.id.label, t.sub)
            r.setTextViewText(R.id.value, value)
            r.setTextColor(R.id.value, t.gold)
            val bmp = building?.let { icons.bitmapForLevel(it, null, iconPx) }
            when {
                bmp != null -> r.setImageViewBitmap(R.id.icon, bmp)
                drawable != null -> r.setImageViewResource(R.id.icon, drawable)
                else -> r.setViewVisibility(R.id.icon, View.GONE)
            }
            return r
        }

        val day = ctx.getString(R.string.time_day_short)
        val all = road.builderWork + road.lab + road.pets
        rv.removeAllViews(R.id.col_time)
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_widget_builders), duration(road.builderSeconds(builders), day), 1000015, R.drawable.road_builders))
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_lab), duration(road.lab.seconds, day), 1000007, R.drawable.road_lab))
        rv.addView(R.id.col_time, row(ctx.getString(R.string.road_pets), duration(road.pets.seconds, day), 1000068, R.drawable.road_pets))

        rv.removeAllViews(R.id.col_res)
        if (wide) {
            rv.setViewVisibility(R.id.col_res, View.VISIBLE)
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_gold), compact(all.gold), null, R.drawable.road_gold))
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_elixir), compact(all.elixir), null, R.drawable.road_elixir))
            rv.addView(R.id.col_res, row(ctx.getString(R.string.resource_dark_elixir), compact(all.darkElixir), null, R.drawable.road_dark_elixir))
        } else {
            rv.setViewVisibility(R.id.col_res, View.GONE)
        }
        return rv
    }

    private fun duration(seconds: Long, dayUnit: String): String {
        val days = seconds / 86_400
        return if (days >= 1) String.format(Locale.US, "%,d %s", days, dayUnit) else Countdown.text(seconds, dayUnit)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.snkb.chieftimer.MainActivity::class.java)
        return PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun switchRoadAccountIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetInternalReceiver::class.java)
            .setAction(ChiefWidgetReceiver.ACTION_SWITCH_ROAD_ACCOUNT)
            .putExtra(ChiefWidgetReceiver.EXTRA_WIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            ("switch_road:$appWidgetId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun syncIntent(context: Context): PendingIntent {
        val intent = Intent(context, SyncActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(SyncActivity.EXTRA_WIDGET_TAP, true)
        return PendingIntent.getActivity(context, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Setup screen for the Road to max widget: Taverna Clash theme + account selector. */
class RoadWidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)
        setContent {
            var selectedTheme by rememberSaveable { mutableStateOf(ChiefThemeMode.CLASH.key) }
            ChiefTimerTheme(themeKey = selectedTheme) {
                val pal = LocalChiefPalette.current
                val bgBrush = if (!pal.useTextures) {
                    Brush.verticalGradient(listOf(pal.screenBgTop, pal.screenBgMid, pal.screenBgBot))
                } else {
                    rememberStoneBrush()
                }
                val context = LocalContext.current
                val density = LocalDensity.current
                val repo = remember { StateRepository(context) }
                val accounts by repo.accounts.collectAsState(initial = emptyList())
                var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }

                LaunchedEffect(appWidgetId) {
                    val existingTheme = repo.getWidgetTheme(appWidgetId)
                    selectedTheme = ChiefThemeMode.fromKey(existingTheme).key
                    selectedTag = repo.getWidgetAccount(appWidgetId) ?: repo.selectedAccountTag.first()
                }
                LaunchedEffect(accounts) {
                    if (selectedTag == null || accounts.none { it.tag == selectedTag }) {
                        selectedTag = accounts.firstOrNull()?.tag
                    }
                }

                Box(Modifier.fillMaxSize().background(bgBrush)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TavernWoodBanner {
                            ClashOutlinedText(
                                text = stringResource(R.string.road_widget_name),
                                fontSize = 20.sp,
                                color = Color.White,
                                strokeWidth = 5.5f,
                            )
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (accounts.isNotEmpty()) {
                                items(accounts) { acct ->
                                    val isSel = acct.tag == selectedTag
                                    val thBmp = remember(acct.townHall) {
                                        val px = maxOf(with(density) { 52.dp.roundToPx() }, 160)
                                        WidgetIcons.get(context).townHall(acct.townHall, px)?.asImageBitmap()
                                    }
                                    TavernStoneCard(
                                        highlighted = isSel,
                                        modifier = Modifier.clickable { selectedTag = acct.tag },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            if (thBmp != null) {
                                                Image(bitmap = thBmp, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.size(44.dp))
                                            }
                                            Column {
                                                ClashOutlinedText(
                                                    text = acct.displayName + (acct.townHall?.let { " · ${stringResource(R.string.widget_th_tag, it)}" } ?: ""),
                                                    fontSize = 16.5.sp,
                                                    color = if (isSel) TavernGold else Color.White,
                                                    strokeWidth = 4.5f,
                                                )
                                                Text(acct.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    TavernStoneCard {
                                        Text(stringResource(R.string.road_widget_empty), color = Color.White)
                                    }
                                }
                            }

                            item {
                                TavernStoneCard {
                                    ClashOutlinedText(
                                        text = stringResource(R.string.widget_theme),
                                        fontSize = 16.5.sp,
                                        color = TavernGold,
                                        strokeWidth = 4.5f,
                                    )
                                    ThemeSelectorGrid(
                                        selectedTheme = selectedTheme,
                                        onSelect = { selectedTheme = it },
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Clash3DButton(
                                onClick = { finish() },
                                style = ClashButtonStyle.STONE,
                                modifier = Modifier.weight(1f),
                            ) {
                                ClashOutlinedText(stringResource(R.string.action_cancel), fontSize = 14.sp)
                            }
                            Clash3DButton(
                                onClick = {
                                    lifecycleScope.launch {
                                        selectedTag?.let { repo.setWidgetAccount(appWidgetId, it) }
                                        repo.setWidgetTheme(appWidgetId, selectedTheme)
                                        RoadWidgetUpdater.updateAll(this@RoadWidgetConfigActivity, force = true)
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    }
                                },
                                style = ClashButtonStyle.GREEN,
                                modifier = Modifier.weight(1f),
                            ) {
                                ClashOutlinedText(stringResource(R.string.action_save), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
