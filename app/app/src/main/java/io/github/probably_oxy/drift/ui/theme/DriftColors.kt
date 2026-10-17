package io.github.probably_oxy.drift.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.probably_oxy.drift.data.ThemeAuto
import kotlin.math.roundToInt

/**
 * The redesigned "starship MFD" palette (Claude Design handoff, 2026-06).
 *
 * Unlike the legacy top-level vals in [Color.kt], colours here are bundled into
 * one [DriftColors] holder and handed down the tree via [LocalDriftColors]. That
 * is the whole point of the "structure now, wire later" theme work: a composable
 * reads `LocalDriftColors.current.greenBright` instead of a hard-coded val, so
 * when we later let the user pick Amber/Ice/Mono we just provide a different
 * instance at the top and the entire UI re-tints with zero edits downstream.
 *
 * Hex values are the design tokens reproduced precisely. rgba() tokens are packed
 * as 0xAARRGGBB (alpha first), e.g. rgba(47,224,74,0.55) -> 0x8C2FE04A.
 */
@Immutable
data class DriftColors(
    // ── surfaces ──────────────────────────────────────────────
    val bg: Color,            // app background
    val cardBg: Color,        // layer card / panel fill (off)
    val cardBgOn: Color,      // layer card fill (active)
    // ── lines / borders ───────────────────────────────────────
    val cardLine: Color,      // default 1px borders / dividers
    val greenLine: Color,     // active / emphasis borders, info-button outline
    // ── greens (primary hue + its ramp) ───────────────────────
    val greenBright: Color,   // primary phosphor: active text, lit VU, glow source
    val greenMid: Color,      // layer title (off), secondary labels
    val greenDim: Color,      // section labels, idle controls
    val greenFaint: Color,    // tertiary text, status line, unlit accents
    val greenGlow: Color,     // strong glow (shadow / blur)
    val greenGlowSoft: Color, // soft glow
    // ── amber accent (unused outside the AMBER preset's own primary hue) ──────
    val amber: Color,
    val amberGlow: Color,
    // ── text / meter / danger ─────────────────────────────────
    val textDim: Color,       // body / readout text, status-bar text
    val vuOff: Color,         // unlit VU segment
    val red: Color,           // STOP ALL text / icon
    val redBg: Color,         // STOP ALL fill
    val redLine: Color,       // STOP ALL border
    // ── mode ──────────────────────────────────────────────────
    /** True for light-background palettes: flips Material's scheme and the system-bar icons. */
    val isLight: Boolean = false,
)

/** PHOSPHOR — the default green-on-black theme (the only one wired for now). */
val PhosphorColors = DriftColors(
    bg = Color(0xFF060A07),
    cardBg = Color(0x29162A1A),
    cardBgOn = Color(0x291E4828),
    cardLine = Color(0x38408450),
    greenLine = Color(0x8050B464),
    greenBright = Color(0xFF2FE04A),
    greenMid = Color(0xFF3F9A52),
    greenDim = Color(0xFF357A44),
    greenFaint = Color(0xFF2C5436),
    greenGlow = Color(0x8C2FE04A),
    greenGlowSoft = Color(0x472FE04A),
    amber = Color(0xFFD9A82E),
    amberGlow = Color(0x99D9A82E),
    textDim = Color(0xFF5A8A64),
    vuOff = Color(0xFF173A22),
    red = Color(0xFFE0532F),
    redBg = Color(0x38782216),
    redLine = Color(0x8CBE422A),
)

/**
 * DAYLIGHT — the one light palette, for reading in bright rooms or outdoors. Styled as
 * a lab instrument / e-ink panel (warm paper, dark-green ink) rather than plain white,
 * so it still reads as Drift. The emphasis ramp is inverted versus the dark themes:
 * "bright" means most prominent, which on paper is the darkest, most saturated ink.
 *
 * WCAG contrast against bg / card (checked 2026-10-10): greenBright 5.6/6.1,
 * greenMid 7.0/7.6, greenDim 5.7/6.1, greenFaint 4.2/4.6, textDim 10.6/11.4,
 * red 5.2/5.7. Glows are kept faint — a phosphor halo just reads as a smudge on paper.
 */
val DaylightColors = DriftColors(
    bg = Color(0xFFF2F0E8),
    cardBg = Color(0xFFFAF9F4),
    cardBgOn = Color(0xFFE3EFE2),
    cardLine = Color(0x402D5A37),
    greenLine = Color(0x990F6E28),
    greenBright = Color(0xFF0F6E28),
    greenMid = Color(0xFF2D5A37),
    greenDim = Color(0xFF44664D),
    greenFaint = Color(0xFF5F7865),
    greenGlow = Color(0x330F6E28),
    greenGlowSoft = Color(0x1A0F6E28),
    amber = Color(0xFF8A5C00),
    amberGlow = Color(0x408A5C00),
    textDim = Color(0xFF2A3A2E),
    vuOff = Color(0xFFD3DCD1),
    red = Color(0xFFB23A1E),
    redBg = Color(0x14B23A1E),
    redLine = Color(0x80B23A1E),
    isLight = true,
)

