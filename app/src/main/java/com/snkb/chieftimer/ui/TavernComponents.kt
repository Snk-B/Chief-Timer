package com.snkb.chieftimer.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.ui.theme.Chief3DPalette
import com.snkb.chieftimer.ui.theme.ChiefThemeMode
import com.snkb.chieftimer.ui.theme.LilitaOne
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tracks whether the enclosing pager page is settled and active so infinite decorations
 * (such as wavy progress bars) pause while off-screen or mid-swipe.
 */
internal val LocalPageActiveState = compositionLocalOf<State<Boolean>> { mutableStateOf(true) }

private val BtnShape11 = RoundedCornerShape(11.dp)
private val CardShape14 = RoundedCornerShape(14.dp)
private val BannerShape16 = RoundedCornerShape(16.dp)
private val PillShape20 = RoundedCornerShape(20.dp)

internal object TavernTextureCache {
    private val brushes = ConcurrentHashMap<Int, ShaderBrush>()

    fun brush(context: Context, @DrawableRes resId: Int): ShaderBrush {
        brushes[resId]?.let { return it }
        return synchronized(this) {
            brushes[resId] ?: run {
                val bmp = BitmapFactory.decodeResource(context.applicationContext.resources, resId)
                    ?: error("Missing tavern texture resource $resId")
                val shaderBrush = ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Mirror, TileMode.Mirror))
                brushes[resId] = shaderBrush
                shaderBrush
            }
        }
    }
}

@Composable
fun rememberWoodBrush(): Brush {
    val context = LocalContext.current
    return remember(context) { TavernTextureCache.brush(context, R.drawable.tex_tavern_wood) }
}

@Composable
fun rememberDarkWoodBrush(): Brush {
    val context = LocalContext.current
    return remember(context) { TavernTextureCache.brush(context, R.drawable.tex_tavern_wood_dark) }
}

@Composable
fun rememberStoneBrush(): Brush {
    val context = LocalContext.current
    return remember(context) { TavernTextureCache.brush(context, R.drawable.tex_tavern_stone) }
}

/**
 * Authentic Supercell-style Lilita One text with crisp black/dark-brown stroke + drop shadow.
 */
@Composable
fun ClashOutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    color: Color = Color.White,
    outlineColor: Color = Color(0xFF140B04),
    strokeWidth: Float = 5.5f,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    tabularNums: Boolean = false,
) {
    val feat = if (tabularNums) "tnum" else null
    val baseStyle = TextStyle(
        fontFamily = LilitaOne,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize,
        fontFeatureSettings = feat,
        textAlign = textAlign ?: TextAlign.Unspecified,
    )
    Box(modifier) {
        Text(
            text = text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = baseStyle.copy(
                color = outlineColor,
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
                shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 4f), blurRadius = 2f),
            ),
        )
        Text(
            text = text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = baseStyle.copy(color = color),
        )
    }
}

enum class ClashButtonStyle { GREEN, STONE, GOLD }

/**
 * 3D Glossy Clash of Clans button with press-down physics, bottom 3D bevel lip, and top specular reflection.
 */
@Composable
fun Clash3DButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ClashButtonStyle = ClashButtonStyle.GREEN,
    minHeight: Dp = 46.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressOffset = if (pressed && enabled) 2.dp else 0.dp

    val pal = LocalChiefPalette.current
    val (topGrad, botGrad, lipColor, borderColor) = when {
        !enabled -> pal.btnDisabled
        style == ClashButtonStyle.GREEN -> pal.btnGreen
        style == ClashButtonStyle.GOLD -> pal.btnGold
        else -> pal.btnStone
    }

    val shape = BtnShape11
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .offset(y = pressOffset)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .drawBehind {
                val r = 11.dp.toPx()
                val lip = if (pressed) 1.5.dp.toPx() else 4.dp.toPx()
                // Outer dark border + bottom 3D lip
                drawRoundRect(color = borderColor, cornerRadius = CornerRadius(r, r))
                val inset = 1.5.dp.toPx()
                drawRoundRect(
                    color = lipColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(r - inset, r - inset),
                )
                // Main gradient body
                val bodyH = (size.height - inset * 2 - lip).coerceAtLeast(4f)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(topGrad, botGrad), startY = inset, endY = inset + bodyH),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, bodyH),
                    cornerRadius = CornerRadius(r - inset, r - inset),
                )
                // Glossy top specular shine band
                val glossPad = 4.dp.toPx()
                val glossH = (bodyH * 0.42f).coerceAtLeast(2f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f)),
                        startY = inset + 2.dp.toPx(),
                        endY = inset + 2.dp.toPx() + glossH,
                    ),
                    topLeft = Offset(glossPad, inset + 2.dp.toPx()),
                    size = Size((size.width - glossPad * 2).coerceAtLeast(2f), glossH),
                    cornerRadius = CornerRadius(r * 0.7f, r * 0.7f),
                )
            }
            .padding(bottom = if (pressed) 1.dp else 3.dp)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Polished Oak Wood Panel framed by a double beveled golden-bronze border (Village Command Banner).
 */
