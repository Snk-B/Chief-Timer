package com.snkb.chieftimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snkb.chieftimer.R
import com.snkb.chieftimer.ui.theme.LocalChiefPalette
import com.snkb.chieftimer.ui.theme.TavernGold
import com.snkb.chieftimer.ui.theme.TavernGoldDark
import com.snkb.chieftimer.ui.theme.TavernGoldLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class EmberSpec(
    val xFrac: Float,
    val startPhase: Float,
    val speed: Float,
    val radiusDp: Float,
    val swayAmp: Float,
)

/**
 * Choreographed Supercell Taverna Clash entrance animation:
 * - Rotating golden god-rays & rising tavern embers
 * - 3D Royal Shield & Crossed Builder Hammers Chronometer Crest slamming in with spring physics
 * - Expanding golden impact shockwave + sweeping 360° glowing chrono ring + metallic shine sweep
 * - Staggered "Chief Timer" title pop + unfurling "Welcome, Chief!" golden ribbon
 * Tap anywhere to skip immediately.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val pal = LocalChiefPalette.current
    val bgBrush = if (!pal.useTextures) {
        Brush.verticalGradient(listOf(pal.screenBgTop, pal.screenBgMid, pal.screenBgBot))
    } else {
        rememberStoneBrush()
    }
    val rayPrimary = pal.accentLight.copy(alpha = 0.18f)
    val raySecondary = pal.accent.copy(alpha = 0.08f)
    val vignetteInner = pal.accent.copy(alpha = 0.26f)
    val vignetteMid = pal.accent.copy(alpha = 0.12f)
    val vignetteOuter = pal.screenBgBot.copy(alpha = 0.92f)
    val emberGlow = pal.accent.copy(alpha = 0.34f)
    val accentLight = pal.accentLight
    val accent = pal.accent
    val accentDark = pal.accentDark
    val statusGreen = pal.statusGreen

    val auraScale = remember { Animatable(0.35f) }
    val crestScale = remember { Animatable(0.18f) }
    val crestRotation = remember { Animatable(-14f) }
    val shockwave = remember { Animatable(0f) }
    val ringSweep = remember { Animatable(0f) }
    val shimmer = remember { Animatable(-0.6f) }
    val titleScale = remember { Animatable(0.45f) }
    val titleAlpha = remember { Animatable(0f) }
    val ribbonScaleX = remember { Animatable(0.20f) }
    val ribbonAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "splashAmbient")
    val rayRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rayRotation",
    )
    val emberClock by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "emberClock",
    )

    val embers = remember {
        listOf(
            EmberSpec(0.16f, 0.05f, 1.1f, 2.4f, 14f),
            EmberSpec(0.24f, 0.42f, 0.9f, 3.1f, 18f),
            EmberSpec(0.31f, 0.78f, 1.3f, 2.0f, 11f),
            EmberSpec(0.38f, 0.21f, 1.0f, 3.5f, 20f),
            EmberSpec(0.45f, 0.63f, 1.2f, 2.2f, 15f),
            EmberSpec(0.52f, 0.12f, 0.85f, 3.2f, 16f),
            EmberSpec(0.59f, 0.51f, 1.25f, 2.6f, 19f),
            EmberSpec(0.66f, 0.87f, 1.05f, 3.0f, 13f),
            EmberSpec(0.73f, 0.33f, 1.15f, 2.3f, 17f),
            EmberSpec(0.81f, 0.69f, 0.95f, 2.8f, 14f),
            EmberSpec(0.28f, 0.92f, 1.35f, 1.9f, 12f),
            EmberSpec(0.76f, 0.15f, 1.2f, 2.5f, 16f),
        )
    }

    LaunchedEffect(Unit) {
        // 1. Radial tavern glow blooms + Crest slams in with bouncy Supercell spring
        launch {
            auraScale.animateTo(1.12f, tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            crestScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.54f, stiffness = 340f),
            )
        }
        launch {
            crestRotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = 310f),
            )
        }
        // 2. Impact shockwave ring + sweeping 360° golden chrono arc
        launch {
            delay(90)
            shockwave.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
        }
        launch {
            delay(110)
            ringSweep.animateTo(1f, tween(920, easing = FastOutSlowInEasing))
        }
        // 3. Diagonal metallic gold shine sweep across the 3D shield & stopwatch
        launch {
            delay(260)
            shimmer.animateTo(1.65f, tween(760, easing = FastOutSlowInEasing))
        }
        // 4. Staggered Title pop & Golden "Welcome, Chief!" Ribbon unfurl
        launch {
            delay(170)
            launch { titleAlpha.animateTo(1f, tween(200)) }
            titleScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = 380f),
            )
        }
        launch {
            delay(310)
            launch { ribbonAlpha.animateTo(1f, tween(220)) }
            ribbonScaleX.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.66f, stiffness = 390f),
            )
        }

        delay(1520)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onFinished() },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient Vignette + Rotating Sunburst God-Rays + Rising Golden Embers + Shockwave
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.44f
            val maxR = size.minDimension * 0.72f * auraScale.value

            // Dark outer vignette for dramatic lighting
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        vignetteInner,
                        vignetteMid,
                        vignetteOuter,
                    ),
                    center = Offset(cx, cy),
                    radius = maxR.coerceAtLeast(80f),
                ),
            )

            // 12 Rotating Golden God-Rays
            val rayCount = 12
            val rayLen = size.minDimension * 0.56f * auraScale.value
            val halfAngleRad = (7.5f * PI / 180f).toFloat()
            rotate(degrees = rayRotation, pivot = Offset(cx, cy)) {
                for (i in 0 until rayCount) {
                    val baseRad = ((i * (360f / rayCount)) * PI / 180f).toFloat()
                    val x1 = cx + cos(baseRad - halfAngleRad) * rayLen
                    val y1 = cy + sin(baseRad - halfAngleRad) * rayLen
                    val x2 = cx + cos(baseRad + halfAngleRad) * rayLen
                    val y2 = cy + sin(baseRad + halfAngleRad) * rayLen
                    val rayPath = Path().apply {
                        moveTo(cx, cy)
                        lineTo(x1, y1)
                        lineTo(x2, y2)
                        close()
                    }
                    drawPath(
                        path = rayPath,
                        brush = Brush.radialGradient(
                            colors = listOf(rayPrimary, raySecondary, Color.Transparent),
                            center = Offset(cx, cy),
                            radius = rayLen.coerceAtLeast(40f),
                        ),
                    )
                }
            }

            // Expanding Impact Shockwave Ring
            val sw = shockwave.value
            if (sw > 0.01f && sw < 0.99f) {
                val swRadius = (72.dp.toPx()) + sw * (145.dp.toPx())
                val swAlpha = (1f - sw) * 0.72f
                drawCircle(
                    color = accentLight.copy(alpha = swAlpha),
                    radius = swRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = ((1f - sw) * 5.dp.toPx()).coerceAtLeast(1f)),
                )
            }

            // Rising Ambient Embers
            val riseSpan = size.height * 0.55f
            val baseStartY = cy + 120.dp.toPx()
            embers.forEach { e ->
                val p = (emberClock * e.speed + e.startPhase) % 1f
                val ex = cx + (e.xFrac - 0.5f) * size.width * 0.82f +
                    sin((p * 2f * PI).toFloat()) * e.swayAmp * density
                val ey = baseStartY - p * riseSpan
                val eAlpha = sin((p * PI).toFloat()).coerceIn(0f, 1f) * 0.80f * auraScale.value.coerceAtMost(1f)
                val rPx = e.radiusDp.dp.toPx()
                drawCircle(
                    color = emberGlow.copy(alpha = eAlpha * 0.45f),
                    radius = rPx * 2.4f,
                    center = Offset(ex, ey),
                )
                drawCircle(
                    color = accentLight.copy(alpha = eAlpha),
                    radius = rPx,
                    center = Offset(ex, ey),
                )
            }
        }

        // Foreground Choreographed Emblem + Title + Ribbon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Crest + Sweeping 360° Chrono Halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(236.dp),
            ) {
                // Carved Chrono Halo Ring & Glowing Sweep Arc
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(crestScale.value.coerceAtMost(1.06f)),
                ) {
                    val strokeW = 4.5.dp.toPx()
                    val inset = strokeW * 1.5f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    // Subtle recessed ring track
                    drawArc(
                        color = accentLight.copy(alpha = 0.27f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeW),
                    )
                    // Sweeping Progress Arc
                    val sweepDeg = 360f * ringSweep.value
                    if (sweepDeg > 1f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(accentDark, accent, accentLight, statusGreen, accentLight),
                            ),
                            startAngle = -90f,
                            sweepAngle = sweepDeg,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        )
                        // Glowing spark tip at leading edge of the sweep
                        val tipRad = ((-90f + sweepDeg) * PI / 180f).toFloat()
                        val radius = arcSize.width / 2f
                        val tx = size.width / 2f + cos(tipRad) * radius
                        val ty = size.height / 2f + sin(tipRad) * radius
                        drawCircle(
                            color = Color(0x88FFF6B8),
                            radius = 9.dp.toPx(),
                            center = Offset(tx, ty),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(tx, ty),
                        )
                    }
                }

                // 3D Royal Shield + Crossed Hammers + Chronometer Crest with Metallic Shine Sweep
                val sProg = shimmer.value
                Image(
                    painter = painterResource(R.drawable.ic_tavern_splash_crest),
                    contentDescription = null,
                    modifier = Modifier
                        .size(198.dp)
                        .graphicsLayer {
                            scaleX = crestScale.value
                            scaleY = crestScale.value
                            rotationZ = crestRotation.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            if (sProg > -0.5f && sProg < 1.55f) {
                                val bandW = size.width * 0.45f
                                val startX = sProg * size.width
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x70FFF6CC),
                                            Color.Transparent,
                                        ),
                                        start = Offset(startX - bandW, 0f),
                                        end = Offset(startX + bandW, size.height),
                                    ),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        },
                )
            }

            Spacer(Modifier.height(14.dp))

            // App Title Pop
            ClashOutlinedText(
                text = stringResource(R.string.app_name),
                fontSize = 33.sp,
                color = TavernGoldLight,
                strokeWidth = 7f,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .scale(titleScale.value),
            )

            Spacer(Modifier.height(10.dp))

            // Unfurling Golden Tavern Ribbon ("Welcome, Chief!" / "Bem-vindo, Chefe!")
            val ribbonShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .alpha(ribbonAlpha.value)
                    .graphicsLayer {
                        scaleX = ribbonScaleX.value
                    }
                    .clip(ribbonShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(pal.ribbonEdge, pal.ribbonCenter, pal.ribbonEdge),
                        ),
                    )
                    .border(1.4.dp, Brush.verticalGradient(listOf(TavernGoldLight, TavernGoldDark)), ribbonShape)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                ClashOutlinedText(
                    text = "✦  ${stringResource(R.string.welcome_chief)}  ✦",
                    fontSize = 14.5.sp,
                    color = TavernGold,
                    strokeWidth = 4.2f,
                )
            }
        }
    }
}
