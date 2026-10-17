package io.github.probably_oxy.drift.ui.cockpit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probably_oxy.drift.data.CustomSound
import io.github.probably_oxy.drift.data.SettingsStore
import io.github.probably_oxy.drift.data.ThemeAuto
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.style.TextAlign
import io.github.probably_oxy.drift.ui.AboutContent
import io.github.probably_oxy.drift.ui.CreditsContent
import io.github.probably_oxy.drift.ui.theme.JetBrainsMono
import io.github.probably_oxy.drift.ui.theme.LocalDriftColors
import io.github.probably_oxy.drift.ui.theme.ShareTechMono
import io.github.probably_oxy.drift.ui.theme.ThemePreset

private enum class MenuView(val title: String) {
    ROOT("MENU"), SOUNDS("MY SOUNDS"), ABOUT("ABOUT"), CREDITS("CREDITS"), SETTINGS("SETTINGS")
}

/**
 * Left slide-in menu drawer (Phase 4f). Overlays the whole screen with a dimming
 * scrim; the panel is ~82% wide. THEME re-tints the app live; ABOUT/CREDITS/SETTINGS
 * push simple sub-views. MY SOUNDS manages the user's imported sounds.
 */
@Composable
fun MenuDrawer(
    open: Boolean,
    currentPreset: ThemePreset,
    shownPreset: ThemePreset,
    settings: SettingsStore,
    onSelectPreset: (ThemePreset) -> Unit,
    onClose: () -> Unit,
    customSounds: List<CustomSound>,
    onImport: () -> Unit,
    onTrim: (CustomSound, Float) -> Unit,
    onRename: (CustomSound) -> Unit,
    onDelete: (CustomSound) -> Unit,
    onSuggest: () -> Unit,
) {
    val colors = LocalDriftColors.current
    var view by remember { mutableStateOf(MenuView.ROOT) }
    // Reset to the root list each time the drawer is dismissed.
    LaunchedEffect(open) { if (!open) view = MenuView.ROOT }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xB8020603))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(tween(360)) { -it },
            exit = slideOutHorizontally(tween(300)) { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .background(colors.bg)
                    .drawBehind {
                        val x = size.width
                        drawLine(colors.greenLine, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    .padding(horizontal = 22.dp)
                    .padding(top = 22.dp, bottom = 16.dp),
            ) {
                DrawerHeader(
                    view = view,
                    onBack = { view = MenuView.ROOT },
                    onClose = onClose,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (view) {
                        MenuView.ROOT -> RootMenu(
                            currentPreset = currentPreset,
                            shownPreset = shownPreset,
                            settings = settings,
                            onSelectPreset = onSelectPreset,
                            onOpen = { view = it },
                        )
                        MenuView.SOUNDS -> SoundsView(
                            sounds = customSounds,
                            onImport = onImport,
                            onTrim = onTrim,
                            onRename = onRename,
                            onDelete = onDelete,
                            onSuggest = onSuggest,
                        )
                        MenuView.ABOUT -> AboutContent()
                        MenuView.CREDITS -> CreditsContent()
                        MenuView.SETTINGS -> SettingsView(settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(view: MenuView, onBack: () -> Unit, onClose: () -> Unit) {
    val colors = LocalDriftColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = view.title,
            color = colors.greenDim,
            fontFamily = ShareTechMono,
            fontSize = 15.sp,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.weight(1f))
        if (view == MenuView.ROOT) {
            OutlineButton("✕ CLOSE", onClose)
        } else {
            OutlineButton("‹ BACK", onBack)
        }
    }
}

@Composable
private fun RootMenu(
    currentPreset: ThemePreset,
    shownPreset: ThemePreset,
    settings: SettingsStore,
    onSelectPreset: (ThemePreset) -> Unit,
    onOpen: (MenuView) -> Unit,
) {
    val colors = LocalDriftColors.current
    val auto = settings.themeAuto != ThemeAuto.OFF
    Text("THEME", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 4.sp)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePreset.entries.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    // With AUTO on, the highlight follows what's on screen; a tap
                    // overrides AUTO until its next switch (see pickTheme).
                    ThemeChip(
                        preset = preset,
                        selected = preset == shownPreset,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        if (auto) "▸ AUTO: tap a theme to override until the next switch." else "▸ recolors the entire interface.",
        color = colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
    )

    Spacer(Modifier.height(18.dp))
    AutoThemeSection(
        settings = settings,
        onEnable = {
            // The night theme must be a dark one.
            if (currentPreset.colors.isLight) onSelectPreset(ThemePreset.PHOSPHOR)
        },
    )

    Spacer(Modifier.height(24.dp))
    MenuItem("MY SOUNDS") { onOpen(MenuView.SOUNDS) }
    MenuDivider()
    MenuItem("ABOUT") { onOpen(MenuView.ABOUT) }
    MenuDivider()
    MenuItem("CREDITS") { onOpen(MenuView.CREDITS) }
    MenuDivider()
    MenuItem("SETTINGS") { onOpen(MenuView.SETTINGS) }

    Spacer(Modifier.height(28.dp))
    Text("DRIFT // AUDIO", color = colors.greenFaint, fontFamily = ShareTechMono, fontSize = 11.sp, letterSpacing = 5.sp)
    Spacer(Modifier.height(4.dp))
    Text("offline · no tracking · © 2026", color = colors.greenFaint, fontFamily = JetBrainsMono, fontSize = 9.sp)
}

@Composable
private fun ThemeChip(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.greenBright.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (selected) colors.greenBright else colors.cardLine, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Outlined so a pale swatch (DAYLIGHT) stays visible on a light drawer.
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(preset.swatch)
                .border(1.dp, colors.cardLine, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            preset.label,
            color = if (selected) colors.greenBright else colors.greenDim,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * AUTO light/dark: OFF / SYSTEM / SCHEDULE, plus the day and night start times
 * when on a schedule. [onEnable] runs when switching from OFF to an auto mode.
 */
@Composable
private fun AutoThemeSection(settings: SettingsStore, onEnable: () -> Unit) {
    val colors = LocalDriftColors.current
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = day start, false = night start
    Text("AUTO LIGHT / DARK", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 4.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeAuto.entries.forEach { mode ->
            val selected = settings.themeAuto == mode
            Text(
                text = mode.name,
                color = if (selected) colors.greenBright else colors.greenDim,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) colors.greenBright.copy(alpha = 0.08f) else Color.Transparent)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.greenBright else colors.cardLine, RoundedCornerShape(6.dp))
                    .clickable {
                        if (settings.themeAuto == ThemeAuto.OFF && mode != ThemeAuto.OFF) onEnable()
                        settings.updateThemeAuto(mode)
                    }
                    .padding(vertical = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        when (settings.themeAuto) {
            ThemeAuto.OFF -> "▸ the theme above is always used."
            ThemeAuto.SYSTEM -> "▸ follows the Android dark mode setting."
            ThemeAuto.SCHEDULE -> "▸ DAYLIGHT between the times below."
        },
        color = colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
    )
    if (settings.themeAuto == ThemeAuto.SCHEDULE) {
        Spacer(Modifier.height(6.dp))
        TimeRow("DAY FROM", settings.dayStartMin) { editing = true }
        MenuDivider()
        TimeRow("NIGHT FROM", settings.nightStartMin) { editing = false }
    }

    editing?.let { day ->
        TimePickDialog(
            initialMin = if (day) settings.dayStartMin else settings.nightStartMin,
            onConfirm = { min ->
                if (day) settings.updateDayStart(min) else settings.updateNightStart(min)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeRow(label: String, minuteOfDay: Int, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.greenMid, fontFamily = JetBrainsMono, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.weight(1f))
        Text(
            "%02d:%02d ▸".format(minuteOfDay / 60, minuteOfDay % 60),
            color = colors.greenBright,
            fontFamily = JetBrainsMono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initialMin: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDriftColors.current
    val state = rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("SET", color = colors.greenBright) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.greenFaint) } },
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Text(
        text = "▸ $label",
        color = colors.greenMid,
        fontFamily = JetBrainsMono,
        fontSize = 14.sp,
        letterSpacing = 2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDriftColors.current.cardLine))
}

/**
 * The user's imported sounds: import, rename, trim, delete — plus the route to
 * suggest a good find for the built-in catalogue (imports themselves carry no
 * license info, so they can never be shipped directly).
 */
@Composable
private fun SoundsView(
    sounds: List<CustomSound>,
    onImport: () -> Unit,
    onTrim: (CustomSound, Float) -> Unit,
    onRename: (CustomSound) -> Unit,
    onDelete: (CustomSound) -> Unit,
    onSuggest: () -> Unit,
) {
    val colors = LocalDriftColors.current
    Text(
        "Add audio from your phone. Files are copied into Drift and never leave this device.",
        color = colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
    Spacer(Modifier.height(14.dp))
    WideButton("+ IMPORT SOUND", onImport)
    Spacer(Modifier.height(10.dp))
    if (sounds.isEmpty()) {
        Text("No imported sounds yet.", color = colors.greenFaint, fontFamily = JetBrainsMono, fontSize = 11.sp)
    }
    sounds.forEach { sound ->
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sound.name, color = colors.greenMid, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "${sound.segments.size} SEG" + if (sound.segments.any { it.startMs > 0L }) " · AUTO-SPLIT" else "",
                color = colors.greenFaint,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
            TrimRow(trimDb = sound.trimDb, enabled = true, onTrim = { onTrim(sound, it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("RENAME", onClick = { onRename(sound) })
                OutlineButton("✕ DELETE", onClick = { onDelete(sound) }, danger = true)
            }
        }
        MenuDivider()
    }

    Spacer(Modifier.height(24.dp))
    Text("FOUND A GREAT ONE?", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 4.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "If it's CC0, public domain or CC BY, suggest it for Drift's built-in sounds. Opens GitHub in your browser.",
        color = colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )
    Spacer(Modifier.height(10.dp))
    WideButton("SUGGEST A SOUND ↗", onSuggest)
}

@Composable
private fun WideButton(label: String, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, colors.greenLine, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.greenBright, fontFamily = JetBrainsMono, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SettingsView(settings: SettingsStore) {
    val colors = LocalDriftColors.current
    Text("ANIMATION", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 4.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Each effect can be switched off independently.",
        color = colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
    )
    Spacer(Modifier.height(14.dp))
    ToggleRow("SCANLINES", "CRT scanline overlay + slow sweep.", settings.scanlines, settings::updateScanlines)
    MenuDivider()
    ToggleRow("SPINNER", "Animated status / layer activity spinner.", settings.spinner, settings::updateSpinner)
    MenuDivider()
    ToggleRow("VU FLICKER", "Blinking top segment of the volume meters.", settings.vuFlicker, settings::updateVuFlicker)
    MenuDivider()
    ToggleRow("GLOW PULSE", "Breathing glow on active layer cards.", settings.glowPulse, settings::updateGlowPulse)
    MenuDivider()
    ToggleRow("BOOT ANIMATION", "Panels & cards slide in on launch.", settings.entrance, settings::updateEntrance)
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalDriftColors.current
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.greenMid, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Text(desc, color = colors.greenFaint, fontFamily = JetBrainsMono, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.bg,
                checkedTrackColor = colors.greenBright,
                checkedBorderColor = colors.greenBright,
                uncheckedThumbColor = colors.greenDim,
                uncheckedTrackColor = colors.cardBg,
                uncheckedBorderColor = colors.cardLine,
            ),
        )
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val colors = LocalDriftColors.current
    Text(
        text = label,
        color = if (danger) colors.red else colors.greenMid,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (danger) colors.redLine else colors.greenFaint, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
