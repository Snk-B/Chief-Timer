@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.ReleaseInfo
import com.snkb.chieftimer.core.UpdateCheck
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import com.snkb.chieftimer.ui.theme.TavernGreenLight
import com.snkb.chieftimer.update.AppUpdater
import com.snkb.chieftimer.update.UpdateCheckJob
import com.snkb.chieftimer.update.UpdatePrefs
import kotlinx.coroutines.launch

/** Supercell's required wording for fan content (supercell.com/fan-content-policy). */
const val FAN_CONTENT_DISCLAIMER =
    "This material is unofficial and is not endorsed by Supercell. For more information see " +
        "Supercell's Fan Content Policy: www.supercell.com/fan-content-policy."

/** About: version, in-app GitHub updates for Snk-B/Chief-Timer, issue reporting, and ClashKing credits. */
@Composable
fun AboutSection(reportProvider: () -> String = { "" }) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    var showLicence by remember { mutableStateOf(false) }
    var creditsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val installedVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var autoCheck by remember { mutableStateOf(UpdatePrefs.autoCheck(context)) }

    fun check() {
        update = UpdateState.Checking
        scope.launch {
            update = try {
                val info = AppUpdater.fetchLatest()
                if (info != null && UpdateCheck.isNewer(installedVersion, info.version)) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: java.io.IOException) {
                UpdateState.Failed((e.message ?: e.javaClass.simpleName) + "\n\n" + AppUpdater.diagnose(context))
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun install(info: ReleaseInfo) {
        scope.launch {
            try {
                update = UpdateState.Downloading(info, 0f)
                val apk = AppUpdater.download(context, info) { update = UpdateState.Downloading(info, it) }
                AppUpdater.install(context, apk)
                update = UpdateState.Available(info)
            } catch (e: Exception) {
                update = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TavernWoodBanner {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tavern_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ClashOutlinedText(
                        text = stringResource(R.string.about_version, version),
                        fontSize = 18.sp,
                        color = Color.White,
                        strokeWidth = 5f,
                    )
                    Text(
                        stringResource(R.string.about_updates_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFECC2),
                    )
                }
            }

            when (val u = update) {
                is UpdateState.Available -> {
                    ClashOutlinedText(
                        text = stringResource(R.string.update_available, u.info.version),
                        fontSize = 14.5.sp,
                        color = TavernGreenLight,
                        strokeWidth = 3.8f,
                    )
                    val canInstall = rememberCanInstallPackage()
                    if (canInstall) {
                        UpdateActionButton(R.string.update_download_install, ClashButtonStyle.GOLD) { install(u.info) }
                    } else {
                        Text(
                            stringResource(R.string.update_allow_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFECC2),
                        )
                        UpdateActionButton(R.string.update_allow_install, ClashButtonStyle.GOLD) {
                            AppUpdater.openInstallSettings(context)
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Text(
                        stringResource(R.string.update_downloading, (u.progress.coerceAtLeast(0f) * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TavernGoldLight,
                    )
                    if (u.progress >= 0f) {
                        LinearProgressIndicator(progress = { u.progress }, color = TavernGold, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(color = TavernGold, modifier = Modifier.fillMaxWidth())
                    }
                }
                UpdateState.Checking -> {
                    Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium, color = TavernGoldLight)
                }
                UpdateState.UpToDate -> {
                    Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium, color = TavernGreenLight)
                    UpdateActionButton(R.string.about_check_updates, ClashButtonStyle.GREEN) { check() }
                }
                is UpdateState.Failed -> {
                    Text(stringResource(R.string.update_failed, u.message), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4B4))
                    UpdateActionButton(R.string.about_check_updates, ClashButtonStyle.GREEN) { check() }
                }
                UpdateState.Idle -> UpdateActionButton(R.string.about_check_updates, ClashButtonStyle.GREEN) { check() }
            }

            SwitchRow(stringResource(R.string.update_auto), autoCheck) { on ->
                autoCheck = on
                UpdatePrefs.setAutoCheck(context, on)
                UpdateCheckJob.sync(context)
            }

            TextButton(
                onClick = { openUrl(context, RELEASES_URL) },
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.update_releases_page), color = TavernGoldLight)
            }
        }

        AboutGroup(stringResource(R.string.about_feedback_title)) {
            Text(
                stringResource(R.string.about_feedback_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Clash3DButton(
                    onClick = { reportIssue(context, reportProvider()) },
                    style = ClashButtonStyle.GOLD,
                    modifier = Modifier.weight(1f),
                ) {
                    ClashOutlinedText(stringResource(R.string.about_report_issue), fontSize = 13.sp, maxLines = 1)
                }
                Clash3DButton(
                    onClick = { openUrl(context, FEATURE_URL) },
                    style = ClashButtonStyle.STONE,
                    modifier = Modifier.weight(1f),
                ) {
                    ClashOutlinedText(stringResource(R.string.about_request_feature), fontSize = 13.sp, maxLines = 1)
                }
            }
            Text(
                stringResource(R.string.about_report_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Clash3DButton(
                onClick = { openUrl(context, ISSUES_LIST_URL) },
                style = ClashButtonStyle.STONE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ClashOutlinedText(stringResource(R.string.about_view_issues), fontSize = 13.5.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { creditsOpen = !creditsOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClashOutlinedText(
                    text = stringResource(R.string.about_credits_title),
                    fontSize = 15.sp,
                    color = TavernGold,
                    strokeWidth = 4f,
                    modifier = Modifier.weight(1f),
                )
                ClashOutlinedText(
                    text = if (creditsOpen) "\u2212" else "+",
                    fontSize = 22.sp,
                    color = TavernGold,
                    strokeWidth = 4.5f,
                )
            }
            AnimatedVisibility(visible = creditsOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        FAN_CONTENT_DISCLAIMER,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Game data, pictures and event dates are provided by ClashKing. Thank you to the ClashKing team.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CREDIT_LINKS.forEach { (label, url) ->
                        TextButton(
                            onClick = { openUrl(context, url) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(label, color = TavernGoldLight) }
                    }
                    Text(
                        stringResource(R.string.about_credits_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showLicence = !showLicence },
                        modifier = Modifier.heightIn(min = 44.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            if (showLicence) stringResource(R.string.about_hide_license) else stringResource(R.string.about_show_license),
                            color = TavernGold,
                        )
                    }
                    if (showLicence) {
                        val licence = remember { readAsset(context, "licenses/LilitaOne-OFL.txt") }
                        Text(licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
private fun UpdateActionButton(label: Int, style: ClashButtonStyle, onClick: () -> Unit) {
    Clash3DButton(
        onClick = onClick,
        style = style,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(R.drawable.ic_sync), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        ClashOutlinedText(
            text = stringResource(label),
            fontSize = 14.5.sp,
            color = Color.White,
            strokeWidth = 4.5f,
        )
    }
}

@Composable
private fun AboutGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClashOutlinedText(text = title, fontSize = 15.sp, color = TavernGold, strokeWidth = 4f)
        content()
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private fun reportIssue(context: Context, report: String) {
    if (report.isNotEmpty()) copyReport(context, report)
    val body = "**What happened?**\n\n\n**Diagnostic report** (it is on your clipboard, paste it here)\n\n" +
        "App: ${appVersion(context)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n"
    openUrl(context, ISSUES_URL + "?body=" + Uri.encode(body))
}

private const val RELEASES_URL = "https://github.com/Snk-B/Chief-Timer/releases/latest"
private const val ISSUES_LIST_URL = "https://github.com/Snk-B/Chief-Timer/issues"
private const val ISSUES_URL = "https://github.com/Snk-B/Chief-Timer/issues/new"
private const val FEATURE_URL = "https://github.com/Snk-B/Chief-Timer/issues/new?title=Feature+request%3A+&labels=enhancement"

private val CREDIT_LINKS = listOf(
    "ClashKing" to "https://clashk.ing",
    "ClashKingAssets on GitHub" to "https://github.com/ClashKingInc/ClashKingAssets",
    "ClashKing API documentation" to "https://api.clashk.ing/swagger/index.html",
)

internal fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "v${info.versionName} ($code)"
}.getOrDefault("")

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("Licence file missing.")
