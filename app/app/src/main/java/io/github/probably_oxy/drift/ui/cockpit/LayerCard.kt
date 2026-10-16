package io.github.probably_oxy.drift.ui.cockpit

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probably_oxy.drift.data.Catalogue
import io.github.probably_oxy.drift.data.CustomSound
import io.github.probably_oxy.drift.data.LocalDriftAnim
import io.github.probably_oxy.drift.data.License
import io.github.probably_oxy.drift.data.Sound
import io.github.probably_oxy.drift.data.VariantKind
import io.github.probably_oxy.drift.ui.theme.DriftTheme
import io.github.probably_oxy.drift.ui.theme.JetBrainsMono
import io.github.probably_oxy.drift.ui.theme.LocalDriftColors
import java.util.Locale
import kotlin.math.floor

/**
 * Telemetry layer card (Phase 4c front + 4d info readout). Constant 138dp height
 * (more under an enlarged system font) across off / active / info — the shell
 * never resizes.
 *
 * Controlled: the parent owns which card (if any) shows its readout. [showInfo]
 * drives the front↔readout swap; [onInfo] is the toggle (the (i) opens it; tapping
 * `‹ BACK` or the readout body calls it again to return).
 *
 * @param channel  1-based catalogue index, e.g. 3 → "CH 03".
 * @param active   on AND not globally muted — drives the energised visual state.
 */
@Composable
fun LayerCard(
    sound: Sound,
    channel: Int,
    on: Boolean,
    volume: Float,
    active: Boolean,
    showInfo: Boolean,
    variantId: String?,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    onVariant: (String) -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    /** Non-null for playable sounds: shows the trim stepper on the readout. */
    trimDb: Float? = null,
    onTrim: (Float) -> Unit = {},
    /** Non-null only for user imports: "suggest this for the catalogue" link. */
    onSuggest: (() -> Unit)? = null,
) {
    val colors = LocalDriftColors.current
    val pulseEnabled = LocalDriftAnim.current.glowPulse
    val shape = RoundedCornerShape(7.dp)

    // Active glow pulse (3.2s); a steady glow when the pulse is disabled.
    val pulse = rememberInfiniteTransition(label = "cardPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val glowColor = colors.greenGlow.copy(alpha = if (pulseEnabled) pulseAlpha else 0.4f)

    val borderColor = if (active) colors.greenBright else colors.cardLine

    // Swap animation: front fades (140ms); readout wipes in left→right (stepped, 400ms).
    val frontAlpha by animateFloatAsState(
        targetValue = if (showInfo) 0f else 1f,
        animationSpec = tween(140, easing = LinearEasing),
        label = "frontAlpha",
    )
    val reveal by animateFloatAsState(
        targetValue = if (showInfo) 1f else 0f,
        animationSpec = tween(400, easing = SteppedReveal),
        label = "readoutReveal",
    )

    // Fixed height, but grown with the system font scale so the readout's text
    // and trim row still fit when the user has enlarged fonts (issue #14).
    val fontGrowth = (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT + CARD_FONT_GROWTH * fontGrowth)
            .then(if (active) Modifier.glow(glowColor) else Modifier)
            .clip(shape)
            .background(if (active) colors.cardBgOn else colors.cardBg)
            .border(if (active) 1.5.dp else 1.dp, borderColor, shape),
    ) {
        // ── Front face ──────────────────────────────────────────
        if (frontAlpha > 0f) {
            CardFront(
                sound = sound,
                volume = volume,
                active = active,
                enabled = !showInfo,
                variantId = variantId,
                onToggle = onToggle,
                onVolume = onVolume,
                onVariant = onVariant,
                onInfo = onInfo,
                modifier = Modifier.fillMaxSize().alpha(frontAlpha),
            )
        }
        // ── Info readout ────────────────────────────────────────
        if (reveal > 0f) {
            CardReadout(
                sound = sound,
                channel = channel,
                volume = volume,
                active = showInfo,
                enabled = showInfo,
                onBack = onInfo,
                trimDb = trimDb,
                onTrim = onTrim,
                onSuggest = onSuggest,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() } },
            )
        }
    }
}

