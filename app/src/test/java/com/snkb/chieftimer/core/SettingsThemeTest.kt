package com.snkb.chieftimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsThemeTest {

    @Test
    fun legacyJsonInheritsWidgetThemeForBothAppAndWidgets() {
        val global = GlobalSettings.decode("""{"widgetTheme":"black"}""")
        assertEquals("black", global.effectiveAppTheme)
        assertEquals("black", global.effectiveWidgetTheme)
        assertTrue(global.widgetFollowsAppTheme)

        val settings = AppSettings.decode("""{"widgetTheme":"dark"}""")
        assertEquals("dark", settings.effectiveAppTheme)
        assertEquals("dark", settings.effectiveWidgetTheme)
        assertTrue(settings.widgetFollowsAppTheme)
    }

    @Test
    fun linkedModeMakesWidgetsFollowAppTheme() {
        val settings = AppSettings(
            appTheme = "material_you",
            widgetTheme = "clash",
            widgetFollowsAppTheme = true,
        )
        assertEquals("material_you", settings.effectiveAppTheme)
        assertEquals("material_you", settings.effectiveWidgetTheme)
    }

    @Test
    fun unlinkedModeKeepsSeparateThemesAcrossSerialization() {
        val initial = GlobalSettings(
            widgetTheme = "black",
            appTheme = "clash",
            widgetFollowsAppTheme = false,
        )
        assertEquals("clash", initial.effectiveAppTheme)
        assertEquals("black", initial.effectiveWidgetTheme)

        val decoded = GlobalSettings.decode(initial.encode())
        assertFalse(decoded.widgetFollowsAppTheme)
        assertEquals("clash", decoded.effectiveAppTheme)
        assertEquals("black", decoded.effectiveWidgetTheme)
    }

    @Test
    fun floatingTabBarDefaultsToTrueAndRoundTripsFalse() {
        assertTrue(GlobalSettings().floatingTabBar)
        assertTrue(AppSettings().floatingTabBar)
        val decoded = GlobalSettings.decode(GlobalSettings(floatingTabBar = false).encode())
        assertFalse(decoded.floatingTabBar)
        val settings = AppSettings.decode(AppSettings(floatingTabBar = false).encode())
        assertFalse(settings.floatingTabBar)
    }
}
