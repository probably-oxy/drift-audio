package io.github.probably_oxy.drift.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed colours for the Material [androidx.compose.material3.ColorScheme] the dark
 * themes share (see Theme.kt) — only what built-in Material components fall back
 * to. Everything Drift draws itself reads the active palette from
 * [LocalDriftColors] instead (DriftColors.kt).
 */
val Bg = Color(0xFF0A0F0A)
val Surface = Color(0xFF111A0E)
val Border = Color(0xFF1E3014)
val TextDim = Color(0xFF4DD470)
val TextActive = Color(0xFF00FF41)
val Accent = Color(0xFFC8A030)
val Danger = Color(0xFFC05030)
