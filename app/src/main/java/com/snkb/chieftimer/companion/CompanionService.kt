package com.snkb.chieftimer.companion

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.snkb.chieftimer.R
import com.snkb.chieftimer.locale.LanguageManager
import com.snkb.chieftimer.notify.Notifier
import com.snkb.chieftimer.sync.ClipboardAutoSync
import com.snkb.chieftimer.sync.SyncActivity
import com.snkb.chieftimer.sync.SyncRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Play-Protect-safe Clash Companion (`SYSTEM_ALERT_WINDOW` + `PACKAGE_USAGE_STATS` + Quick Settings tile).
 *
 * Never declares `BIND_ACCESSIBILITY_SERVICE`. While active as a `specialUse` foreground service:
 *  - If Usage Access is granted and the setting is enabled, polls [UsageStatsManager.queryEvents] for window transitions
 *    to detect Clash of Clans coming to or leaving the foreground.
 *  - Also supports manual activation from the Quick Settings tile ([CompanionTileService]) or the app's Open game button.
 *  - Shows the 56 dp draggable golden button using `TYPE_APPLICATION_OVERLAY` and a quiet status notification.
 */
class CompanionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = CompanionSession()

    private var bar: CompanionOverlayView? = null
    private var barLayoutParams: WindowManager.LayoutParams? = null
    private var icon: ImageView? = null
    private var badge: TextView? = null
    private var pulse: ObjectAnimator? = null

    private var fetching = false
    @Volatile
    private var launchAcked = false
    @Volatile
    private var manualOverride = false
    private var lastTapAt = 0L
    private var status: String? = null
    private var overlayClipRequested = false
    private var screenReceiverRegistered = false

    private var lastQueryEndMs = 0L
    private var lastEventTs = 0L
    private var lastGameComponent: String? = null
    private var cachedKeyboards: Set<String> = emptySet()
    private var cachedKeyboardsAtMs = 0L

    private val applyLater = Runnable { apply() }
    private val fetchTimeout = Runnable {
        overlayClipRequested = false
        restoreOverlayNotFocusable()
        report(false, LanguageManager.wrap(this).getString(R.string.companion_timeout))
    }
    private val clearBadge = Runnable {
        badge?.visibility = View.GONE
        status = null
        postNotification()
    }
    private val overlayClipboardFallback = Runnable { triggerOverlayClipboardFallback() }
    private val overlayFocusDeadline = Runnable { runOverlayClipboardImport() }

    private val pollUsage = object : Runnable {
        override fun run() {
            if (!shouldAutoMonitor()) return
            val pm = getSystemService(PowerManager::class.java)
            if (pm?.isInteractive != true) return
            pollForegroundGame()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(pollUsage)
                    manualOverride = false
                    session.onGameLeft(now())
                    apply()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (shouldAutoMonitor()) {
                        lastQueryEndMs = (System.currentTimeMillis() - 10_000L).coerceAtLeast(0L)
                        startPolling()
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun now() = SystemClock.elapsedRealtime()

    private fun shouldAutoMonitor(): Boolean =
        CompanionPrefs.enabled(this) && CompanionPrefs.usageAccessAllowed(this)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundCompat(buildNotification())) {
            stopCleanly()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                if (intent.getBooleanExtra(EXTRA_DISABLE, false)) {
                    CompanionPrefs.setEnabled(this, false)
                }
                stopCleanly()
                return START_NOT_STICKY
            }
            ACTION_HIDE_BAR -> {
                manualOverride = false
                session.dismiss()
                apply()
                if (!shouldAutoMonitor()) {
                    stopCleanly()
                    return START_NOT_STICKY
                }
            }
            ACTION_MANUAL_SHOW -> {
                manualOverride = true
                if (lastQueryEndMs == 0L) {
                    val nowWall = System.currentTimeMillis()
                    lastQueryEndMs = nowWall
                    lastEventTs = nowWall
                }
                if (intent.getBooleanExtra(EXTRA_IMMEDIATE, false)) {
                    session.forceShow(now())
                } else {
                    session.onGameEntered(now())
                }
                apply()
                if (shouldAutoMonitor()) startPolling() else handler.removeCallbacks(pollUsage)
            }
            else -> {
                if (!shouldAutoMonitor() && !manualOverride) {
                    stopCleanly()
                    return START_NOT_STICKY
                }
                if (shouldAutoMonitor()) startPolling()
                apply()
            }
        }
        return if (shouldAutoMonitor()) START_STICKY else START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    }.getOrDefault(false)

    private fun startPolling() {
        handler.removeCallbacks(pollUsage)
        val pm = getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == true && shouldAutoMonitor()) {
            pollForegroundGame()
            handler.postDelayed(pollUsage, POLL_MS)
        }
    }

    private fun pollForegroundGame() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val nowWall = System.currentTimeMillis()
        val begin = if (lastQueryEndMs == 0L) {
            nowWall - 15 * 60_000L
        } else {
            (lastQueryEndMs - 1_000L).coerceAtLeast(nowWall - 60_000L)
        }
        lastQueryEndMs = nowWall
        if (begin >= nowWall) return

        val events = runCatching { usm.queryEvents(begin, nowWall) }.getOrNull() ?: return
        val ev = UsageEvents.Event()
        val resumedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        val stoppedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_STOPPED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }

        var kb: Set<String>? = null
        var currentActive = session.active
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.timeStamp <= lastEventTs) continue
            when (ev.eventType) {
                resumedType -> {
                    lastEventTs = ev.timeStamp
                    if (ev.packageName == CompanionLogic.GAME_PACKAGE) {
                        lastGameComponent = ev.className
                    }
                    val keyboards = kb ?: keyboards().also { kb = it }
                    currentActive = CompanionLogic.next(
                        packageName = ev.packageName,
                        current = currentActive,
                        own = packageName,
                        keyboards = keyboards,
                        className = ev.className,
                    )
                }
                stoppedType -> {
                    if (ev.packageName == CompanionLogic.GAME_PACKAGE && !fetching) {
                        val sameComponent = lastGameComponent == null || ev.className == null || ev.className == lastGameComponent
                        if (sameComponent) {
                            lastEventTs = ev.timeStamp
                            lastGameComponent = null
                            currentActive = false
                        }
                    }
                }
            }
        }
        if (currentActive != session.active) {
            if (currentActive) {
                session.onGameEntered(now())
            } else {
                manualOverride = false
                session.onGameLeft(now())
            }
            apply()
        }
    }

    private fun keyboards(): Set<String> {
        val t = now()
        if (cachedKeyboardsAtMs != 0L && t - cachedKeyboardsAtMs < KEYBOARDS_CACHE_MS) {
            return cachedKeyboards
        }
        val fetched = runCatching {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        cachedKeyboards = fetched
        cachedKeyboardsAtMs = t
        return fetched
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bar != null) {
            hideBar()
            apply()
        }
    }

    /** Brings the button and notification in line with the game state, the session and the settings. */
    fun apply() {
        handler.removeCallbacks(applyLater)
        val allowDisplay = manualOverride || CompanionPrefs.enabled(this)
        val activeForOverlay = session.active && allowDisplay
        val t = now()
        val wantBar = activeForOverlay && (manualOverride || CompanionPrefs.bar(this))
        if (wantBar && session.canShow(t)) {
            showBar()
        } else {
            hideBar()
            if (wantBar && session.pending()) handler.postDelayed(applyLater, session.waitMs(t))
        }
        postNotification()
    }

    private fun pasteIntent() = Intent(this, SyncActivity::class.java)
        .putExtra(SyncActivity.EXTRA_WIDGET_TAP, true)
        .putExtra(SyncActivity.EXTRA_SOURCE, "companion")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    // ---- tap handling & triple reliability ----------------------------------------------------------------------

    private fun onTap() {
        val t = now()
        if (t - lastTapAt < DOUBLE_TAP_MS) { // double tap: hide for this session
            lastTapAt = 0
            manualOverride = false
            session.dismiss()
            apply()
            if (!shouldAutoMonitor()) stopCleanly()
            return
        }
        lastTapAt = t
        startFetch()
    }

    /** One fetch at a time; a second tap while one runs only counts towards a double tap. */
    private fun startFetch() {
        if (fetching) return
        fetching = true
        launchAcked = false
        overlayClipRequested = false
        badge?.visibility = View.GONE
        pulse?.cancel()
        startFetchAnimationOnly()
        handler.postDelayed(fetchTimeout, FETCH_TIMEOUT_MS)
        runCatching { startActivity(pasteIntent()) }
        // Fallback: if Background Activity Launch blocks SyncActivity, temporarily focus the overlay view to read clipboard.
        handler.postDelayed(overlayClipboardFallback, OVERLAY_FALLBACK_DELAY_MS)
    }

    private fun triggerOverlayClipboardFallback() {
        if (!fetching || launchAcked) return
        val v = bar
        val lp = barLayoutParams
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (v == null || lp == null || wm == null) {
            report(false, LanguageManager.wrap(this).getString(R.string.companion_failed))
            return
        }
        overlayClipRequested = true
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        val updated = runCatching {
            wm.updateViewLayout(v, lp)
            v.requestFocus()
            true
        }.getOrDefault(false)
        if (!updated) {
            overlayClipRequested = false
            restoreOverlayNotFocusable()
            report(false, LanguageManager.wrap(this).getString(R.string.companion_failed))
            return
        }
        handler.postDelayed(overlayFocusDeadline, OVERLAY_FOCUS_DEADLINE_MS)
    }

    private fun runOverlayClipboardImport() {
        if (!overlayClipRequested) return
        overlayClipRequested = false
        handler.removeCallbacks(overlayFocusDeadline)
        val text = SyncRunner.readClipboard(this)
        restoreOverlayNotFocusable()
        serviceScope.launch {
            val result = runCatching { SyncRunner.sync(applicationContext, text, "companion") }
                .getOrElse { e -> SyncRunner.Result(false, e.message ?: LanguageManager.wrap(this@CompanionService).getString(R.string.companion_failed)) }
            if (result.ok) ClipboardAutoSync.markUsed(applicationContext, text)
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            showResult(result.ok, result.message)
        }
    }

    private fun restoreOverlayNotFocusable() {
        val v = bar ?: return
        val lp = barLayoutParams ?: return
        if ((lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun stopLoading() {
        fetching = false
        overlayClipRequested = false
        handler.removeCallbacks(fetchTimeout)
        handler.removeCallbacks(overlayClipboardFallback)
        handler.removeCallbacks(overlayFocusDeadline)
        restoreOverlayNotFocusable()
        pulse?.cancel()
        pulse = null
        icon?.apply { alpha = 1f; scaleX = 1f; scaleY = 1f }
    }

    /** Result of an import started from the button or the notification. */
    fun showResult(ok: Boolean, message: String) {
        stopLoading()
        status = (if (ok) "✓ " else "✕ ") + message
        postNotification()
        handler.removeCallbacks(clearBadge)
        val b = badge
        if (b != null) {
            b.text = if (ok) "✓" else "✕"
            (b.background as? GradientDrawable)?.setColor(if (ok) 0xFF2E9E4F.toInt() else 0xFFD13B3B.toInt())
            b.visibility = View.VISIBLE
        }
        handler.postDelayed(clearBadge, if (ok) SUCCESS_SHOW_MS else FAIL_SHOW_MS)
    }

    // ---- overlay ------------------------------------------------------------------------------------------------

    private inner class CompanionOverlayView(context: Context) : FrameLayout(context) {
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus && overlayClipRequested) {
                runOverlayClipboardImport()
            }
        }
    }

    private fun showBar() {
        if (bar != null || !CompanionPrefs.overlayAllowed(this)) return
        val lctx = LanguageManager.wrap(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (BUTTON_DP * d).toInt()
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.companion_import)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = lctx.getString(R.string.companion_paste)
        }
        val badge = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.GRAY)
                setStroke((1.5f * d).toInt(), Color.WHITE)
            }
        }
        val view = CompanionOverlayView(this).apply {
            isFocusableInTouchMode = true
            addView(icon, FrameLayout.LayoutParams(size, size))
            addView(badge, FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), Gravity.TOP or Gravity.END))
            setOnClickListener { onTap() }
        }
        val bounds: android.graphics.Rect = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val (rightOff, yFrac) = CompanionPrefs.position(this)
        val maxX = (bounds.width() - size).coerceAtLeast(0)
        val maxY = (bounds.height() - size).coerceAtLeast(0)
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            x = (bounds.width() - bounds.height() * rightOff - size / 2f).toInt().coerceIn(0, maxX)
            y = (bounds.height() * yFrac - size / 2f).toInt().coerceIn(0, maxY)
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - sx) > 8 * d || kotlin.math.abs(e.rawY - sy) > 8 * d) moved = true
                    if (moved) {
                        lp.x = (ox + (e.rawX - sx)).toInt().coerceIn(0, maxX)
                        lp.y = (oy + (e.rawY - sy)).toInt().coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                    moved
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val cx = lp.x + size / 2f
                        val cy = lp.y + size / 2f
                        CompanionPrefs.setPosition(this, (bounds.width() - cx) / bounds.height(), cy / bounds.height())
                    }
                    moved
                }
                else -> moved
            }
        }
        val added = runCatching {
            wm.addView(view, lp)
            bar = view
            barLayoutParams = lp
            this.icon = icon
            this.badge = badge
            true
        }.getOrDefault(false)
        if (added) {
            if (fetching) startFetchAnimationOnly()
            CompanionTileService.requestUpdate(this)
        }
    }

    private fun startFetchAnimationOnly() {
        val v = icon ?: return
        pulse?.cancel()
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f),
        ).apply { duration = 550; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; start() }
    }

    private fun hideBar() {
        val v = bar ?: return
        overlayClipRequested = false
        handler.removeCallbacks(overlayClipboardFallback)
        handler.removeCallbacks(overlayFocusDeadline)
        pulse?.cancel()
        pulse = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        bar = null
        barLayoutParams = null
        icon = null
        badge = null
        CompanionTileService.requestUpdate(this)
    }

    // ---- notification -------------------------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val lctx = LanguageManager.wrap(this)
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, lctx.getString(R.string.companion_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = lctx.getString(R.string.companion_channel_desc)
                        setShowBadge(false)
                    },
            )
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(lctx.getString(R.string.companion_notif_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        val showPasteActions = session.active && (manualOverride || CompanionPrefs.notification(this))
        if (showPasteActions) {
            val pastePi = PendingIntent.getActivity(
                this,
                7,
                pasteIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val hidePi = PendingIntent.getService(
                this,
                8,
                Intent(this, CompanionService::class.java).setAction(ACTION_HIDE_BAR),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .setContentText(status ?: lctx.getString(R.string.companion_notif_text))
                .addAction(0, lctx.getString(R.string.companion_paste), pastePi)
                .addAction(0, lctx.getString(R.string.companion_hide_action), hidePi)
        } else {
            val stopPi = PendingIntent.getService(
                this,
                9,
                Intent(this, CompanionService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_DISABLE, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .setContentText(lctx.getString(R.string.companion_notif_waiting))
                .addAction(0, lctx.getString(R.string.companion_stop_action), stopPi)
        }
        return builder.build()
    }

    private fun postNotification() {
        if (!Notifier.allowed(this)) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun stopCleanly() {
        cleanup()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            screenReceiverRegistered = false
            runCatching { unregisterReceiver(screenReceiver) }
        }
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        stopLoading()
        hideBar()
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
        manualOverride = false
        session.onGameLeft(now())
        if (instance === this) instance = null
    }

    companion object {
        private const val CHANNEL = "companion"
        private const val NOTIFICATION_ID = 7001
        private const val BUTTON_DP = 56
        private const val POLL_MS = 2_000L
        private const val KEYBOARDS_CACHE_MS = 5 * 60_000L
        private const val DOUBLE_TAP_MS = 350L
        private const val OVERLAY_FALLBACK_DELAY_MS = 650L
        private const val OVERLAY_FOCUS_DEADLINE_MS = 220L
        private const val FETCH_TIMEOUT_MS = 12_000L
        private const val SUCCESS_SHOW_MS = 1_200L
        private const val FAIL_SHOW_MS = 2_500L

        private const val ACTION_SYNC = "com.snkb.chieftimer.COMPANION_SYNC"
        private const val ACTION_MANUAL_SHOW = "com.snkb.chieftimer.COMPANION_SHOW"
        private const val ACTION_HIDE_BAR = "com.snkb.chieftimer.COMPANION_HIDE"
        private const val ACTION_STOP = "com.snkb.chieftimer.COMPANION_STOP"
        private const val EXTRA_IMMEDIATE = "immediate"
        private const val EXTRA_DISABLE = "disable"

        @Volatile
        var instance: CompanionService? = null
            private set

        /** Called when Chief Timer's own MainActivity comes to the front: hides any leftover game button and syncs auto-monitor. */
        fun onHostResumed(context: Context) {
            val app = context.applicationContext
            val cur = instance
            if (cur != null && (cur.manualOverride || !CompanionPrefs.usageAccessAllowed(app) || cur.bar != null)) {
                hide(app)
            }
            sync(app)
        }

        /** Starts monitoring automatically when enabled + Usage Access allowed, otherwise stops non-manual runs. */
        fun sync(context: Context) {
            val app = context.applicationContext
            if (CompanionPrefs.enabled(app) && CompanionPrefs.usageAccessAllowed(app)) {
                runCatching {
                    ContextCompat.startForegroundService(
                        app,
                        Intent(app, CompanionService::class.java).setAction(ACTION_SYNC),
                    )
                }
            } else {
                val cur = instance
                if (cur != null && !cur.manualOverride) {
                    stop(app)
                }
            }
        }

        /** Shows the floating button on demand (from the Quick Settings tile or the app's Open game button). */
        fun activateManual(context: Context, immediate: Boolean) {
            val app = context.applicationContext
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, CompanionService::class.java)
                        .setAction(ACTION_MANUAL_SHOW)
                        .putExtra(EXTRA_IMMEDIATE, immediate),
                )
            }
        }

        /** Hides the floating button until the next manual request or game launch. */
        fun hide(context: Context) {
            instance?.let { s ->
                s.handler.post {
                    s.manualOverride = false
                    s.session.dismiss()
                    s.apply()
                    if (!s.shouldAutoMonitor()) s.stopCleanly()
                }
            }
        }

        /** Stops the companion service completely. */
        fun stop(context: Context) {
            instance?.let { s ->
                s.handler.post { s.stopCleanly() }
            }
        }

        fun isShowingBar(): Boolean = instance?.bar != null

        fun ackLaunch() {
            instance?.launchAcked = true
        }

        /** Called after a setting changes. */
        fun refresh() {
            instance?.let { s -> s.handler.post { s.apply() } }
        }

        /** Called by the paste screen when an import from the companion finished. */
        fun report(ok: Boolean, message: String) {
            instance?.let { s -> s.handler.post { s.showResult(ok, message) } }
        }
    }
}
