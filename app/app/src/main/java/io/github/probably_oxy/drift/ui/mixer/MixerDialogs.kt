package io.github.probably_oxy.drift.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.probably_oxy.drift.ui.theme.JetBrainsMono
import io.github.probably_oxy.drift.ui.theme.LocalDriftColors
import io.github.probably_oxy.drift.ui.theme.ShareTechMono

// The mixer screen's dialogs: save mix / rename, import, delete sound.

/** Single text-field dialog, shared by "save mix" and "rename sound". */
@Composable
internal fun NameDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    val colors = LocalDriftColors.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        title = { Text(title, color = colors.greenBright, fontFamily = ShareTechMono, fontSize = 14.sp, letterSpacing = 2.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(placeholder, color = colors.greenFaint) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel, color = colors.greenBright) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.greenFaint) } },
    )
}

/**
 * Name the picked file(s) and choose whether long files are auto-split into
 * segments (crossfaded between, hiding the loop seam) or kept as one long loop.
 */
@Composable
internal fun ImportDialog(
    defaultName: String,
    fileCount: Int,
    onConfirm: (name: String, autoSplit: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDriftColors.current
    var name by remember { mutableStateOf(defaultName) }
    var autoSplit by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        title = { Text("IMPORT SOUND", color = colors.greenBright, fontFamily = ShareTechMono, fontSize = 14.sp, letterSpacing = 2.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (fileCount > 1) {
                    Text(
                        "$fileCount files → one sound, crossfading between them.",
                        color = colors.greenFaint,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Sound name", color = colors.greenFaint) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AUTO-SPLIT", color = colors.greenMid, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (autoSplit) "Files over 90s are cut into ~45s parts that crossfade, hiding the loop point."
                            else "Each file plays as one long loop.",
                            color = colors.greenFaint,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoSplit,
                        onCheckedChange = { autoSplit = it },
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
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, autoSplit) }) { Text("IMPORT", color = colors.greenBright) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.greenFaint) } },
    )
}

/** Confirms removing an imported sound and its copied audio. */
@Composable
internal fun DeleteSoundDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDriftColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        title = { Text("DELETE SOUND", color = colors.red, fontFamily = ShareTechMono, fontSize = 14.sp, letterSpacing = 2.sp) },
        text = {
            Text(
                "Remove \"$name\" and its copied audio from Drift? Presets using it will skip it.",
                color = colors.textDim,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("DELETE", color = colors.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.greenFaint) } },
    )
}
