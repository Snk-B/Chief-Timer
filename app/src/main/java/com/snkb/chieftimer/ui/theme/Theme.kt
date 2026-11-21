@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.snkb.chieftimer.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

enum class ChiefThemeMode(val key: String) {
    CLASH("clash"),
    DARK("dark"),
    MATERIAL_YOU("material_you"),
    MATERIAL_AMOLED("black"),
    ;

    val isDark: Boolean get() = this != CLASH

    companion object {
        fun fromKey(key: String?): ChiefThemeMode = when (key) {
            "dark" -> DARK
            "material_you" -> MATERIAL_YOU
            "black" -> MATERIAL_AMOLED
            else -> CLASH
        }
    }
}

@Immutable
data class Chief3DPalette(
    val mode: ChiefThemeMode,
    val useTextures: Boolean,
    val isDark: Boolean,
    val accent: Color,
    val accentLight: Color,
    val accentDark: Color,
    val statusGreen: Color,
    val bannerSubText: Color,
    val screenBgTop: Color,
    val screenBgMid: Color,
    val screenBgBot: Color,
    val headerBgTop: Color,
    val headerBgMid: Color,
    val headerBgBot: Color,
    val dockBgTop: Color,
    val dockBgBot: Color,
    val navIndicatorTop: Color,
    val navIndicatorBot: Color,
    val bannerOuter: Color,
    val bannerGroove: Color,
    val bannerTop: Color,
    val bannerMid: Color,
    val bannerBot: Color,
    val cardTop: Color,
    val cardMid: Color,
    val cardBot: Color,
    val cardBorder: Color,
    val cardHighlightTop: Color,
    val cardHighlightBot: Color,
    val shieldOuter: Color,
    val shieldRimTop: Color,
    val shieldRimBot: Color,
    val shieldGroove: Color,
    val shieldFaceTop: Color,
    val shieldFaceMid: Color,
    val shieldFaceBot: Color,
    val iconSlotOuter: Color,
    val iconSlotTop: Color,
    val iconSlotBot: Color,
    val chipOuter: Color,
    val chipRimTop: Color,
    val chipRimBot: Color,
    val chipFillTop: Color,
    val chipFillBot: Color,
    val btnDisabled: List<Color>,
    val btnGreen: List<Color>,
    val btnGold: List<Color>,
    val btnStone: List<Color>,
    val pillActiveTop: Color,
    val pillActiveBot: Color,
    val pillActiveBorder: Color,
    val pillActiveText: Color,
    val pillIdleTop: Color,
    val pillIdleBot: Color,
    val pillIdleBorder: Color,
    val ribbonEdge: Color,
    val ribbonCenter: Color,
    val ribbonText: Color,
    val barTrackRim: Color,
    val barTrackBg: Color,
    val barPrimary: Triple<Color, Color, Color>,
    val barSecondary: Triple<Color, Color, Color>,
    val barTertiary: Triple<Color, Color, Color>,
) {
    companion object {
        fun clash() = Chief3DPalette(
            mode = ChiefThemeMode.CLASH,
            useTextures = true,
            isDark = false,
            accent = Color(0xFFFFCA45),
            accentLight = Color(0xFFFFE999),
            accentDark = Color(0xFF8C5B14),
            statusGreen = Color(0xFF6EEB3F),
            bannerSubText = Color(0xFFFFECC2),
            screenBgTop = Color(0xFF151311),
            screenBgMid = Color(0xFF11100E),
            screenBgBot = Color(0xFF0C0B09),
            headerBgTop = TavernWoodLight,
            headerBgMid = TavernWoodMid,
            headerBgBot = TavernWoodDark,
            dockBgTop = TavernWoodMid,
            dockBgBot = TavernWoodDark,
            navIndicatorTop = Color(0xFF754B1D),
            navIndicatorBot = Color(0xFF3E230B),
            bannerOuter = Color(0xFF140C05),
            bannerGroove = Color(0xFF2B1708),
            bannerTop = TavernWoodLight,
            bannerMid = TavernWoodMid,
            bannerBot = TavernWoodDark,
            cardTop = Color(0xFF2D2721),
            cardMid = TavernStoneCard,
            cardBot = Color(0xFF211D18),
            cardBorder = Color(0xFF6E5E4E),
            cardHighlightTop = Color(0xFF3A2D1D),
            cardHighlightBot = Color(0xFF261E15),
            shieldOuter = Color(0xFF120F0C),
            shieldRimTop = Color(0xFF7D7265),
            shieldRimBot = Color(0xFF4A4239),
            shieldGroove = Color(0xFF211D19),
            shieldFaceTop = Color(0xFF474038),
            shieldFaceMid = Color(0xFF332E28),
            shieldFaceBot = Color(0xFF28241F),
            iconSlotOuter = Color(0xFF120D07),
            iconSlotTop = Color(0xFF3B3228),
            iconSlotBot = Color(0xFF1F1A15),
            chipOuter = Color(0xFF1E1005),
            chipRimTop = Color(0xFF7D5328),
            chipRimBot = Color(0xFF472C13),
            chipFillTop = Color(0xFF4D2F14),
            chipFillBot = Color(0xFF331D0B),
            btnDisabled = listOf(Color(0xFF55504A), Color(0xFF3A3632), Color(0xFF221F1C), Color(0xFF141210)),
            btnGreen = listOf(Color(0xFF72ED42), Color(0xFF2AA11B), Color(0xFF145C0D), Color(0xFF0A2407)),
            btnGold = listOf(Color(0xFFFFDE6A), Color(0xFFD18817), Color(0xFF754708), Color(0xFF241403)),
            btnStone = listOf(Color(0xFF706559), Color(0xFF473F37), Color(0xFF29241F), Color(0xFF14110E)),
            pillActiveTop = Color(0xFFE8A82C),
            pillActiveBot = Color(0xFF9E620F),
            pillActiveBorder = Color(0xFFFFE999),
            pillActiveText = Color(0xFFFFF6D6),
            pillIdleTop = Color(0xFF342E27),
            pillIdleBot = Color(0xFF231F1A),
            pillIdleBorder = Color(0xFF6B5D4E),
            ribbonEdge = Color(0xFF6B410B),
            ribbonCenter = Color(0xFF8C5812),
            ribbonText = Color(0xFFFFF4D2),
            barTrackRim = Color(0xFF473E34),
            barTrackBg = Color(0xFF161310),
            barPrimary = Triple(Color(0xFF6EEA3E), Color(0xFF269619), Color(0xFFBDFCA0)),
            barSecondary = Triple(Color(0xFFF670FF), Color(0xFF941EC8), Color(0xFFFFB5FF)),
            barTertiary = Triple(Color(0xFFFFDF5E), Color(0xFFCC8514), Color(0xFFFFF3B0)),
        )

        fun dark() = Chief3DPalette(
            mode = ChiefThemeMode.DARK,
            useTextures = false,
            isDark = true,
            accent = Color(0xFFFFCA45),
            accentLight = Color(0xFFFFE999),
            accentDark = Color(0xFF8C5B14),
            statusGreen = Color(0xFF6EEB3F),
            bannerSubText = Color(0xFFE2E8F5),
            screenBgTop = Color(0xFF0A0D14),
            screenBgMid = Color(0xFF07090F),
            screenBgBot = Color(0xFF05060A),
            headerBgTop = Color(0xFF1A202E),
            headerBgMid = Color(0xFF111520),
            headerBgBot = Color(0xFF0B0E15),
            dockBgTop = Color(0xFF141824),
            dockBgBot = Color(0xFF0A0D14),
            navIndicatorTop = Color(0xFF2B354D),
            navIndicatorBot = Color(0xFF161C29),
            bannerOuter = Color(0xFF07090D),
            bannerGroove = Color(0xFF090C12),
            bannerTop = Color(0xFF1C2332),
            bannerMid = Color(0xFF121722),
            bannerBot = Color(0xFF0B0E16),
            cardTop = Color(0xFF171C28),
            cardMid = ObsidianCard,
            cardBot = Color(0xFF0E121A),
            cardBorder = Color(0xFF43506B),
            cardHighlightTop = Color(0xFF282217),
            cardHighlightBot = Color(0xFF161513),
            shieldOuter = Color(0xFF06080C),
            shieldRimTop = Color(0xFF515E78),
            shieldRimBot = Color(0xFF283042),
            shieldGroove = Color(0xFF0B0E15),
            shieldFaceTop = Color(0xFF22293A),
            shieldFaceMid = Color(0xFF161B26),
            shieldFaceBot = Color(0xFF10141D),
            iconSlotOuter = Color(0xFF07090D),
            iconSlotTop = Color(0xFF222A3B),
            iconSlotBot = Color(0xFF111520),
            chipOuter = Color(0xFF07090D),
            chipRimTop = Color(0xFF4F5C75),
            chipRimBot = Color(0xFF283042),
            chipFillTop = Color(0xFF22293A),
            chipFillBot = Color(0xFF141824),
            btnDisabled = listOf(Color(0xFF394152), Color(0xFF232834), Color(0xFF13161E), Color(0xFF080A0F)),
            btnGreen = listOf(Color(0xFF72ED42), Color(0xFF2AA11B), Color(0xFF145C0D), Color(0xFF0A2407)),
            btnGold = listOf(Color(0xFFFFDE6A), Color(0xFFD18817), Color(0xFF754708), Color(0xFF241403)),
            btnStone = listOf(Color(0xFF4F5C75), Color(0xFF2E3647), Color(0xFF181D29), Color(0xFF090C12)),
            pillActiveTop = Color(0xFFE8A82C),
            pillActiveBot = Color(0xFF9E620F),
            pillActiveBorder = Color(0xFFFFE999),
            pillActiveText = Color(0xFFFFF6D6),
            pillIdleTop = Color(0xFF1F2638),
            pillIdleBot = Color(0xFF121722),
            pillIdleBorder = Color(0xFF47546E),
            ribbonEdge = Color(0xFF4A3310),
            ribbonCenter = Color(0xFF6B4712),
            ribbonText = Color(0xFFFFF4D2),
            barTrackRim = Color(0xFF2D3547),
            barTrackBg = Color(0xFF0A0D13),
            barPrimary = Triple(Color(0xFF6EEA3E), Color(0xFF269619), Color(0xFFBDFCA0)),
            barSecondary = Triple(Color(0xFFF670FF), Color(0xFF941EC8), Color(0xFFFFB5FF)),
            barTertiary = Triple(Color(0xFFFFDF5E), Color(0xFFCC8514), Color(0xFFFFF3B0)),
        )

        private fun vividPair(base: Color, container: Color, fallbackTop: Color, fallbackBot: Color): Pair<Color, Color> {
            val top = if (base.luminance() > 0.08f) lerp(base, Color.White, 0.20f) else fallbackTop
            val bot = lerp(container, base, 0.42f).let { if (it.luminance() < 0.04f) fallbackBot else it }
            return top to bot
        }

        fun materialYou(dyn: ColorScheme): Chief3DPalette {
            val primaryBright = lerp(dyn.primary, Color.White, 0.22f)
            val accentLight = lerp(dyn.primary, Color.White, 0.48f)
            val accent = lerp(dyn.primary, accentLight, 0.25f)
            val accentDark = lerp(dyn.primaryContainer, dyn.primary, 0.34f)
            val secondaryBright = lerp(dyn.secondary, Color.White, 0.30f)
            val tertiaryBright = lerp(dyn.tertiary, Color.White, 0.24f)

            val tintBase = lerp(dyn.surfaceContainerLow, dyn.primaryContainer, 0.30f)
            val tintElevated = lerp(dyn.surfaceContainerHigh, dyn.primaryContainer, 0.34f)
            val tintHighest = lerp(dyn.surfaceContainerHighest, dyn.primary, 0.22f)

            val (pTop, pBot) = vividPair(dyn.primary, dyn.primaryContainer, Color(0xFF54B6FF), Color(0xFF1D6EC7))
            val (tTop, tBot) = vividPair(dyn.tertiary, dyn.tertiaryContainer, Color(0xFFB39DFF), Color(0xFF563BB8))
            val (sTop, sBot) = vividPair(dyn.secondary, dyn.secondaryContainer, Color(0xFF6AE0C8), Color(0xFF1E8574))

            return Chief3DPalette(
                mode = ChiefThemeMode.MATERIAL_YOU,
                useTextures = false,
                isDark = true,
                accent = accent,
                accentLight = accentLight,
                accentDark = accentDark,
                statusGreen = secondaryBright,
                bannerSubText = lerp(dyn.onSurface, dyn.primary, 0.28f),
                screenBgTop = lerp(dyn.background, dyn.primaryContainer, 0.26f),
                screenBgMid = lerp(dyn.background, dyn.primaryContainer, 0.18f),
                screenBgBot = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.14f),
                headerBgTop = lerp(tintElevated, dyn.primary, 0.14f),
                headerBgMid = tintBase,
                headerBgBot = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.22f),
                dockBgTop = tintBase,
                dockBgBot = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.20f),
                navIndicatorTop = lerp(dyn.primaryContainer, dyn.primary, 0.40f),
                navIndicatorBot = dyn.primaryContainer,
                bannerOuter = lerp(dyn.surfaceContainerLowest, Color.Black, 0.55f),
                bannerGroove = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.28f),
                bannerTop = tintElevated,
                bannerMid = tintBase,
                bannerBot = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.25f),
                cardTop = lerp(tintElevated, Color.White, 0.04f),
                cardMid = tintBase,
                cardBot = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.20f),
                cardBorder = lerp(dyn.outline, accent, 0.48f),
                cardHighlightTop = lerp(dyn.primaryContainer, dyn.primary, 0.25f),
                cardHighlightBot = dyn.primaryContainer,
                shieldOuter = lerp(dyn.surfaceContainerLowest, Color.Black, 0.65f),
                shieldRimTop = lerp(accent, dyn.outline, 0.28f),
                shieldRimBot = accentDark,
                shieldGroove = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.22f),
                shieldFaceTop = tintHighest,
                shieldFaceMid = tintElevated,
                shieldFaceBot = tintBase,
                iconSlotOuter = lerp(dyn.surfaceContainerLowest, Color.Black, 0.60f),
                iconSlotTop = tintHighest,
                iconSlotBot = tintBase,
                chipOuter = lerp(dyn.surfaceContainerLowest, Color.Black, 0.60f),
                chipRimTop = lerp(accent, tintHighest, 0.35f),
                chipRimBot = accentDark,
                chipFillTop = tintElevated,
                chipFillBot = tintBase,
                btnDisabled = listOf(lerp(tintElevated, Color.Gray, 0.25f), tintBase, dyn.surfaceContainerLowest, Color(0xFF090B0E)),
                btnGreen = listOf(pTop, pBot, lerp(pBot, Color.Black, 0.45f), lerp(pBot, Color.Black, 0.72f)),
                btnGold = listOf(tTop, tBot, lerp(tBot, Color.Black, 0.45f), lerp(tBot, Color.Black, 0.72f)),
                btnStone = listOf(tintHighest, tintBase, dyn.surfaceContainerLowest, Color(0xFF0A0D12)),
                pillActiveTop = pTop,
                pillActiveBot = pBot,
                pillActiveBorder = accentLight,
                pillActiveText = lerp(dyn.onPrimary, Color.White, 0.85f),
                pillIdleTop = tintElevated,
                pillIdleBot = tintBase,
                pillIdleBorder = lerp(dyn.outline, accent, 0.35f),
                ribbonEdge = dyn.primaryContainer,
                ribbonCenter = lerp(dyn.primaryContainer, dyn.primary, 0.32f),
                ribbonText = lerp(dyn.onPrimaryContainer, Color.White, 0.45f),
                barTrackRim = lerp(dyn.outline, accent, 0.30f),
                barTrackBg = lerp(dyn.surfaceContainerLowest, dyn.primaryContainer, 0.16f),
                barPrimary = Triple(pTop, pBot, lerp(primaryBright, Color.White, 0.55f)),
                barSecondary = Triple(tTop, tBot, lerp(tertiaryBright, Color.White, 0.55f)),
                barTertiary = Triple(sTop, sBot, lerp(secondaryBright, Color.White, 0.55f)),
            )
        }

        fun materialAmoled(dyn: ColorScheme): Chief3DPalette {
            val primaryBright = lerp(dyn.primary, Color.White, 0.22f)
            val accentLight = lerp(dyn.primary, Color.White, 0.52f)
            val accent = lerp(dyn.primary, accentLight, 0.22f)
            val accentDark = lerp(dyn.primaryContainer, dyn.primary, 0.38f)
            val secondaryBright = lerp(dyn.secondary, Color.White, 0.28f)
            val tertiaryBright = lerp(dyn.tertiary, Color.White, 0.24f)

            val (pTop, pBot) = vividPair(dyn.primary, dyn.primaryContainer, Color(0xFFC38CFF), Color(0xFF6B2AD6))
            val (tTop, tBot) = vividPair(dyn.tertiary, dyn.tertiaryContainer, Color(0xFFF59BFF), Color(0xFF9C29C2))
            val (sTop, sBot) = vividPair(dyn.secondary, dyn.secondaryContainer, Color(0xFF7AE8FF), Color(0xFF1F82A6))

            return Chief3DPalette(
                mode = ChiefThemeMode.MATERIAL_AMOLED,
                useTextures = false,
                isDark = true,
                accent = accent,
                accentLight = accentLight,
                accentDark = accentDark,
                statusGreen = secondaryBright,
                bannerSubText = lerp(Color(0xFFD5DCE8), accentLight, 0.26f),
                screenBgTop = Color.Black,
                screenBgMid = Color.Black,
                screenBgBot = Color.Black,
                headerBgTop = Color(0xFF0A0D13),
                headerBgMid = Color(0xFF040508),
                headerBgBot = Color.Black,
                dockBgTop = Color(0xFF080A0F),
                dockBgBot = Color.Black,
                navIndicatorTop = lerp(dyn.primaryContainer, dyn.primary, 0.34f),
                navIndicatorBot = Color(0xFF0D0F16),
                bannerOuter = Color.Black,
                bannerGroove = Color(0xFF040508),
                bannerTop = Color(0xFF10131C),
                bannerMid = Color(0xFF090B11),
                bannerBot = Color(0xFF030407),
                cardTop = Color(0xFF0E1118),
                cardMid = Color(0xFF07090E),
                cardBot = Color(0xFF020305),
                cardBorder = lerp(Color(0xFF282F3F), accent, 0.42f),
                cardHighlightTop = lerp(Color(0xFF14121D), dyn.primaryContainer, 0.45f),
                cardHighlightBot = Color(0xFF08080D),
                shieldOuter = Color.Black,
                shieldRimTop = lerp(Color(0xFF3E4659), accent, 0.50f),
                shieldRimBot = lerp(Color(0xFF1B1F2B), accentDark, 0.55f),
                shieldGroove = Color(0xFF040508),
                shieldFaceTop = Color(0xFF131722),
                shieldFaceMid = Color(0xFF0A0C13),
                shieldFaceBot = Color(0xFF040509),
                iconSlotOuter = Color.Black,
                iconSlotTop = Color(0xFF151A26),
                iconSlotBot = Color(0xFF07090E),
                chipOuter = Color.Black,
                chipRimTop = lerp(Color(0xFF384054), accent, 0.52f),
                chipRimBot = accentDark,
                chipFillTop = Color(0xFF121620),
                chipFillBot = Color(0xFF06070B),
                btnDisabled = listOf(Color(0xFF2A303E), Color(0xFF171B24), Color(0xFF0B0D13), Color.Black),
                btnGreen = listOf(pTop, pBot, lerp(pBot, Color.Black, 0.52f), Color.Black),
                btnGold = listOf(tTop, tBot, lerp(tBot, Color.Black, 0.52f), Color.Black),
                btnStone = listOf(Color(0xFF252C3B), Color(0xFF12161F), Color(0xFF07090E), Color.Black),
                pillActiveTop = pTop,
                pillActiveBot = pBot,
                pillActiveBorder = accentLight,
                pillActiveText = lerp(dyn.onPrimary, Color.White, 0.88f),
                pillIdleTop = Color(0xFF11151E),
                pillIdleBot = Color(0xFF06080C),
                pillIdleBorder = lerp(Color(0xFF2D3546), accent, 0.35f),
                ribbonEdge = lerp(Color(0xFF0C0E16), dyn.primaryContainer, 0.45f),
                ribbonCenter = lerp(Color(0xFF151926), dyn.primaryContainer, 0.65f),
                ribbonText = lerp(dyn.onPrimaryContainer, Color.White, 0.55f),
                barTrackRim = lerp(Color(0xFF262D3B), accent, 0.40f),
                barTrackBg = Color(0xFF030406),
                barPrimary = Triple(pTop, pBot, lerp(primaryBright, Color.White, 0.58f)),
                barSecondary = Triple(tTop, tBot, lerp(tertiaryBright, Color.White, 0.58f)),
                barTertiary = Triple(sTop, sBot, lerp(secondaryBright, Color.White, 0.58f)),
            )
        }

        private val FallbackSapphireDynamic = darkColorScheme(
            primary = Color(0xFF7CC4FF),
            onPrimary = Color(0xFF002D4F),
            primaryContainer = Color(0xFF174A7A),
            onPrimaryContainer = Color(0xFFD6EAFF),
            secondary = Color(0xFF66E2D5),
            onSecondary = Color(0xFF003731),
            secondaryContainer = Color(0xFF144F4A),
            onSecondaryContainer = Color(0xFFC2FFF7),
            tertiary = Color(0xFFB3ADFF),
            onTertiary = Color(0xFF231966),
            tertiaryContainer = Color(0xFF3E348C),
            onTertiaryContainer = Color(0xFFE6E2FF),
            background = Color(0xFF101929),
            surface = Color(0xFF162238),
            surfaceContainerLowest = Color(0xFF0B111E),
            surfaceContainerLow = Color(0xFF152035),
            surfaceContainer = Color(0xFF1A2842),
            surfaceContainerHigh = Color(0xFF213252),
            surfaceContainerHighest = Color(0xFF2A3F66),
            outline = Color(0xFF5F7AA6),
        )

        private val FallbackAmethystDynamic = darkColorScheme(
            primary = Color(0xFFB983FF),
            onPrimary = Color(0xFF2E0059),
            primaryContainer = Color(0xFF4F2487),
            onPrimaryContainer = Color(0xFFF0DBFF),
            secondary = Color(0xFF72E2FF),
            onSecondary = Color(0xFF003547),
            secondaryContainer = Color(0xFF134B66),
            onSecondaryContainer = Color(0xFFC8F3FF),
            tertiary = Color(0xFFF382FF),
            onTertiary = Color(0xFF4D0059),
            tertiaryContainer = Color(0xFF721D82),
            onTertiaryContainer = Color(0xFFFFD6FF),
            background = Color.Black,
            surface = Color(0xFF080A0F),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF07090D),
            surfaceContainer = Color(0xFF0D1017),
            surfaceContainerHigh = Color(0xFF141824),
            surfaceContainerHighest = Color(0xFF1C2233),
            outline = Color(0xFF6C5491),
        )

        fun resolve(context: Context, mode: ChiefThemeMode): Chief3DPalette = when (mode) {
            ChiefThemeMode.CLASH -> clash()
            ChiefThemeMode.DARK -> dark()
            ChiefThemeMode.MATERIAL_YOU -> {
                val dyn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else FallbackSapphireDynamic
                materialYou(dyn)
            }
            ChiefThemeMode.MATERIAL_AMOLED -> {
                val dyn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else FallbackAmethystDynamic
                materialAmoled(dyn)
            }
        }
    }
}

