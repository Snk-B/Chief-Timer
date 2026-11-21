@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.Composable
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
import com.snkb.chieftimer.data.StateRepository
import com.snkb.chieftimer.ui.Clash3DButton
import com.snkb.chieftimer.ui.ClashButtonStyle
import com.snkb.chieftimer.ui.ClashOutlinedText
import com.snkb.chieftimer.ui.TavernFilterPill
import com.snkb.chieftimer.ui.TavernStoneCard
import com.snkb.chieftimer.ui.TavernWoodBanner
import com.snkb.chieftimer.ui.ThemeSelectorGrid
import com.snkb.chieftimer.ui.rememberStoneBrush
import com.snkb.chieftimer.ui.theme.ChiefThemeMode
import com.snkb.chieftimer.ui.theme.ChiefTimerTheme
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.snkb.chieftimer.locale.LanguageManager.wrap(newBase))
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        setContent {
            var previewTheme by rememberSaveable { mutableStateOf(ChiefThemeMode.CLASH.key) }
            ChiefTimerTheme(themeKey = previewTheme) {
                val pal = LocalChiefPalette.current
                val bgBrush = if (!pal.useTextures) {
                    Brush.verticalGradient(listOf(pal.screenBgTop, pal.screenBgMid, pal.screenBgBot))
                } else {
                    rememberStoneBrush()
                }
                Box(Modifier.fillMaxSize().background(bgBrush)) {
                    WidgetConfigScreen(
                        appWidgetId = appWidgetId,
                        onThemePreviewChange = { previewTheme = it },
                        onSave = { tag, style, theme ->
                            val repo = StateRepository(this@WidgetConfigActivity)
                            lifecycleScope.launch {
                                repo.setWidgetAccount(appWidgetId, tag)
                                repo.setWidgetTheme(appWidgetId, theme)
                                repo.setWidgetStyle(appWidgetId, style)
                                WidgetUpdater.updateAll(this@WidgetConfigActivity, "config")
                                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigScreen(
    appWidgetId: Int,
    onThemePreviewChange: (String) -> Unit,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val repo = remember { StateRepository(context) }
    val accounts by repo.accounts.collectAsState(initial = emptyList())
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStyle by rememberSaveable { mutableStateOf(StateRepository.WIDGET_STYLE_LIST) }
    var selectedTheme by rememberSaveable { mutableStateOf(ChiefThemeMode.CLASH.key) }

    LaunchedEffect(appWidgetId) {
        val existingStyle = repo.getWidgetStyle(appWidgetId)
        selectedStyle = if (existingStyle == StateRepository.WIDGET_STYLE_TILES) {
            StateRepository.WIDGET_STYLE_TILES
        } else {
            StateRepository.WIDGET_STYLE_LIST
        }
        val existingTheme = repo.getWidgetTheme(appWidgetId)
        selectedTheme = ChiefThemeMode.fromKey(existingTheme).key
        onThemePreviewChange(selectedTheme)
        selectedTag = repo.getWidgetAccount(appWidgetId)
    }
    LaunchedEffect(selectedTheme) {
        onThemePreviewChange(selectedTheme)
    }
    LaunchedEffect(accounts) {
        if (selectedTag == null) selectedTag = accounts.firstOrNull()?.tag
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TavernWoodBanner {
            ClashOutlinedText(
                text = stringResource(R.string.widget_choose_account),
                fontSize = 20.sp,
                color = Color.White,
                strokeWidth = 5.5f,
            )
        }

        if (accounts.isEmpty()) {
            TavernStoneCard {
                Text(stringResource(R.string.widget_no_accounts), color = Color.White)
                Clash3DButton(onClick = onCancel, style = ClashButtonStyle.STONE, modifier = Modifier.fillMaxWidth()) {
                    ClashOutlinedText(stringResource(R.string.action_close), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(accounts) { acct ->
                    val isSel = acct.tag == selectedTag
                    val thBmp = remember(acct.townHall) {
                        val px = maxOf(with(density) { 52.dp.roundToPx() }, 160)
                        WidgetIcons.get(context).bitmapForLevel(1000001, acct.townHall, px)?.asImageBitmap()
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
                                    text = acct.displayName + (acct.townHall?.let { " · CV$it" } ?: ""),
                                    fontSize = 16.5.sp,
                                    color = if (isSel) TavernGold else Color.White,
                                    strokeWidth = 4.5f,
                                )
                                Text(acct.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    TavernStoneCard(modifier = Modifier.padding(top = 4.dp)) {
                        ClashOutlinedText(
                            text = stringResource(R.string.widget_style),
                            fontSize = 16.5.sp,
                            color = TavernGold,
                            strokeWidth = 4.5f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TavernFilterPill(
                                label = stringResource(R.string.widget_style_list),
                                selected = selectedStyle == StateRepository.WIDGET_STYLE_LIST,
                                onClick = { selectedStyle = StateRepository.WIDGET_STYLE_LIST },
                                modifier = Modifier.weight(1f),
                            )
                            TavernFilterPill(
                                label = stringResource(R.string.widget_style_tiles),
                                selected = selectedStyle == StateRepository.WIDGET_STYLE_TILES,
                                onClick = { selectedStyle = StateRepository.WIDGET_STYLE_TILES },
                                modifier = Modifier.weight(1f),
                            )
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
                    onClick = onCancel,
                    style = ClashButtonStyle.STONE,
                    modifier = Modifier.weight(1f),
                ) {
                    ClashOutlinedText(stringResource(R.string.action_cancel), fontSize = 14.sp)
                }
                Clash3DButton(
                    onClick = { selectedTag?.let { onSave(it, selectedStyle, selectedTheme) } },
                    enabled = selectedTag != null,
                    style = ClashButtonStyle.GREEN,
                    modifier = Modifier.weight(1f),
                ) {
                    ClashOutlinedText(stringResource(R.string.action_save), fontSize = 14.sp)
                }
            }
        }
    }
}
