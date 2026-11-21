package com.snkb.chieftimer.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snkb.chieftimer.core.EventsConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The downloaded event schedule. It refreshes quietly in the background every time the app opens (switchable in
 * Clash > Events) and on demand; the last good copy is kept, so it also works offline.
 */
object EventsStore {
    private const val URL_TEXT = "https://raw.githubusercontent.com/SnK-B/Chief-Timer/main/data/events.json"
    private const val PREFS = "events_remote"
    private const val MIN_GAP_MS = 60_000L // not again within a minute (screen rotation, quick re-opens)
    private const val MAX_BYTES = 256 * 1024

    var config by mutableStateOf(EventsConfig.EMPTY)
        private set

    /** When the schedule was last downloaded successfully (epoch millis), 0 if never. */
    var updatedAt by mutableStateOf(0L)
        private set

    @Volatile private var loaded = false

    private fun file(c: Context) = File(c.applicationContext.filesDir, "events_remote.json")
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun auto(c: Context) = prefs(c).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()

    /** Reads the saved copy once (falling back to bundled `assets/events.json`). */
    fun load(c: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val remoteText = runCatching { file(c).takeIf { it.exists() }?.readText() }.getOrNull()
            val parsedRemote = remoteText?.let { EventsConfig.parse(it) }
            if (parsedRemote != null) {
                config = parsedRemote
            } else {
                runCatching {
                    c.applicationContext.assets.open("events.json").bufferedReader().use { it.readText() }
                }.getOrNull()?.let { EventsConfig.parse(it) }?.let { config = it }
            }
            updatedAt = prefs(c).getLong("updated", 0L)
        }
    }

    /** Downloads the schedule now. Throws [IOException] when it cannot be reached or is not a usable events file. */
    suspend fun refresh(c: Context) {
        val text = withContext(Dispatchers.IO) {
            val conn = URL(URL_TEXT).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "ChiefTimer-events")
                if (conn.responseCode != 200) throw IOException("The event list answered ${conn.responseCode}")
                if (conn.contentLengthLong > MAX_BYTES) throw IOException("The event list is too large")
                (conn.inputStream.use { it.readBounded(MAX_BYTES) } ?: throw IOException("The event list is too large"))
                    .toString(Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        }
        val parsed = EventsConfig.parse(text) ?: throw IOException("The event list is not valid")
        file(c).writeText(text)
        val now = System.currentTimeMillis()
        prefs(c).edit().putLong("updated", now).putLong("checked", now).apply()
        config = parsed
        updatedAt = now
    }

    /** Called each time the app opens: refreshes when switched on, unless it just did. Failures are silent. */
    suspend fun refreshIfDue(c: Context) {
        load(c)
        if (!auto(c)) return
        val now = System.currentTimeMillis()
        val last = prefs(c).getLong("checked", 0L)
        if (now - last in 0 until MIN_GAP_MS) return
        prefs(c).edit().putLong("checked", now).apply()
        runCatching { refresh(c) }
    }
}
