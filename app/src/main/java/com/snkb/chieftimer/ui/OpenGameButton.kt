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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private const val GAME_PACKAGE = "com.supercell.clashofclans"

/** 3D "Abrir jogo" badge with the installed Clash of Clans icon, dynamically recolored by [LocalChiefPalette]. */
@Composable
fun OpenGameButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pal = LocalChiefPalette.current
    val gameIcon = remember {
        runCatching { context.packageManager.getApplicationIcon(GAME_PACKAGE).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(pal.chipOuter)
            .padding(1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(pal.accentLight, pal.accent, pal.accentDark)))
            .padding(1.5.dp)
            .clip(RoundedCornerShape(7.5.dp))
            .background(Brush.verticalGradient(listOf(pal.chipFillTop, pal.chipFillBot)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (gameIcon != null) {
                Image(gameIcon, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
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
