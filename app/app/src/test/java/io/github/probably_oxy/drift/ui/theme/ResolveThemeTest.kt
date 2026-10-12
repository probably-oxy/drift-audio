package io.github.probably_oxy.drift.ui.theme

import io.github.probably_oxy.drift.data.ThemeAuto
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveThemeTest {

    private fun resolve(
        chosen: ThemePreset = ThemePreset.AMBER,
        auto: ThemeAuto = ThemeAuto.SCHEDULE,
        systemDark: Boolean = true,
        now: Int,
        day: Int = 7 * 60,
        night: Int = 21 * 60,
    ) = resolveTheme(chosen, auto, systemDark, now, day, night)

    @Test
    fun off_alwaysTheChosenTheme() {
        assertEquals(ThemePreset.AMBER, resolve(auto = ThemeAuto.OFF, now = 12 * 60))
        assertEquals(ThemePreset.DAYLIGHT, resolve(chosen = ThemePreset.DAYLIGHT, auto = ThemeAuto.OFF, now = 0))
    }

    @Test
    fun system_followsDarkMode() {
        assertEquals(ThemePreset.DAYLIGHT, resolve(auto = ThemeAuto.SYSTEM, systemDark = false, now = 0))
        assertEquals(ThemePreset.AMBER, resolve(auto = ThemeAuto.SYSTEM, systemDark = true, now = 12 * 60))
    }

    @Test
    fun schedule_dayWindow_startInclusiveEndExclusive() {
        assertEquals(ThemePreset.AMBER, resolve(now = 7 * 60 - 1))
        assertEquals(ThemePreset.DAYLIGHT, resolve(now = 7 * 60))
        assertEquals(ThemePreset.DAYLIGHT, resolve(now = 21 * 60 - 1))
        assertEquals(ThemePreset.AMBER, resolve(now = 21 * 60))
    }

    @Test
    fun schedule_dayWindowWrappingMidnight() {
        // e.g. a night-shift worker: "day" 22:00 → 06:00.
        assertEquals(ThemePreset.DAYLIGHT, resolve(now = 23 * 60, day = 22 * 60, night = 6 * 60))
        assertEquals(ThemePreset.DAYLIGHT, resolve(now = 60, day = 22 * 60, night = 6 * 60))
        assertEquals(ThemePreset.AMBER, resolve(now = 12 * 60, day = 22 * 60, night = 6 * 60))
    }

    @Test
    fun nightWithLightThemePicked_fallsBackToPhosphor() {
        assertEquals(ThemePreset.PHOSPHOR, resolve(chosen = ThemePreset.DAYLIGHT, now = 23 * 60))
    }
}