@Composable
private fun CardFront(
    sound: Sound,
    volume: Float,
    active: Boolean,
    enabled: Boolean,
    variantId: String?,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    onVariant: (String) -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    val frequencies = remember(sound) { sound.variants.filter { it.kind == VariantKind.FREQUENCY } }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onToggle,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LayerIcon(soundId = sound.id, active = active)
            Spacer(Modifier.width(11.dp))
            Text(
                text = sound.name,
                color = if (active) colors.greenBright else colors.greenMid,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            InfoButton(onClick = onInfo)
        }

        VuMeter(value = volume, active = active, onValueChange = onVolume)

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsciiSpinner(active = active, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "VOL ${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
                color = if (active) colors.greenBright else colors.greenFaint,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
            if (frequencies.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    frequencies.forEach { freq ->
                        FrequencyChip(
                            label = freq.label,
                            selected = freq.id == variantId,
                            enabled = enabled,
                            onClick = { onVariant(freq.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardReadout(
    sound: Sound,
    channel: Int,
    volume: Float,
    active: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    trimDb: Float?,
    onTrim: (Float) -> Unit,
    onSuggest: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    val ch = channel.toString().padStart(2, '0')
    val pct = (volume.coerceIn(0f, 1f) * 100).toInt()
    // Cards with a trim row drop the blank spacer line so it fits the fixed card height.
    val body =
        "SRC   ${sourceOf(sound)}\n" +
            "CH $ch · ${sound.type.name} · VOL $pct%\n" +
            (if (trimDb == null) "\n" else "") +
            sound.description
    val typed = rememberTypewriter(body, active, cps = 150)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onBack,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "▸ ${sound.name.uppercase()}",
                color = colors.greenBright,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
            BackButton(onClick = onBack)
        }
        // The text takes whatever height is left, so the trim row below always
        // stays on the card; text that doesn't fit is cut with "…" rather than
        // pushing the controls off the bottom edge.
        Row(Modifier.weight(1f).clipToBounds()) {
            Text(
                text = typed.text,
                color = colors.textDim,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                lineHeight = 15.5.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Cursor(show = !typed.done)
        }
        if (trimDb != null) {
            TrimRow(trimDb = trimDb, enabled = enabled, onTrim = onTrim, onSuggest = onSuggest)
        }
    }
}

/**
 * Per-sound loudness trim: − / value / + in [CustomSound.TRIM_STEP_DB] steps;
 * tapping the value resets it to 0 dB. Stored as metadata and applied at
 * playback, so it can be changed any time. Each button consumes its own tap, so
 * the rest of the readout still flips the card back.
 */
@Composable
fun TrimRow(
    trimDb: Float,
    enabled: Boolean,
    onTrim: (Float) -> Unit,
    onSuggest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDriftColors.current
    // Equal-height row (at least TRIM_ROW_HEIGHT, taller under a large font
    // scale); every bordered box fills it with its label centred, so all box
    // edges line up exactly regardless of each label's font size.
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = TRIM_ROW_HEIGHT).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TRIM", color = colors.greenFaint, fontFamily = JetBrainsMono, fontSize = 9.5.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(8.dp))
        StepButton("−", enabled && trimDb > CustomSound.TRIM_MIN_DB) { onTrim(trimDb - CustomSound.TRIM_STEP_DB) }
        Text(
            text = formatDb(trimDb),
            color = colors.greenBright,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(62.dp)
                .clickable(enabled = enabled && trimDb != 0f) { onTrim(0f) },
        )
        StepButton("+", enabled && trimDb < CustomSound.TRIM_MAX_DB) { onTrim(trimDb + CustomSound.TRIM_STEP_DB) }
        if (onSuggest != null) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, colors.greenFaint, RoundedCornerShape(3.dp))
                    .clickable(enabled = enabled, onClick = onSuggest)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("SUGGEST ↗", color = colors.greenMid, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

private val TRIM_ROW_HEIGHT = 22.dp

private val CARD_HEIGHT = 138.dp

/** Extra card height per +100% font scale (≈ the readout's font-sized content). */
private val CARD_FONT_GROWTH = 90.dp

/** At a limit the step button drops its border AND dims — never colour alone. */
@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Box(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.border(1.dp, colors.greenLine, RoundedCornerShape(3.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.greenBright else colors.greenFaint,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
        )
    }
}

private fun formatDb(db: Float): String =
    if (db == 0f) "0.0 dB" else String.format(Locale.US, "%+.1f dB", db)

@Composable
private fun BackButton(onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Text(
        text = "‹ BACK",
        color = colors.greenMid,
        fontFamily = JetBrainsMono,
        fontSize = 9.5.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, colors.greenFaint, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A small tappable pill for one FREQUENCY variant (e.g. CONT / SPARSE). */
@Composable
private fun FrequencyChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDriftColors.current
    Text(
        text = label,
        color = if (selected) colors.bg else colors.greenFaint,
        fontFamily = JetBrainsMono,
        fontSize = 8.5.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) colors.greenBright else Color.Transparent)
            .border(1.dp, if (selected) colors.greenBright else colors.greenFaint, RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** SRC line = the credited author; SYN/original sounds have none, so label them as synth. */
private fun sourceOf(sound: Sound): String =
    sound.license.author ?: when (sound.license.license) {
        License.ORIGINAL -> "Synth · original"
        License.USER -> "Your import"
        else -> "—"
    }

/** Stepped (steps-16) easing for the readout's left→right clip wipe. */
private val SteppedReveal = Easing { f -> floor(f * 16f) / 16f }

@Preview(showBackground = true, backgroundColor = 0xFF060A07)
@Composable
private fun LayerCardPreview() {
    DriftTheme {
        Column(
            Modifier
                .background(LocalDriftColors.current.bg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LayerCard(
                sound = Catalogue.byId("interstellarplasma")!!, channel = 5,
                on = true, volume = 0.4f, active = true, showInfo = false, variantId = null,
                onToggle = {}, onVolume = {}, onVariant = {}, onInfo = {},
            )
            LayerCard(
                sound = Catalogue.byId("thunder")!!, channel = 1,
                on = false, volume = 0.6f, active = false, showInfo = true, variantId = "continuous",
                onToggle = {}, onVolume = {}, onVariant = {}, onInfo = {},
            )
        }
    }
}
