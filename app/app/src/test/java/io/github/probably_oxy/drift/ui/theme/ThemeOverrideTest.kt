package io.github.probably_oxy.drift.ui.theme

import io.github.probably_oxy.drift.data.ThemeAuto
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeOverrideTest {

    private fun resolve(now: Int, flipped: Boolean, auto: ThemeAuto = ThemeAuto.SCHEDULE) =
        resolveTheme(ThemePreset.AMBER, auto, systemDark = false, now, 7 * 60, 21 * 60, flipped)

    @Test
    fun flipByDay_showsTheNightTheme() {
        assertEquals(ThemePreset.AMBER, resolve(now = 12 * 60, flipped = true))
    }

    @Test
    fun flipByNight_showsDaylight() {
        assertEquals(ThemePreset.DAYLIGHT, resolve(now = 23 * 60, flipped = true))
    }

    @Test
    fun flipWithAutoOff_isIgnored() {
        assertEquals(ThemePreset.AMBER, resolve(now = 12 * 60, flipped = true, auto = ThemeAuto.OFF))
    }

    @Test
    fun nextSwitch_isTheNearerBoundary() {
        assertEquals(9 * 60, minutesUntilNextSwitch(12 * 60, 7 * 60, 21 * 60)) // → night at 21:00
        assertEquals(8 * 60, minutesUntilNextSwitch(23 * 60, 7 * 60, 21 * 60)) // → day at 07:00, past midnight
    }

    @Test
    fun nextSwitch_exactlyOnABoundary_isTheOtherOne() {
        assertEquals(14 * 60, minutesUntilNextSwitch(7 * 60, 7 * 60, 21 * 60))
    }
}
