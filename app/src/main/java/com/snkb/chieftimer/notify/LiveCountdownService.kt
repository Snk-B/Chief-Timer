package com.snkb.chieftimer.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.snkb.chieftimer.R
import com.snkb.chieftimer.core.Finish
import com.snkb.chieftimer.sync.LiveUpdateAlert
import com.snkb.chieftimer.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight Foreground Service that runs ONLY during the final N minutes before an upgrade finishes,
 * keeping the Status Bar Live Pill (`setShortCriticalText`) and expanded card countdown ticking second-by-second,
 * then stops itself immediately at 00:00.
 */
class LiveCountdownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    @Volatile
    private var marqueeStep: Long = 0L
    @Volatile
    private var lastPrimaryIdentity: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannel(this)
        if (intent?.action == ACTION_STOP) {
            startForegroundCompat(buildShutdownNotification())
            stopCleanly()
            return START_NOT_STICKY
        }

        val snapshot = currentItems
        val multi = currentMultiAccounts
        val nowSec = System.currentTimeMillis() / 1000
        val active = snapshot
            .filter {
                it.finishAt - nowSec > Finish.GRACE_SEC &&
                    !LiveUpdateAlert.isDismissed(this, it.accountTag, it.jobKey, it.finishAt)
            }
            .sortedBy { it.finishAt }

        if (!Notifier.allowed(this) || !LiveUpdateAlert.enabled(this) || active.isEmpty()) {
            startForegroundCompat(buildShutdownNotification())
            stopCleanly()
            return START_NOT_STICKY
        }

        val primary = active.first()
        val primaryId = "${primary.accountTag}:${primary.jobKey}"
        if (primaryId != lastPrimaryIdentity) {
            lastPrimaryIdentity = primaryId
            marqueeStep = 0L
        }
        val primaryNotif = Notifier.buildLiveNotification(this, primary, multi, nowSec, marqueeStep)
        if (!startForegroundCompat(primaryNotif)) {
            Notifier.postLiveFallback(applicationContext, active, multi)
            stopSelf()
            return START_NOT_STICKY
        }

        if (tickerJob?.isActive == true) {
            isLoopRunning = true
            return START_NOT_STICKY
        }

        isLoopRunning = true
        tickerJob = scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            val notifiedDoneKeys = mutableSetOf<String>()
            try {
                while (isActive) {
                    delay(TICK_MS)
                    val now = System.currentTimeMillis() / 1000
                    val allSnapshot = currentItems
                    val newlyFinished = allSnapshot.filter {
                        it.finishAt - now <= Finish.GRACE_SEC &&
                            notifiedDoneKeys.add("${it.accountTag}:${it.jobKey}:${it.finishAt}")
                    }
                    if (newlyFinished.isNotEmpty()) {
                        val app = applicationContext
                        scope.launch {
                            runCatching { WidgetUpdater.updateAll(app, "live_done") }
                        }
                    }

                    val running = allSnapshot
                        .filter {
                            it.finishAt - now > Finish.GRACE_SEC &&
                                !LiveUpdateAlert.isDismissed(this@LiveCountdownService, it.accountTag, it.jobKey, it.finishAt)
                        }
                        .sortedBy { it.finishAt }

                    if (!Notifier.allowed(this@LiveCountdownService) || !LiveUpdateAlert.enabled(this@LiveCountdownService) || running.isEmpty()) {
                        stopCleanly()
                        break
                    }

                    val multiAcct = currentMultiAccounts
                    val first = running.first()
                    val firstIdentity = "${first.accountTag}:${first.jobKey}"
                    if (firstIdentity != lastPrimaryIdentity) {
                        lastPrimaryIdentity = firstIdentity
                        marqueeStep = 0L
                    } else {
                        marqueeStep++
                    }
                    runCatching {
                        nm?.notify(
                            FOREGROUND_NOTIF_ID,
                            Notifier.buildLiveNotification(this@LiveCountdownService, first, multiAcct, now, marqueeStep),
                        )
                    }
                }
            } finally {
                isLoopRunning = false
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notif: Notification): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notif)
        }
        true
    }.getOrDefault(false)

    private fun buildShutdownNotification(): Notification =
        Notification.Builder(this, Notifier.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .build()

    private fun stopCleanly() {
        isLoopRunning = false
        tickerJob?.cancel()
        tickerJob = null
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { nm?.cancel(FOREGROUND_NOTIF_ID) }
        stopSelf()
    }

    override fun onDestroy() {
        isLoopRunning = false
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_NOTIF_ID = 0x4354_4C56 // "CTLV"
        private const val TICK_MS = 1_000L
        private const val ACTION_START = "com.snkb.chieftimer.LIVE_START"
        private const val ACTION_STOP = "com.snkb.chieftimer.LIVE_STOP"

        @Volatile
        private var currentItems: List<Notifier.LiveCountdownItem> = emptyList()

        @Volatile
        private var currentMultiAccounts: Boolean = false

        @Volatile
        private var isLoopRunning: Boolean = false

        fun updateOrStop(context: Context, items: List<Notifier.LiveCountdownItem>, multipleAccounts: Boolean): Boolean {
            val app = context.applicationContext
            val filtered = items.filter { !LiveUpdateAlert.isDismissed(app, it.accountTag, it.jobKey, it.finishAt) }
            currentItems = filtered
            currentMultiAccounts = multipleAccounts
            if (filtered.isEmpty() || !Notifier.allowed(app) || !LiveUpdateAlert.enabled(app)) {
                isLoopRunning = false
                currentItems = emptyList()
                runCatching { app.stopService(Intent(app, LiveCountdownService::class.java)) }
                runCatching { app.getSystemService(NotificationManager::class.java)?.cancel(FOREGROUND_NOTIF_ID) }
                return true
            }
            if (isLoopRunning) {
                return true
            }
            return runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, LiveCountdownService::class.java).setAction(ACTION_START),
                )
                true
            }.getOrDefault(false)
        }

        /** Immediately updates or tears down the foreground pill when the user taps "Dismiss alert". */
        fun onJobDismissed(context: Context) {
            updateOrStop(context, currentItems, currentMultiAccounts)
        }
    }
}
