package com.snkb.chieftimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.ReleaseInfo
import com.snkb.chieftimer.core.UpdateCheck
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.update.AppUpdater
import com.snkb.chieftimer.update.UpdatePrefs
import kotlinx.coroutines.launch

@Composable
internal fun rememberCanInstallPackage(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(resumeTick) { AppUpdater.canInstall(context) }
}

/** When the app opens (at most every 12 hours, if switched on in Support) checks Chief Timer GitHub for a newer release. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canInstall = rememberCanInstallPackage()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!UpdatePrefs.dueNow(context)) return@LaunchedEffect
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val info = runCatching { AppUpdater.fetchLatest() }.getOrNull() ?: return@LaunchedEffect
        if (UpdateCheck.isNewer(installed, info.version) && !UpdatePrefs.skipped(context, info.version)) release = info
    }

    val info = release ?: return
    val busy = progress != null
    AlertDialog(
        onDismissRequest = { if (!busy) release = null },
        title = {
            ClashOutlinedText(
                text = stringResource(R.string.notif_update_title, info.version),
                fontSize = 18.sp,
                color = TavernGold,
                strokeWidth = 4.5f,
                maxLines = 2,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.take(800),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                progress?.let {
                    LinearProgressIndicator(
                        progress = { it.coerceAtLeast(0f) },
                        color = TavernGold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!canInstall) {
                    Text(
                        stringResource(R.string.update_allow_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (!canInstall) {
                        AppUpdater.openInstallSettings(context)
                        return@TextButton
                    }
                    error = null
                    scope.launch {
                        try {
                            progress = 0f
                            val apk = AppUpdater.download(context, info) { progress = it }
                            AppUpdater.install(context, apk)
                            release = null
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        }
                        progress = null
                    }
                },
            ) {
                Text(
                    if (canInstall) stringResource(R.string.update_download_install)
                    else stringResource(R.string.update_allow_install),
                    color = TavernGoldLight,
                )
            }
        },
        dismissButton = {
            if (!busy) {
                Row {
                    TextButton(onClick = { UpdatePrefs.skip(context, info.version); release = null }) {
                        Text(stringResource(R.string.action_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { release = null }) {
                        Text(stringResource(R.string.gold_pass_later), color = TavernGold)
                    }
                }
            }
        },
    )
}
