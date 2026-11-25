package com.snkb.chieftimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.snkb.chieftimer.data.MaxDataStore
import com.snkb.chieftimer.widget.WidgetIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns the cached game icon synchronously if already in RAM, otherwise decodes it on
 * [Dispatchers.IO] without stalling Compose layout or pager swipe animations.
 */
@Composable
internal fun rememberGameIcon(id: Int?, level: Int?, sizeDp: Dp, minPx: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val version = MaxDataStore.version
    val sizePx = maxOf(with(density) { sizeDp.roundToPx() }, minPx).coerceAtLeast(1)
    var bitmap by remember(id, level, sizePx, version) {
        val hit = id?.let { WidgetIcons.get(context).cachedImageBitmapForLevel(it, level, sizePx) }
        mutableStateOf(hit)
    }
    if (id != null && bitmap == null) {
        LaunchedEffect(id, level, sizePx, version) {
            val loaded = withContext(Dispatchers.IO) {
                WidgetIcons.get(context).imageBitmapForLevel(id, level, sizePx)
            }
            if (loaded != null) bitmap = loaded
        }
    }
    return bitmap
}

/**
 * Picks the first available icon from [ids], reading RAM cache synchronously and decoding
 * asynchronously on [Dispatchers.IO] on cache miss.
 */
@Composable
internal fun rememberFirstGameIcon(ids: List<Int>, level: Int?, sizeDp: Dp, minPx: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val version = MaxDataStore.version
    val sizePx = maxOf(with(density) { sizeDp.roundToPx() }, minPx).coerceAtLeast(1)
    var bitmap by remember(ids, level, sizePx, version) {
        val hit = if (ids.isEmpty()) null else {
            WidgetIcons.get(context).cachedFirstImageBitmapForLevel(ids, level, sizePx)
        }
        mutableStateOf(hit)
    }
    if (ids.isNotEmpty() && bitmap == null) {
        LaunchedEffect(ids, level, sizePx, version) {
            val loaded = withContext(Dispatchers.IO) {
                val icons = WidgetIcons.get(context)
                ids.firstNotNullOfOrNull { icons.imageBitmapForLevel(it, level, sizePx) }
            }
            if (loaded != null) bitmap = loaded
        }
    }
    return bitmap
}

/**
 * Loads a named icon (e.g. "goblin") from RAM cache if warm, or on [Dispatchers.IO] otherwise.
 */
@Composable
internal fun rememberNamedGameIcon(name: String, sizeDp: Dp, enabled: Boolean = true): ImageBitmap? {
    if (!enabled) return null
    val context = LocalContext.current
    val density = LocalDensity.current
    val version = MaxDataStore.version
    val sizePx = with(density) { sizeDp.roundToPx() }.coerceAtLeast(1)
    var bitmap by remember(name, sizePx, version) {
        mutableStateOf(WidgetIcons.get(context).cachedImageBitmap(name, sizePx))
    }
    if (bitmap == null) {
        LaunchedEffect(name, sizePx, version) {
            val loaded = withContext(Dispatchers.IO) {
                WidgetIcons.get(context).imageBitmap(name, sizePx)
            }
            if (loaded != null) bitmap = loaded
        }
    }
    return bitmap
}

/**
 * Loads high-res Town Hall artwork from RAM cache or asynchronously on [Dispatchers.IO].
 */
@Composable
internal fun rememberTownHallIcon(level: Int?, sizeDp: Dp, minPx: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val version = MaxDataStore.version
    val sizePx = maxOf(with(density) { sizeDp.roundToPx() }, minPx).coerceAtLeast(1)
    var bitmap by remember(level, sizePx, version) {
        mutableStateOf(WidgetIcons.get(context).cachedTownHallImage(level, sizePx))
    }
    if (level != null && bitmap == null) {
        LaunchedEffect(level, sizePx, version) {
            val loaded = withContext(Dispatchers.IO) {
                WidgetIcons.get(context).townHallImage(level, sizePx)
            }
            if (loaded != null) bitmap = loaded
        }
    }
    return bitmap
}
