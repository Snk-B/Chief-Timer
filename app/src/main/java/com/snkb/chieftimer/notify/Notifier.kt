package com.snkb.chieftimer.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.snkb.chieftimer.MainActivity
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.FinishNotice
import com.snkb.chieftimer.sync.LiveUpdateAlert
import com.snkb.chieftimer.widget.ChiefWidgetReceiver
import com.snkb.chieftimer.widget.WidgetIcons
import com.snkb.chieftimer.widget.WidgetInternalReceiver
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** "Builder free: Inferno Tower" / "Lab free" / "Pet house free" + Android 16/17 & Samsung One UI Live Pill. */
object Notifier {
    /** Plays on the alarm volume and breaks through Do Not Disturb (the original channel, kept for existing users). */
    const val CHANNEL_ID = "finished_v2"
    /** Plays on the notification volume and is silenced by Do Not Disturb. */
    const val CHANNEL_QUIET = "finished_v3"
    /** Clock Tower ready: its own sound, one channel per Do Not Disturb choice (a channel's sound can't change once created). */
    const val CHANNEL_CLOCK = "clocktower_v1"
    const val CHANNEL_CLOCK_QUIET = "clocktower_v1_quiet"
    /** Silent high-priority channel for Live Countdown Notifications / Samsung Status Bar Pill & Now Bar. */
    const val CHANNEL_LIVE = "live_countdown_v1"
    /** Vibrant Clash Tavern Orange for the Status Bar Live Pill, Now Bar badge & progress bar. */
    const val LIVE_PILL_ORANGE = 0xFFEE7214.toInt()

    private const val PREFS = "notif_prefs"
    private const val KEY_RESPECT_DND = "respect_dnd"
    private const val OLD_CHANNEL_ID = "finished"
    private const val CLASH_PACKAGE = "com.supercell.clashofclans"

    data class LiveCountdownItem(
        val accountTag: String,
        val accountName: String,
        val jobKey: String,
        val shortName: String,
        val label: String,
        val categoryTitle: String,
        val finishAt: Long,
        val capturedLeftSec: Long,
        val progress: Float,
        val iconIds: List<Int>,
        val iconLevel: Int?,
        val accentColor: Int,
    )

    /** Whether finish alerts follow Do Not Disturb (default yes, so they stay quiet overnight). */
    fun respectDnd(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RESPECT_DND, true)

