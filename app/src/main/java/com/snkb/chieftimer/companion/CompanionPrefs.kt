package com.snkb.chieftimer.companion

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings

/** Settings for the Clash Companion (Settings > Import assistant). Off until the user turns it on. */
object CompanionPrefs {
    private const val PREFS = "companion"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("enabled", on).apply()
    fun bar(c: Context) = p(c).getBoolean("bar", true)
    fun setBar(c: Context, on: Boolean) = p(c).edit().putBoolean("bar", on).apply()
    fun notification(c: Context) = p(c).getBoolean("notification", true)
    fun setNotification(c: Context, on: Boolean) = p(c).edit().putBoolean("notification", on).apply()

    /** Where the user dragged the button to: distance of its centre from the right edge (in screen heights) and its height fraction. */
    fun position(c: Context): Pair<Float, Float> = p(c).getFloat("pos_right", 0.195f) to p(c).getFloat("pos_y", 0.635f)
    fun setPosition(c: Context, rightOffset: Float, y: Float) =
        p(c).edit().putFloat("pos_right", rightOffset.coerceIn(0f, 1.5f)).putFloat("pos_y", y.coerceIn(0f, 1f)).apply()

    /** True when "Display over other apps" is allowed for this app. */
    fun overlayAllowed(c: Context): Boolean = Settings.canDrawOverlays(c)

    /** True when "Usage access" is allowed for automatic Clash of Clans foreground detection. */
    fun usageAccessAllowed(c: Context): Boolean = runCatching {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Lightweight cached builder/helper readiness summary shown as an idle pill on the overlay button. */
    fun setQuickStatus(c: Context, freeBuilders: Int, readyHelpers: Int) {
        p(c).edit()
            .putInt("qs_free_builders", freeBuilders.coerceAtLeast(0))
            .putInt("qs_ready_helpers", readyHelpers.coerceAtLeast(0))
            .apply()
    }

    fun quickStatus(c: Context): Pair<Int, Int> {
        val prefs = p(c)
        return prefs.getInt("qs_free_builders", 0) to prefs.getInt("qs_ready_helpers", 0)
    }
}
