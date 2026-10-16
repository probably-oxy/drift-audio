package io.github.probably_oxy.drift.ui.mixer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probably_oxy.drift.data.OutputMode
import io.github.probably_oxy.drift.data.Preset
import io.github.probably_oxy.drift.ui.cockpit.AsciiSpinner
import io.github.probably_oxy.drift.ui.cockpit.IconMenu
import io.github.probably_oxy.drift.ui.cockpit.IconVolume
import io.github.probably_oxy.drift.ui.cockpit.IconVolumeMute
import io.github.probably_oxy.drift.ui.cockpit.InfoButton
import io.github.probably_oxy.drift.ui.cockpit.InfoStrip
import io.github.probably_oxy.drift.ui.cockpit.OutputIcon
import io.github.probably_oxy.drift.ui.theme.JetBrainsMono
import io.github.probably_oxy.drift.ui.theme.LocalDriftColors
import io.github.probably_oxy.drift.ui.theme.ShareTechMono

// The mixer screen's fixed panels (header, status, sleep timer, output) and
// the presets section. Stateless: MixerScreen passes values and callbacks in.

@Composable
internal fun Header(onMenu: () -> Unit) {
    val colors = LocalDriftColors.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMenu,
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconMenu(colors.greenDim, size = 26.dp)
        }
        Text(
            text = "DRIFT // AUDIO",
            color = colors.greenDim,
            fontFamily = ShareTechMono,
            fontSize = 15.sp,
            letterSpacing = 7.sp,
        )
    }
}

/** The SYS.STATUS multi-function panel: spinner + layer/state readout + MUTE / STOP ALL. */
@Composable
internal fun StatusPanel(
    layerCount: Int,
    playing: Boolean,
    muted: Boolean,
    enabled: Boolean,
    onMute: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    val state = when {
        layerCount == 0 -> "IDLE"
        muted -> "MUTED" // a muted mix still "plays" (spinner runs); flag it here
        playing -> "PLAYING"
        else -> "IDLE"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.cardLine, RoundedCornerShape(9.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsciiSpinner(active = playing, boxed = true, boxSize = 27.dp)
            Spacer(Modifier.width(10.dp))
            Text("SYS.STATUS", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 12.sp, letterSpacing = 5.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = "LAYERS: $layerCount / $state",
                color = if (playing) colors.greenBright else colors.greenDim,
                fontFamily = JetBrainsMono,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.cardLine))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MuteButton(muted = muted, enabled = enabled, onClick = onMute, modifier = Modifier.weight(1f))
            StopButton(enabled = enabled, onClick = onStop, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDriftColors.current
    val tint = if (enabled) colors.greenBright else colors.greenDim
    val border = when {
        !enabled -> colors.cardLine
        muted -> colors.greenBright
        else -> colors.greenLine
    }
    val bg = if (muted && enabled) colors.greenBright.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muted) IconVolumeMute(tint, size = 18.dp) else IconVolume(tint, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(if (muted) "UNMUTE" else "MUTE", color = tint, fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun StopButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDriftColors.current
    val tint = if (enabled) colors.red else colors.red.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) colors.redBg else Color.Transparent)
            .border(1.dp, if (enabled) colors.redLine else colors.cardLine, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("■ STOP ALL", color = tint, fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)
    }
}

/** Compact sleep-timer panel: moon + SLEEP, idle chips or running countdown, and an info strip. */
@Composable
internal fun SleepPanel(
    remainingMs: Long,
    enabled: Boolean,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    val running = remainingMs > 0
    var infoOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.cardLine, RoundedCornerShape(9.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SLEEP", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 13.sp, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            if (running) {
                SleepCountdown(remainingMs)
                Spacer(Modifier.width(10.dp))
                Chip("✕", enabled = true, onClick = onCancel)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60, 90).forEach { m ->
                        Chip("$m", enabled = enabled, onClick = { onPick(m) })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            InfoButton(onClick = { infoOpen = !infoOpen })
        }
        InfoStrip(
            open = infoOpen,
            text = "▸ SLEEP TIMER · fades the mix out and stops playback when the countdown reaches zero.",
        )
    }
}

@Composable
private fun SleepCountdown(ms: Long) {
    val colors = LocalDriftColors.current
    val transition = rememberInfiniteTransition(label = "colon")
    val phase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "colonBlink",
    )
    val showColon = phase < 0.5f
    Text(
        text = fmtDuration(ms, showColon),
        color = colors.greenBright,
        fontFamily = JetBrainsMono,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun Chip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.cardLine, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) colors.greenDim else colors.greenFaint, fontFamily = JetBrainsMono, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

/** Compact output panel: OUTPUT + three sink icon buttons + an info strip describing the sink. */
@Composable
internal fun OutputPanel(
    selected: OutputMode,
    onSelect: (OutputMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    var infoOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.cardLine, RoundedCornerShape(9.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OUTPUT", color = colors.greenDim, fontFamily = ShareTechMono, fontSize = 13.sp, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputMode.entries.forEach { mode ->
                    OutputButton(mode = mode, selected = mode == selected, onClick = { onSelect(mode) })
                }
            }
            Spacer(Modifier.width(8.dp))
            InfoButton(onClick = { infoOpen = !infoOpen })
        }
        InfoStrip(open = infoOpen, text = outputInfo(selected))
    }
}

@Composable
private fun OutputButton(mode: OutputMode, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    val tint = if (selected) colors.greenBright else colors.greenDim
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.greenBright.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (selected) colors.greenBright else colors.cardLine, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OutputIcon(mode = mode, tint = tint, size = 20.dp)
    }
}

private fun outputInfo(mode: OutputMode): String = when (mode) {
    OutputMode.SPEAKER -> "▸ SPEAKER · phone loudspeaker — mono, full-range playback."
    OutputMode.STEREO -> "▸ STEREO · external / cast stereo — wide L-R image."
    OutputMode.PHONES -> "▸ PHONES · headphones — spatialised binaural mix."
}

private fun fmtDuration(ms: Long, colon: Boolean = true): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val sep = if (colon) ":" else " "
    return if (h > 0) "%d$sep%02d$sep%02d".format(h, m, s) else "%02d$sep%02d".format(m, s)
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDriftColors.current.greenDim,
        fontFamily = ShareTechMono,
        fontSize = 12.sp,
        letterSpacing = 5.sp,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun PresetsSection(
    builtIn: List<Preset>,
    userPresets: List<Preset>,
    enabled: Boolean,
    canSave: Boolean,
    onApply: (Preset) -> Unit,
    onSaveClick: () -> Unit,
    onDelete: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        builtIn.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    PresetChip(
                        label = preset.name,
                        enabled = enabled,
                        onClick = { onApply(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(4.dp))
        PresetChip(
            label = "+ SAVE CURRENT MIX",
            enabled = canSave,
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            accent = true,
        )
        userPresets.forEach { preset ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PresetChip(
                    label = preset.name,
                    enabled = enabled,
                    onClick = { onApply(preset) },
                    modifier = Modifier.weight(1f),
                )
                PresetChip(
                    label = "×",
                    enabled = true,
                    onClick = { onDelete(preset) },
                    danger = true,
                )
            }
        }
    }
}

@Composable
internal fun PresetChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false,
) {
    val colors = LocalDriftColors.current
    val borderColor = when {
        !enabled -> colors.cardLine
        danger -> colors.redLine
        accent -> colors.greenLine
        else -> colors.cardLine
    }
    val textColor = when {
        !enabled -> colors.greenFaint
        danger -> colors.red
        accent -> colors.greenBright
        else -> colors.greenDim
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(colors.cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