    fun setRespectDnd(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESPECT_DND, on).apply()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val lctx = com.snkb.chieftimer.locale.LanguageManager.wrap(context)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_QUIET) == null) {
            val quiet = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_QUIET, lctx.getString(R.string.notif_channel_quiet), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = lctx.getString(R.string.notif_channel_quiet_desc)
                    setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.finish_sound}"), quiet)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_LIVE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE, lctx.getString(R.string.notif_channel_live), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = lctx.getString(R.string.notif_channel_live_desc)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        ensureClockChannels(context, nm)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.finish_sound}")
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                lctx.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = lctx.getString(R.string.notif_channel_desc)
                setSound(soundUri, audioAttributes)
            },
        )
    }

    private fun ensureClockChannels(context: Context, nm: NotificationManager) {
        val lctx = com.snkb.chieftimer.locale.LanguageManager.wrap(context)
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.clock_tower_ready}")
        fun make(id: String, name: String, usage: Int) {
            if (nm.getNotificationChannel(id) != null) return
            val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = lctx.getString(R.string.notif_channel_clock_desc)
                    setSound(sound, attrs)
                },
            )
        }
        make(CHANNEL_CLOCK_QUIET, lctx.getString(R.string.notif_channel_clock_quiet), AudioAttributes.USAGE_NOTIFICATION)
        make(CHANNEL_CLOCK, lctx.getString(R.string.notif_channel_clock), AudioAttributes.USAGE_ALARM)
    }

    /** True if the app may post notifications (runtime permission on Android 13+, and not blocked in settings). */
    fun allowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** True if Android 16+ / One UI 8+ allows promoting ongoing notifications to the Status Bar Chip & Now Bar. */
    fun canPromoteLive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(true)
    }

    /** Formats remaining seconds cleanly as `MM:SS` (5 chars) or `H:MM:SS` (7 chars) for the status bar pill & title. */
    fun formatClock(leftSec: Long): String {
        val s = leftSec.coerceAtLeast(0L)
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val sec = s % 60L
        return if (h > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }

    /** Greedily fills [deficit] using descending-width [fillers], rounding to the nearest smallest filler. */
    internal fun buildFixedWidthPadding(
        deficit: Float,
        fillers: List<Pair<Char, Float>>,
        maxChars: Int = 16,
    ): String {
        if (deficit <= 0f || fillers.isEmpty()) return ""
        val sorted = fillers.filter { it.second > 0f }.sortedByDescending { it.second }
        if (sorted.isEmpty()) return ""
        val smallest = sorted.last()
        val sb = StringBuilder()
        var remaining = deficit
        while (remaining > 0f && sb.length < maxChars) {
            val pick = sorted.firstOrNull { it.second <= remaining }
            if (pick != null) {
                sb.append(pick.first)
                remaining -= pick.second
            } else {
                if (remaining > smallest.second * 0.5f && sb.length < maxChars) {
                    sb.append(smallest.first)
                }
                break
            }
        }
        return sb.toString()
    }

    private data class PillMetrics(
        val anchor: Char,
        val maxDigitW: Float,
        val colonW: Float,
        val digitWidths: FloatArray,
        val fillers: List<Pair<Char, Float>>,
    )

    @Volatile
    private var cachedPillMetrics: PillMetrics? = null

    private fun pillMetrics(): PillMetrics? {
        cachedPillMetrics?.let { return it }
        return runCatching {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT
                textSize = 100f
            }
            val anchor = if (paint.hasGlyph("\u202F") && paint.measureText("\u202F") > 0.5f) '\u202F' else '\u00A0'
            val digitWidths = FloatArray(10) { d -> paint.measureText(('0' + d).toString()) }
            val maxDigitW = digitWidths.maxOrNull() ?: 0f
            if (maxDigitW <= 0f) return@runCatching null
            val colonW = paint.measureText(":")
            val candidates = charArrayOf('\u2007', '\u00A0', '\u202F', '\u2009', '\u2006', '\u200A')
            val fillers = mutableListOf<Pair<Char, Float>>()
            for (ch in candidates) {
                val s = ch.toString()
                if (!paint.hasGlyph(s)) continue
                val w = paint.measureText(s)
                if (w in 1f..(maxDigitW * 1.15f) && fillers.none { kotlin.math.abs(it.second - w) < 0.5f }) {
                    fillers += ch to w
                }
            }
            PillMetrics(anchor, maxDigitW, colonW, digitWidths, fillers).also { cachedPillMetrics = it }
        }.getOrNull()
    }

    /**
     * Pads [clockStr] to the width of the widest digits in the device's active system font (`Typeface.DEFAULT`)
     * wrapped in non-breaking narrow spaces (`\u202F`, fallback `\u00A0`) so Android/One UI never trims it,
     * giving the status bar pill a few pixels of breathing room and locking its width across every second.
     */
    internal fun stabilizePillText(clockStr: String): String {
        val m = pillMetrics() ?: return "\u202F$clockStr\u202F"
        var digits = 0
        var colons = 0
        var actualW = 0f
        for (ch in clockStr) {
            when (ch) {
                in '0'..'9' -> {
                    digits++
                    actualW += m.digitWidths[ch - '0']
                }
                ':' -> {
                    colons++
                    actualW += m.colonW
                }
            }
        }
        val targetW = m.maxDigitW * digits + m.colonW * colons
        val deficit = (targetW - actualW).coerceAtLeast(0f)
        val pad = buildFixedWidthPadding(deficit, m.fillers)
        return "${m.anchor}$clockStr$pad${m.anchor}"
    }

    /**
     * Builds the promoted ongoing Live Notification using 100% native Android 16/17 Live Updates APIs
     * (`setRequestPromotedOngoing`, concise `setShortCriticalText`, and `ProgressStyle`), with Android 8-15
     * fallback (`setProgress`) and clash orange accent tint without breaking OS Dark/Light header contrast.
     */
    fun buildLiveNotification(
        context: Context,
        item: LiveCountdownItem,
        multipleAccounts: Boolean,
        nowSec: Long,
        marqueeStep: Long = 0L,
    ): Notification {
        val lctx = com.snkb.chieftimer.locale.LanguageManager.wrap(context)
        val icons = WidgetIcons.get(context)
        val leftSec = (item.finishAt - nowSec).coerceAtLeast(0L)
        val clockStr = formatClock(leftSec)
        val liveProgress = if (item.capturedLeftSec > 0L && item.progress in 0f..1f) {
            val ratio = (leftSec.toFloat() / item.capturedLeftSec.toFloat()).coerceIn(0f, 1f)
            (1f - (1f - item.progress) * ratio).coerceIn(item.progress, 1f)
        } else {
            item.progress.coerceIn(0f, 1f)
        }
        val pctInt = if (leftSec > 0L) (liveProgress * 100f).toInt().coerceIn(0, 99) else 100
        val progPermille = (liveProgress * 1000f).roundToInt()

        val villageAndItem = if (item.accountName.isNotBlank()) {
            "${item.accountName} \u00B7 ${item.label}"
        } else {
            item.label
        }

        // Expanded Notification Card title & subtitle
        val cardTitle = "$villageAndItem   \u2022   $clockStr"
        val finishClock = DateFormat.getTimeFormat(context).format(Date(item.finishAt * 1000L))
        val finishesAtStr = lctx.getString(R.string.finishes_at, finishClock)
        val cardSubtitle = "${item.categoryTitle} \u2022 ${lctx.getString(R.string.percent_complete, pctInt)} \u2022 $finishesAtStr"

        val iconBmp = item.iconIds.firstNotNullOfOrNull { icons.bitmapForLevel(it, item.iconLevel, 144) }
        val timeoutMs = (leftSec * 1000L + 2_000L).coerceAtLeast(1_500L)

        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(cardTitle)
            .setContentText(cardSubtitle)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(item.accentColor)
            .setTimeoutAfter(timeoutMs)
            .setContentIntent(openIntent(context))
            .addAction(
                Notification.Action.Builder(
                    null,
                    lctx.getString(R.string.open_game_short),
                    openIntent(context),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    lctx.getString(R.string.live_notif_dismiss),
                    dismissLiveIntent(context, item),
                ).build(),
            )

        if (iconBmp != null) {
            builder.setLargeIcon(iconBmp)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(stabilizePillText(clockStr))
            val pStyle = Notification.ProgressStyle()
                .addProgressSegment(Notification.ProgressStyle.Segment(1000).setColor(item.accentColor))
                .setProgress(progPermille)
                .setStyledByProgress(true)
            builder.setStyle(pStyle)
        } else {
            builder.setProgress(1000, progPermille, false)
        }

        return builder.build()
    }

    /**
     * Synchronizes active Live Countdown / Samsung Now Bar notifications with [dueItems] via [LiveCountdownService],
     * with a safe fallback to direct NotificationManager posting if background foreground-service start is ever restricted.
     */
    fun syncLiveUpdates(context: Context, dueItems: List<LiveCountdownItem>, multipleAccounts: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val prevPosted = LiveUpdateAlert.postedMap(context)

        if (!allowed(context) || dueItems.isEmpty()) {
            LiveCountdownService.updateOrStop(context, emptyList(), multipleAccounts)
            prevPosted.keys.forEach { id -> runCatching { nm.cancel(id) } }
            if (prevPosted.isNotEmpty()) LiveUpdateAlert.savePostedMap(context, emptyMap())
            return
        }

        // Clear any legacy direct-posted IDs from v2.0.3 before handing over to LiveCountdownService
        if (prevPosted.isNotEmpty()) {
            prevPosted.keys.forEach { id -> runCatching { nm.cancel(id) } }
            LiveUpdateAlert.savePostedMap(context, emptyMap())
        }

        val startedService = LiveCountdownService.updateOrStop(context, dueItems, multipleAccounts)
        if (!startedService) {
            postLiveFallback(context, dueItems, multipleAccounts)
        }
    }

    /** Direct notification-manager fallback when foreground-service startup is restricted by OEM/Android. */
    internal fun postLiveFallback(context: Context, dueItems: List<LiveCountdownItem>, multipleAccounts: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)
        val nowSec = System.currentTimeMillis() / 1000
        val nextPosted = mutableMapOf<Int, Long>()
        for (item in dueItems) {
            val notifId = ("live_nowbar:${item.accountTag}:${item.jobKey}").hashCode()
            runCatching {
                nm.notify(notifId, buildLiveNotification(context, item, multipleAccounts, nowSec))
                nextPosted[notifId] = item.finishAt
            }
        }
        LiveUpdateAlert.savePostedMap(context, nextPosted)
    }

    /** Posts one notification per finished job; returns false if notifications are blocked. */
    fun post(context: Context, notices: List<FinishNotice>, accountName: String?, accountTag: String?, multipleAccounts: Boolean): Boolean {
        if (!allowed(context)) return false
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        val channel = if (respectDnd(context)) CHANNEL_QUIET else CHANNEL_ID
        for ((index, n) in notices.withIndex()) {
            val titlePrefix = if (multipleAccounts && accountName != null) "$accountName ($accountTag): " else ""
            val isClock = n.jobKey.startsWith(com.snkb.chieftimer.core.FinishNotices.CLOCK_TOWER_KEY)
            val notifChannel = if (isClock) (if (respectDnd(context)) CHANNEL_CLOCK_QUIET else CHANNEL_CLOCK) else channel
            val notification = NotificationCompat.Builder(context, notifChannel)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("$titlePrefix${n.title}")
                .setContentText(n.text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                // Several upgrades finishing together: only the first one makes a sound.
                .setSilent(index > 0)
                .setContentIntent(openIntent(context))
                .build()
            try {
                val id = n.jobKey.hashCode() xor (accountTag?.hashCode() ?: 0)
                nm.notify(id, notification)
            } catch (e: SecurityException) {
                return false
            }
        }
        return true
    }

    /** Tapping a notification opens Clash of Clans if it's installed, otherwise this app. */
    private fun openIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(CLASH_PACKAGE)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Dismisses only this specific upgrade's Live Notification without turning off Live Notifications or its 00:00 finish alert. */
    private fun dismissLiveIntent(context: Context, item: LiveCountdownItem): PendingIntent {
        val intent = Intent(context, WidgetInternalReceiver::class.java)
            .setAction(ChiefWidgetReceiver.ACTION_DISMISS_LIVE)
            .putExtra(ChiefWidgetReceiver.EXTRA_ACCOUNT_TAG, item.accountTag)
            .putExtra(ChiefWidgetReceiver.EXTRA_JOB_KEY, item.jobKey)
            .putExtra(ChiefWidgetReceiver.EXTRA_FINISH_AT, item.finishAt)
        val reqCode = ("dismiss_live:${item.accountTag}:${item.jobKey}").hashCode()
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