// ── Structural tokens shared by every theme (only the primary hue swaps) ──────
private val SharedBg = Color(0xFF060A07)
private val SharedAmber = Color(0xFFD9A82E)
private val SharedAmberGlow = Color(0x99D9A82E)
private val SharedRed = Color(0xFFE0532F)
private val SharedRedBg = Color(0x38782216)
private val SharedRedLine = Color(0x8CBE422A)

private fun Color.toHsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(toArgb(), it) }

private fun hsv(h: Float, s: Float, v: Float, alpha: Float = 1f): Color =
    Color(android.graphics.Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/**
 * Derive a full [DriftColors] ramp from a single primary hue, keeping the shared
 * dark bg + amber/red structure. Used to generate the Amber / Ice / Mono presets so
 * selecting one in the menu re-tints the whole app. Phosphor stays hand-tuned.
 */
fun driftPaletteFor(primary: Color): DriftColors {
    val c = primary.toHsv()
    val h = c[0]
    val s = c[1]
    return DriftColors(
        bg = SharedBg,
        cardBg = hsv(h, s * 0.55f, 0.17f, alpha = 0.16f),
        cardBgOn = hsv(h, s * 0.6f, 0.30f, alpha = 0.16f),
        cardLine = primary.copy(alpha = 0.22f),
        greenLine = primary.copy(alpha = 0.5f),
        greenBright = primary,
        greenMid = hsv(h, s * 0.8f, 0.62f),
        greenDim = hsv(h, s * 0.85f, 0.48f),
        greenFaint = hsv(h, s * 0.9f, 0.34f),
        greenGlow = primary.copy(alpha = 0.55f),
        greenGlowSoft = primary.copy(alpha = 0.28f),
        amber = SharedAmber,
        amberGlow = SharedAmberGlow,
        textDim = hsv(h, s * 0.5f, 0.58f),
        vuOff = hsv(h, s * 0.9f, 0.24f),
        red = SharedRed,
        redBg = SharedRedBg,
        redLine = SharedRedLine,
    )
}

/**
 * The menu presets. The first four keep the shared dark bg + structure and only swap
 * the "bright/primary" hue: Phosphor #2FE04A · Amber #D9A82E · Ice #5AD0E0 · Mono
 * #C8D4C8. Phosphor is the hand-tuned canonical palette; the others are hue-derived.
 * Daylight is the hand-tuned light palette.
 */
enum class ThemePreset(val label: String, val swatch: Color, val colors: DriftColors) {
    PHOSPHOR("PHOSPHOR", Color(0xFF2FE04A), PhosphorColors),
    AMBER("AMBER", Color(0xFFD9A82E), driftPaletteFor(Color(0xFFD9A82E))),
    ICE("ICE", Color(0xFF5AD0E0), driftPaletteFor(Color(0xFF5AD0E0))),
    MONO("MONO", Color(0xFFC8D4C8), driftPaletteFor(Color(0xFFC8D4C8))),
    DAYLIGHT("DAYLIGHT", Color(0xFFF2F0E8), DaylightColors),
}

/**
 * Whether automatic switching currently says "day" (null when [auto] is OFF).
 * [nowMin], [dayStartMin] and [nightStartMin] are minutes of the day; a day
 * window may wrap past midnight.
 */
fun isDaytime(
    auto: ThemeAuto,
    systemDark: Boolean,
    nowMin: Int,
    dayStartMin: Int,
    nightStartMin: Int,
): Boolean? = when (auto) {
    ThemeAuto.OFF -> null
    ThemeAuto.SYSTEM -> !systemDark
    ThemeAuto.SCHEDULE ->
        if (dayStartMin <= nightStartMin) nowMin in dayStartMin until nightStartMin
        else nowMin >= dayStartMin || nowMin < nightStartMin
}

/** SCHEDULE mode: minutes from [nowMin] to the next day or night start (1–1440). */
fun minutesUntilNextSwitch(nowMin: Int, dayStartMin: Int, nightStartMin: Int): Int =
    listOf(dayStartMin, nightStartMin).minOf { start ->
        val ahead = ((start - nowMin) % 1440 + 1440) % 1440
        if (ahead == 0) 1440 else ahead
    }

/**
 * The theme actually shown. With automatic switching on, daytime gives DAYLIGHT
 * and night gives the user's picked theme ([chosen]) — or PHOSPHOR, should the
 * pick itself be the light one. [flipped]: the user overrode the current phase
 * (a dark theme by day, DAYLIGHT by night) until the next switch.
 */
fun resolveTheme(
    chosen: ThemePreset,
    auto: ThemeAuto,
    systemDark: Boolean,
    nowMin: Int,
    dayStartMin: Int,
    nightStartMin: Int,
    flipped: Boolean = false,
): ThemePreset {
    val isDay = isDaytime(auto, systemDark, nowMin, dayStartMin, nightStartMin) ?: return chosen
    return when {
        isDay != flipped -> ThemePreset.DAYLIGHT
        chosen.colors.isLight -> ThemePreset.PHOSPHOR
        else -> chosen
    }
}

/** Tree-wide handle for the active palette. Defaults to Phosphor. */
val LocalDriftColors = staticCompositionLocalOf { PhosphorColors }