@Composable
fun TavernWoodBanner(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalChiefPalette.current
    val woodBrush = when (pal.mode) {
        ChiefThemeMode.CLASH -> rememberWoodBrush()
        ChiefThemeMode.DARK -> rememberDarkWoodBrush()
        else -> null
    }
    val bannerOuter = pal.bannerOuter
    val bannerGroove = pal.bannerGroove
    val accentLight = pal.accentLight
    val accent = pal.accent
    val accentDark = pal.accentDark
    val bannerTop = pal.bannerTop
    val bannerMid = pal.bannerMid
    val bannerBot = pal.bannerBot
    val isDarkWood = pal.mode == ChiefThemeMode.DARK
    val shape = BannerShape16
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .drawBehind {
                val r = 16.dp.toPx()
                // 1. Dark outer outline
                drawRoundRect(bannerOuter, cornerRadius = CornerRadius(r, r))
                // 2. Metallic bevel frame
                val gPad = 1.5.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(accentLight, accent, accentDark)),
                    topLeft = Offset(gPad, gPad),
                    size = Size(size.width - gPad * 2, size.height - gPad * 2),
                    cornerRadius = CornerRadius(r - gPad, r - gPad),
                )
                // 3. Inner dark groove
                val iPad = 4.5.dp.toPx()
                drawRoundRect(
                    color = bannerGroove,
                    topLeft = Offset(iPad, iPad),
                    size = Size(size.width - iPad * 2, size.height - iPad * 2),
                    cornerRadius = CornerRadius(r - iPad, r - iPad),
                )
                // 4. Polished Oak Wood (Classic) or Dynamic/Obsidian interior
                val wPad = 6.dp.toPx()
                if (woodBrush != null) {
                    drawRoundRect(
                        brush = woodBrush,
                        topLeft = Offset(wPad, wPad),
                        size = Size(size.width - wPad * 2, size.height - wPad * 2),
                        cornerRadius = CornerRadius(r - wPad, r - wPad),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            if (isDarkWood) listOf(Color(0x188BA0C4), Color.Transparent, Color(0x4205070B))
                            else listOf(Color(0x1EFFE3A3), Color.Transparent, Color(0x36140A03)),
                        ),
                        topLeft = Offset(wPad, wPad),
                        size = Size(size.width - wPad * 2, size.height - wPad * 2),
                        cornerRadius = CornerRadius(r - wPad, r - wPad),
                    )
                } else {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(bannerTop, bannerMid, bannerBot),
                        ),
                        topLeft = Offset(wPad, wPad),
                        size = Size(size.width - wPad * 2, size.height - wPad * 2),
                        cornerRadius = CornerRadius(r - wPad, r - wPad),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(accentLight.copy(alpha = 0.11f), Color.Transparent, Color(0x66020306)),
                        ),
                        topLeft = Offset(wPad, wPad),
                        size = Size(size.width - wPad * 2, size.height - wPad * 2),
                        cornerRadius = CornerRadius(r - wPad, r - wPad),
                    )
                }
            }
            .padding(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

internal enum class StoneCardPart { FULL, TOP, MIDDLE, BOTTOM }

/**
 * Draws the 3D chiseled stone plaque aesthetic (smooth charcoal/slate stone face, crisp bright
 * top bevel catchlight, dark bottom lip & edge outline, plus Supercell-style V-chip edge cracks)
 * across both standalone [TavernStoneCard]s and virtualized section items.
 */
internal fun DrawScope.drawChiseledStoneSurface(
    pal: Chief3DPalette,
    highlighted: Boolean = false,
    part: StoneCardPart = StoneCardPart.FULL,
) {
    val isClash = pal.mode == ChiefThemeMode.CLASH
    val isDark = pal.mode == ChiefThemeMode.DARK
    val isAmoled = pal.mode == ChiefThemeMode.MATERIAL_AMOLED

    val faceColors = when {
        isClash && highlighted -> listOf(Color(0xFF463B24), Color(0xFF392F1C), Color(0xFF2B2314))
        isClash -> listOf(Color(0xFF434647), Color(0xFF36393A), Color(0xFF2A2C2D))
        isDark && highlighted -> listOf(Color(0xFF312B1C), Color(0xFF262115), Color(0xFF1B170F))
        isDark -> listOf(Color(0xFF2C3548), Color(0xFF212838), Color(0xFF171D2A))
        highlighted -> listOf(pal.cardHighlightTop, pal.cardHighlightBot)
        else -> listOf(pal.cardTop, pal.cardMid, pal.cardBot)
    }
    val outerOutline = when {
        highlighted -> pal.accent
        isClash -> Color(0xFF090A0A)
        isDark -> Color(0xFF04060A)
        else -> lerp(pal.cardBorder, Color.Black, 0.55f)
    }
    val topBevel = when {
        highlighted -> pal.accentLight
        isClash -> Color(0xFF868B8A)
        isDark -> Color(0xFF7D92BA)
        else -> lerp(pal.cardBorder, Color.White, if (isAmoled) 0.16f else 0.30f)
    }
    val sideBevel = when {
        highlighted -> Color(0xFFDFAC3A)
        isClash -> Color(0xFF676C6C)
        isDark -> Color(0xFF576B8F)
        else -> pal.cardBorder
    }
    val botBevel = when {
        highlighted && isDark -> Color(0xFFA87D24)
        highlighted -> Color(0xFFB88726)
        isClash -> Color(0xFF545859)
        isDark -> Color(0xFF455675)
        else -> lerp(pal.cardBorder, Color.Black, if (isAmoled) 0.25f else 0.35f)
    }
    val crackDark = outerOutline.copy(alpha = 0.90f)
    val crackLight = topBevel.copy(alpha = if (isAmoled) 0.38f else 0.62f)

    // 1. Satin carved stone face + subtle top-half stone sheen
    if (part == StoneCardPart.MIDDLE) {
        drawRect(color = faceColors[faceColors.size / 2])
    } else {
        drawRect(brush = Brush.verticalGradient(faceColors))
    }
    if (part == StoneCardPart.FULL || part == StoneCardPart.TOP) {
        val sheenH = size.height * (if (part == StoneCardPart.FULL) 0.45f else 0.75f)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (isAmoled) 0.025f else 0.045f), Color.Transparent),
                startY = 0f,
                endY = sheenH,
            ),
            size = Size(size.width, sheenH),
        )
    }

    val w = size.width
    val h = size.height
    val r = 14.dp.toPx()
    val outerW = (if (highlighted) 1.5.dp else 1.25.dp).toPx()
    val bevelW = 1.7.dp.toPx()
    val bevelIn = outerW + bevelW * 0.45f

    fun strokeEdges(radius: Float, inset: Float, topColor: Color, sideColor: Color, botColor: Color, strokeWidth: Float) {
        val ir = (radius - inset).coerceAtLeast(2f)
        when (part) {
            StoneCardPart.FULL -> {
                // Side & bottom base rim
                drawRoundRect(
                    color = sideColor,
                    topLeft = Offset(inset, inset),
                    size = Size(w - inset * 2, h - inset * 2),
                    cornerRadius = CornerRadius(ir, ir),
                    style = Stroke(width = strokeWidth),
                )
                // Bright top chamfer line + shoulders
                val topPath = Path().apply {
                    moveTo(inset, inset + ir)
                    arcTo(Rect(inset, inset, inset + ir * 2, inset + ir * 2), 180f, 90f, false)
                    lineTo(w - inset - ir, inset)
                    arcTo(Rect(w - inset - ir * 2, inset, w - inset, inset + ir * 2), 270f, 90f, false)
                }
                drawPath(topPath, topColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Closed 360 bottom chamfer line + lower shoulders
                val botPath = Path().apply {
                    moveTo(inset, h - inset - ir)
                    arcTo(Rect(inset, h - inset - ir * 2, inset + ir * 2, h - inset), 180f, -90f, false)
                    lineTo(w - inset - ir, h - inset)
                    arcTo(Rect(w - inset - ir * 2, h - inset - ir * 2, w - inset, h - inset), 90f, -90f, false)
                }
                drawPath(botPath, botColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            StoneCardPart.TOP -> {
                val path = Path().apply {
                    moveTo(inset, h)
                    lineTo(inset, inset + ir)
                    arcTo(Rect(inset, inset, inset + ir * 2, inset + ir * 2), 180f, 90f, false)
                    lineTo(w - inset - ir, inset)
                    arcTo(Rect(w - inset - ir * 2, inset, w - inset, inset + ir * 2), 270f, 90f, false)
                    lineTo(w - inset, h)
                }
                drawPath(path, sideColor, style = Stroke(width = strokeWidth))
                val topArc = Path().apply {
                    moveTo(inset, inset + ir)
                    arcTo(Rect(inset, inset, inset + ir * 2, inset + ir * 2), 180f, 90f, false)
                    lineTo(w - inset - ir, inset)
                    arcTo(Rect(w - inset - ir * 2, inset, w - inset, inset + ir * 2), 270f, 90f, false)
                }
                drawPath(topArc, topColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
            StoneCardPart.MIDDLE -> {
                drawLine(sideColor, Offset(inset, 0f), Offset(inset, h), strokeWidth = strokeWidth)
                drawLine(sideColor, Offset(w - inset, 0f), Offset(w - inset, h), strokeWidth = strokeWidth)
            }
            StoneCardPart.BOTTOM -> {
                val path = Path().apply {
                    moveTo(inset, 0f)
                    lineTo(inset, h - inset - ir)
                    arcTo(Rect(inset, h - inset - ir * 2, inset + ir * 2, h - inset), 180f, -90f, false)
                    lineTo(w - inset - ir, h - inset)
                    arcTo(Rect(w - inset - ir * 2, h - inset - ir * 2, w - inset, h - inset), 90f, -90f, false)
                    lineTo(w - inset, 0f)
                }
                drawPath(path, botColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(sideColor, Offset(inset, 0f), Offset(inset, h - inset - ir), strokeWidth = strokeWidth)
                drawLine(sideColor, Offset(w - inset, 0f), Offset(w - inset, h - inset - ir), strokeWidth = strokeWidth)
            }
        }
    }

    // 2. Inner chiseled bevel rim
    strokeEdges(r, bevelIn, topBevel, sideBevel, botBevel, bevelW)

    // 3. Chiseled stone edge V-notches & short hairline cracks along stone rim
    val crackShift = 0.85.dp.toPx()
    val crackStroke = 1.15.dp.toPx()
    fun drawCrack(pts: List<Offset>) {
        if (pts.size < 2) return
        val lightPath = Path().apply {
            moveTo(pts[0].x + crackShift, pts[0].y + crackShift)
            for (i in 1 until pts.size) lineTo(pts[i].x + crackShift, pts[i].y + crackShift)
        }
        val darkPath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(lightPath, crackLight, style = Stroke(width = crackStroke * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(darkPath, crackDark, style = Stroke(width = crackStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    if (part == StoneCardPart.FULL || part == StoneCardPart.TOP) {
        val xL = 24.dp.toPx()
        val yT = outerW * 0.55f
        val notchL = Path().apply {
            moveTo(xL - 2.6.dp.toPx(), yT)
            lineTo(xL + 2.6.dp.toPx(), yT)
            lineTo(xL + 0.2.dp.toPx(), yT + 3.4.dp.toPx())
            close()
        }
        drawLine(
            color = crackLight,
            start = Offset(xL + 0.2.dp.toPx(), yT + 3.4.dp.toPx()),
            end = Offset(xL + 3.0.dp.toPx(), yT + 0.4.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawPath(notchL, outerOutline)
        drawCrack(listOf(Offset(xL + 0.2.dp.toPx(), yT + 3.1.dp.toPx()), Offset(xL + 2.5.dp.toPx(), yT + 6.4.dp.toPx()), Offset(xL + 1.0.dp.toPx(), yT + 9.2.dp.toPx())))

        if (w > 120.dp.toPx()) {
            val xR = w - 26.dp.toPx()
            val notchR = Path().apply {
                moveTo(xR - 1.9.dp.toPx(), yT)
                lineTo(xR + 1.9.dp.toPx(), yT)
                lineTo(xR, yT + 2.6.dp.toPx())
                close()
            }
            drawPath(notchR, outerOutline)
        }
    }
    if (part == StoneCardPart.FULL || part == StoneCardPart.BOTTOM) {
        val xR = w - 24.dp.toPx()
        val yB = h - outerW * 0.55f
        val notchBR = Path().apply {
            moveTo(xR - 2.6.dp.toPx(), yB)
            lineTo(xR + 2.6.dp.toPx(), yB)
            lineTo(xR - 0.2.dp.toPx(), yB - 3.4.dp.toPx())
            close()
        }
        drawLine(
            color = crackLight,
            start = Offset(xR - 3.0.dp.toPx(), yB - 0.4.dp.toPx()),
            end = Offset(xR - 0.2.dp.toPx(), yB - 3.4.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawPath(notchBR, outerOutline)
        drawCrack(listOf(Offset(xR - 0.2.dp.toPx(), yB - 3.1.dp.toPx()), Offset(xR - 2.5.dp.toPx(), yB - 6.4.dp.toPx()), Offset(xR - 1.0.dp.toPx(), yB - 9.2.dp.toPx())))

        if (w > 120.dp.toPx()) {
            val xL = 32.dp.toPx()
            val notchBL = Path().apply {
                moveTo(xL - 1.8.dp.toPx(), yB)
                lineTo(xL + 1.8.dp.toPx(), yB)
                lineTo(xL, yB - 2.5.dp.toPx())
                close()
            }
            drawPath(notchBL, outerOutline)
        }
    }

    // 4. Outer dark crisp border enclosing the entire carved stone plaque
    strokeEdges(r, outerW / 2f, outerOutline, outerOutline, outerOutline, outerW)
}

/**
 * Chiseled Dark Stone / Obsidian Plaque Card with 3D stone bevel rim and carved edge cracks.
 */
@Composable
fun TavernStoneCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalChiefPalette.current
    val shape = CardShape14
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wiggleOnTap()
            .shadow(5.dp, shape)
            .clip(shape)
            .drawBehind {
                drawChiseledStoneSurface(pal = pal, highlighted = highlighted, part = StoneCardPart.FULL)
            }
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/**
 * Shield-shaped carved stone stat plaque with overlapping 3D golden crest badge at top center.
 */
@Composable
fun TavernStatShield(
    @DrawableRes crestRes: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val pal = LocalChiefPalette.current
    val shieldOuter = pal.shieldOuter
    val shieldRimTop = pal.shieldRimTop
    val shieldRimBot = pal.shieldRimBot
    val shieldGroove = pal.shieldGroove
    val shieldFaceTop = pal.shieldFaceTop
    val shieldFaceMid = pal.shieldFaceMid
    val shieldFaceBot = pal.shieldFaceBot
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.padding(top = 14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Shield-pointed plaque
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    fun shieldPath(inset: Float): Path {
                        val w = size.width - inset * 2
                        val h = size.height - inset * 2
                        val r = (10.dp.toPx() - inset * 0.5f).coerceAtLeast(3f)
                        val sideBottom = inset + h * 0.84f
                        val tipY = inset + h
                        val left = inset
                        val right = inset + w
                        val top = inset
                        return Path().apply {
                            moveTo(left + r, top)
                            lineTo(right - r, top)
                            quadraticTo(right, top, right, top + r)
                            lineTo(right, sideBottom - r * 0.5f)
                            quadraticTo(right, sideBottom, right - r * 0.6f, sideBottom + r * 0.25f)
                            lineTo(size.width / 2f, tipY)
                            lineTo(left + r * 0.6f, sideBottom + r * 0.25f)
                            quadraticTo(left, sideBottom, left, sideBottom - r * 0.5f)
                            lineTo(left, top + r)
                            quadraticTo(left, top, left + r, top)
                            close()
                        }
                    }
                    // Dark outer outline
                    drawPath(shieldPath(0f), shieldOuter)
                    // Chiseled bevel border
                    drawPath(
                        shieldPath(1.5.dp.toPx()),
                        Brush.verticalGradient(listOf(shieldRimTop, shieldRimBot)),
                    )
                    // Inner dark groove
                    drawPath(shieldPath(3.5.dp.toPx()), shieldGroove)
                    // Carved plaque face
                    drawPath(
                        shieldPath(5.dp.toPx()),
                        Brush.verticalGradient(listOf(shieldFaceTop, shieldFaceMid, shieldFaceBot)),
                    )
                }
                .padding(top = 28.dp, bottom = 14.dp, start = 6.dp, end = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ClashOutlinedText(
                    text = label,
                    fontSize = 11.5.sp,
                    color = secondaryColor,
                    strokeWidth = 3.5f,
                    maxLines = 1,
                )
                ClashOutlinedText(
                    text = value,
                    fontSize = 16.5.sp,
                    color = Color.White,
                    strokeWidth = 5f,
                    maxLines = 1,
                    tabularNums = true,
                )
            }
        }

        // Overlapping 3D Golden Shield Crest at top center
        Image(
            painter = painterResource(crestRes),
            contentDescription = null,
            modifier = Modifier
                .offset(y = (-15).dp)
                .size(42.dp),
        )
    }
}

/**
 * Beveled Tavern Filter Pill (Golden when selected, Dark Carved Stone / Obsidian when unselected).
 */
@Composable
fun TavernFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalChiefPalette.current
    val shape = PillShape20
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pillScale",
    )
    val topColor by animateColorAsState(
        targetValue = if (selected) pal.pillActiveTop else pal.pillIdleTop,
        animationSpec = tween(220),
        label = "pillTop",
    )
    val botColor by animateColorAsState(
        targetValue = if (selected) pal.pillActiveBot else pal.pillIdleBot,
        animationSpec = tween(220),
        label = "pillBot",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) pal.pillActiveBorder else pal.pillIdleBorder,
        animationSpec = tween(220),
        label = "pillBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) pal.pillActiveText else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(220),
        label = "pillText",
    )

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .scale(pillScale)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topColor, botColor)))
            .border(1.3.dp, borderColor, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        ClashOutlinedText(
            text = label,
            fontSize = 14.sp,
            color = textColor,
            strokeWidth = 4.2f,
        )
    }
}

/**
 * Beveled Golden Square Frame for 3D Building / Troop / Pet Icons on Upgrade Cards.
 */
@Composable
fun TavernIconFrame(
    size: Dp = 60.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val pal = LocalChiefPalette.current
    val shape = BtnShape11
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(pal.iconSlotOuter)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(pal.accentLight, pal.accent, pal.accentDark)))
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(pal.iconSlotTop, pal.iconSlotBot))),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

enum class TavernBarTone { GREEN, ELIXIR, GOLD }

/**
 * Glossy Recessed Tube Progress Bar with continuous animated wavy crest inside (0% to 100%).
 */
@Composable
fun TavernWavyTubeBar(
    progress: Float,
    tone: TavernBarTone,
    modifier: Modifier = Modifier,
) {
    val pal = LocalChiefPalette.current
    val clamped = progress.coerceIn(0f, 1f)
    val displayFrac = maxOf(0.035f, clamped)
    val reduced = rememberReducedMotion()
    val pageActive = LocalPageActiveState.current.value
    val animateWave = !reduced && pageActive && displayFrac > 0.01f
    val phase = if (!animateWave) {
        0f
    } else {
        val infinite = rememberInfiniteTransition(label = "tavernWave")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhase",
        ).value
    }

    val troughPath = remember { Path() }
    val fillPath = remember { Path() }
    val trackWave = remember { Path() }

    val (fillTop, fillBot, waveCrestColor) = when (tone) {
        TavernBarTone.ELIXIR -> pal.barSecondary
        TavernBarTone.GOLD -> pal.barTertiary
        TavernBarTone.GREEN -> pal.barPrimary
    }

    val trackRim = pal.barTrackRim
    val trackBg = pal.barTrackBg
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        troughPath.reset()
        fillPath.reset()
        trackWave.reset()
        val r = size.height / 2f
        // 1. Recessed channel + bevel rim
        drawRoundRect(
            color = trackRim,
            cornerRadius = CornerRadius(r, r),
        )
        val pad = 1.5.dp.toPx()
        val innerW = (size.width - pad * 2).coerceAtLeast(4f)
        val innerH = (size.height - pad * 2).coerceAtLeast(4f)
        val innerR = innerH / 2f
        val troughRect = RoundRect(Rect(Offset(pad, pad), Size(innerW, innerH)), CornerRadius(innerR, innerR))
        troughPath.addRoundRect(troughRect)

        drawRoundRect(
            color = trackBg,
            topLeft = Offset(pad, pad),
            size = Size(innerW, innerH),
            cornerRadius = CornerRadius(innerR, innerR),
        )

        clipPath(troughPath) {
            val fillW = (innerW * displayFrac).coerceAtLeast(innerH)
            // 2. Base glossy colored fill
            val fillRect = RoundRect(Rect(Offset(pad, pad), Size(fillW, innerH)), CornerRadius(innerR, innerR))
            fillPath.addRoundRect(fillRect)
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(listOf(fillTop, fillBot), startY = pad, endY = pad + innerH),
            )

            // 3. Animated sine-wave ribbon running inside the tube + along the full track subtly
            val waveLen = 34.dp.toPx()
            val amp = (innerH * 0.23f).coerceAtLeast(1.8f)
            val cy = pad + innerH * 0.52f

            // Subtle track wave ahead of fill so even low-% bars clearly show the living wave
            val step = 3.dp.toPx().coerceAtLeast(2f)
            var x = pad
            var first = true
            while (x <= pad + innerW) {
                val y = cy + amp * sin(((x / waveLen) * 2f * PI.toFloat()) - phase)
                if (first) {
                    trackWave.moveTo(x, y)
                    first = false
                } else {
                    trackWave.lineTo(x, y)
                }
                x += step
            }
            drawPath(
                path = trackWave,
                color = fillTop.copy(alpha = 0.28f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Bright glowing wave inside the filled tube
            clipPath(fillPath) {
                drawPath(
                    path = trackWave,
                    color = waveCrestColor.copy(alpha = 0.88f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                )
                // Top specular glass highlight
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(pad + 3.dp.toPx(), pad + 1.5.dp.toPx()),
                    size = Size((fillW - 6.dp.toPx()).coerceAtLeast(2f), innerH * 0.32f),
                    cornerRadius = CornerRadius(innerR, innerR),
                )
            }
        }
    }
}

/**
 * Tactile spring wiggle and subtle clock-tick haptic on card tap without swallowing inner clicks or scrolls.
 */
internal fun Modifier.wiggleOnTap(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = tilt.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scope.launch {
                    scale.animateTo(0.986f, spring(stiffness = Spring.StiffnessMediumLow))
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                scope.launch {
                    tilt.snapTo(0.35f)
                    tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                waitForUpOrCancellation()
            }
        }
}

/**
 * Shared 2x2 selector grid for the 4 Chief Timer 3D themes (App + Home-Screen Widgets).
 */
@Composable
fun ThemeSelectorGrid(
    selectedTheme: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeKey = ChiefThemeMode.fromKey(selectedTheme).key
    val rows = listOf(
        listOf(
            ChiefThemeMode.CLASH.key to R.string.widget_theme_clash,
            ChiefThemeMode.DARK.key to R.string.widget_theme_dark,
        ),
        listOf(
            ChiefThemeMode.MATERIAL_YOU.key to R.string.widget_theme_material_you,
            ChiefThemeMode.MATERIAL_AMOLED.key to R.string.widget_theme_black,
        ),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (key, labelRes) ->
                    TavernFilterPill(
                        label = stringResource(labelRes),
                        selected = activeKey == key,
                        onClick = { onSelect(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