val LocalChiefPalette = staticCompositionLocalOf { Chief3DPalette.clash() }
val LocalTavernDark = compositionLocalOf { false }

val TavernGold: Color
    @Composable @ReadOnlyComposable get() = LocalChiefPalette.current.accent
val TavernGoldLight: Color
    @Composable @ReadOnlyComposable get() = LocalChiefPalette.current.accentLight
val TavernGoldDark: Color
    @Composable @ReadOnlyComposable get() = LocalChiefPalette.current.accentDark

val TavernWoodLight = Color(0xFF8C582B)
val TavernWoodMid = Color(0xFF6B411E)
val TavernWoodDark = Color(0xFF3E2410)
val TavernStoneBg = Color(0xFF191613)
val TavernStoneCard = Color(0xFF26221D)
val TavernStoneElevated = Color(0xFF332D27)
val TavernStoneBorder = Color(0xFF52473B)
val TavernGreenLight: Color
    @Composable @ReadOnlyComposable get() = LocalChiefPalette.current.statusGreen
val TavernGreenMid = Color(0xFF32AD1E)
val TavernGreenDark = Color(0xFF155E0E)
val TavernElixirLight: Color
    @Composable @ReadOnlyComposable get() = LocalChiefPalette.current.barSecondary.first
val TavernElixirDark = Color(0xFF8E1EC2)
val TavernTextPrimary = Color(0xFFFAF4E8)
val TavernTextSecondary = Color(0xFFE5D6BF)

