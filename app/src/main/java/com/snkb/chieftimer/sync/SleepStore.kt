package com.snkb.chieftimer.sync

import android.content.Context
import com.snkb.chieftimer.core.SleepWindow

/** When the player sleeps, for the Plan tab's Bedtime view and optional pre-sleep reminder. */
object SleepStore {
    private const val PREFS = "plan_sleep"
    private const val KEY_START = "start"
    private const val KEY_END = "end"
    private const val KEY_ALERT = "bedtime_alert"

    fun get(context: Context): SleepWindow {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = SleepWindow()
        return SleepWindow(
            p.getInt(KEY_START, d.startMin).coerceIn(0, 1439),
            p.getInt(KEY_END, d.endMin).coerceIn(0, 1439),
        )
    }

    fun set(context: Context, window: SleepWindow) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_START, window.startMin).putInt(KEY_END, window.endMin).apply()
    }

    fun alertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALERT, false)

    fun setAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALERT, enabled).apply()
    }

    fun wasNotifiedForSleep(context: Context, tag: String, sleepAt: Long): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("notified_$tag", 0L) == sleepAt

    fun markNotifiedForSleep(context: Context, tag: String, sleepAt: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("notified_$tag", sleepAt).apply()
    }
}
