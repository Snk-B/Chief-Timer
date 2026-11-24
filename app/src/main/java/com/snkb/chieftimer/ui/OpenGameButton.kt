package com.snkb.chieftimer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.snkb.chieftimer.R
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GAME_PACKAGE = "com.supercell.clashofclans"

internal object GameIconCache {
    @Volatile var loaded = false
        private set
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    suspend fun warmUp(context: android.content.Context) {
        if (loaded) return
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(GAME_PACKAGE).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        withContext(Dispatchers.Main.immediate) {
            if (!loaded) {
                bitmap = resolved
                loaded = true
            }
        }
    }
}

/** 3D "Abrir jogo" badge with the installed Clash of Clans icon, dynamically recolored by [LocalChiefPalette]. */
@Composable
fun OpenGameButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val gameIcon = GameIconCache.bitmap
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!GameIconCache.loaded) {
            GameIconCache.warmUp(context)
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val chipOuter = pal.chipOuter
    val accentLight = pal.accentLight
    val accent = pal.accent
    val accentDark = pal.accentDark
    val fillTop = pal.chipFillTop
    val fillBot = pal.chipFillBot
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .drawWithCache {
                val r10 = 10.dp.toPx()
                val r9 = 9.dp.toPx()
                val r7 = 7.5.dp.toPx()
                val p1 = 1.dp.toPx()
                val p25 = 2.5.dp.toPx()
                val rimBrush = Brush.verticalGradient(listOf(accentLight, accent, accentDark))
                val fillBrush = Brush.verticalGradient(listOf(fillTop, fillBot))
                onDrawBehind {
                    drawRoundRect(chipOuter, cornerRadius = CornerRadius(r10, r10))
                    drawRoundRect(
                        brush = rimBrush,
                        topLeft = Offset(p1, p1),
                        size = Size(size.width - p1 * 2, size.height - p1 * 2),
                        cornerRadius = CornerRadius(r9, r9),
                    )
                    drawRoundRect(
                        brush = fillBrush,
                        topLeft = Offset(p25, p25),
                        size = Size(size.width - p25 * 2, size.height - p25 * 2),
                        cornerRadius = CornerRadius(r7, r7),
                    )
                }
            }
            .let { m -> if (enabled) m.clickable(role = Role.Button, onClick = onClick) else m }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val iconBmp = gameIcon
            if (iconBmp != null) {
                Image(iconBmp, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
            } else {
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = pal.accentLight,
                    modifier = Modifier.size(18.dp),
                )
            }
            ClashOutlinedText(
                text = stringResource(R.string.open_game_short),
                fontSize = 12.sp,
                color = Color.White,
                strokeWidth = 3.8f,
                maxLines = 1,
            )
        }
    }
}
