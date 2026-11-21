package com.snkb.chieftimer.sync

import android.content.Context
import kotlin.math.abs

/**
 * Live Countdown Notification / Samsung Now Bar (Settings > Notifications).
 * Shows an ongoing promoted countdown notification during the final [minutes] before an upgrade finishes.
 */
object LiveUpdateAlert {
    private const val PREFS = "live_update_alert"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_POSTED = "posted_map"
    private const val KEY_DISMISSED = "dismissed_jobs"

    /** Lead-time choices in minutes before an upgrade completes. */
    val CHOICES = listOf(3, 5, 10, 15, 30, 60)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun minutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTES, 5).takeIf { it in CHOICES } ?: 5

    fun setMinutes(context: Context, mins: Int) {
        val valid = if (mins in CHOICES) mins else 5
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MINUTES, valid).apply()
    }

    /** Lead window in seconds, or null when the feature is turned off. */
    fun leadSec(context: Context): Long? = if (enabled(context)) minutes(context) * 60L else null

    /** Records that the user tapped "Dismiss alert" on a specific live countdown job. */
    fun dismissJob(context: Context, accountTag: String, jobKey: String, finishAt: Long, nowSec: Long = System.currentTimeMillis() / 1000) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = readDismissed(prefs.getString(KEY_DISMISSED, null))
            .filter { it.third > nowSec - 600 }
            .toMutableList()
        active.removeAll { it.first == accountTag && it.second == jobKey }
        active.add(Triple(accountTag, jobKey, finishAt))
        val encoded = active.joinToString(";") { "${it.first}|${it.second}|${it.third}" }
        prefs.edit().putString(KEY_DISMISSED, encoded).apply()
    }

    /** True if the user dismissed the live countdown for this specific upgrade instance. */
    fun isDismissed(context: Context, accountTag: String, jobKey: String, finishAt: Long): Boolean {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DISMISSED, null)
            ?: return false
        return readDismissed(raw).any { (tag, key, fin) ->
            tag == accountTag && key == jobKey && abs(fin - finishAt) <= 120L
        }
    }

    private fun readDismissed(raw: String?): List<Triple<String, String, Long>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val p = entry.split('|')
            if (p.size == 3) {
                val fin = p[2].toLongOrNull() ?: return@mapNotNull null
                Triple(p[0], p[1], fin)
            } else null
        }
    }

    /** Map of currently posted `notifId -> finishAtSec` (used for fallback non-FGS notifications). */
    fun postedMap(context: Context): Map<Int, Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_POSTED, null)
            ?: return emptyMap()
        return raw.split(';')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) {
                    val id = parts[0].toIntOrNull()
                    val fin = parts[1].toLongOrNull()
                    if (id != null && fin != null) id to fin else null
                } else null
            }
            .toMap()
    }

    fun savePostedMap(context: Context, map: Map<Int, Long>) {
        val encoded = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_POSTED, encoded).apply()
    }
}