val ObsidianBg = Color(0xFF090B0F)
val ObsidianCard = Color(0xFF12161F)
val ObsidianElevated = Color(0xFF1B212E)
val ObsidianBorder = Color(0xFF3A4459)
val ObsidianTextPrimary = Color(0xFFF5F7FA)
val ObsidianTextSecondary = Color(0xFFCBD4E4)

private fun Chief3DPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF16130E),
    primaryContainer = ribbonCenter,
    onPrimaryContainer = accentLight,
    secondary = statusGreen,
    onSecondary = Color(0xFF0A1D08),
    secondaryContainer = cardHighlightTop,
    onSecondaryContainer = Color(0xFFE6FFE0),
    tertiary = barSecondary.first,
    onTertiary = Color(0xFF290538),
    tertiaryContainer = Color(0xFF3D194D),
    onTertiaryContainer = Color(0xFFFAD9FF),
    background = screenBgMid,
    onBackground = if (isDark) ObsidianTextPrimary else TavernTextPrimary,
    surface = cardMid,
    onSurface = if (isDark) ObsidianTextPrimary else TavernTextPrimary,
    surfaceVariant = cardTop,
    onSurfaceVariant = bannerSubText,
    surfaceContainerLowest = screenBgBot,
    surfaceContainerLow = cardBot,
    surfaceContainer = cardMid,
    surfaceContainerHigh = cardTop,
    surfaceContainerHighest = iconSlotTop,
    outline = cardBorder,
    outlineVariant = cardBorder.copy(alpha = 0.55f),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF3B0606),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ChiefTimerTheme(
    themeKey: String = ChiefThemeMode.CLASH.key,
    obsidianDark: Boolean = false,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolvedMode = when {
        themeKey != ChiefThemeMode.CLASH.key -> ChiefThemeMode.fromKey(themeKey)
        obsidianDark -> ChiefThemeMode.DARK
        else -> ChiefThemeMode.CLASH
    }
    val palette = Chief3DPalette.resolve(context, resolvedMode)
    val scheme = palette.toColorScheme()
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalChiefPalette provides palette,
            LocalTavernDark provides palette.isDark,
        ) {
            content()
        }
    }
}
