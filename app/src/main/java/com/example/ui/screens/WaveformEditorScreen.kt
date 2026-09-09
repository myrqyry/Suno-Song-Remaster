package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.AudioBuffer
import com.example.ui.components.WaveformDisplay
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurple
import com.example.ui.theme.StudioYellow
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveformEditorScreen(
    buffer: AudioBuffer?,
    currentSec: Double,
    durationSec: Double,
    selectionStart: Double?,
    selectionEnd: Double?,
    fadeDuration: Float,
    loopEnabled: Boolean,
    isPlaying: Boolean,
    canUndo: Boolean,
    onSeek: (Double) -> Unit,
    onSelectRange: (Double, Double) -> Unit,
    onClearSelection: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onToggleLoop: () -> Unit,
    onFadeDurationChanged: (Float) -> Unit,
    onApplyFade: (Boolean) -> Unit,
    onTrim: () -> Unit,
    onCut: () -> Unit,
    onSilence: () -> Unit,
    onNormalize: () -> Unit,
    onReverse: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ─── Header & Waveform Canvas ──────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INTERACTIVE WAVEFORM (MINI DAW)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Drag to Select Region",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // High-fidelity Waveform
                WaveformDisplay(
                    buffer = buffer,
                    currentPositionSec = currentSec,
                    durationSec = durationSec,
                    selectionStartSec = selectionStart,
                    selectionEndSec = selectionEnd,
                    onSeek = onSeek,
                    onSelectRange = onSelectRange,
                    onClearSelection = onClearSelection,
                    heightDp = 140
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Selection Info Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val hasSelection = selectionStart != null && selectionEnd != null && selectionEnd > selectionStart
                    if (hasSelection) {
                        val start = selectionStart ?: 0.0
                        val end = selectionEnd ?: 0.0
                        val selLen = end - start
                        Text(
                            text = String.format(Locale.US, "Selection: %.2fs → %.2fs (Length: %.2fs)", start, end, selLen),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan
                        )
                    } else {
                        Text(
                            text = "No region selected — drag horizontally on waveform to select a range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ─── Editor Transport & Loop ───────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPlayPause,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPurple),
                        modifier = Modifier.testTag("editor_play_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("editor_stop_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                // Loop Toggle
                FilledTonalButton(
                    onClick = onToggleLoop,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (loopEnabled) StudioPurple.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                        contentColor = if (loopEnabled) StudioPurple else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("editor_loop_button")
                ) {
                    Icon(imageVector = Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (loopEnabled) "Loop ON" else "Loop OFF")
                }
            }
        }

        // ─── Fade Operations ───────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FADE CONTROLS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fs", fadeDuration),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StudioCyan
                    )
                }

                Slider(
                    value = fadeDuration,
                    onValueChange = onFadeDurationChanged,
                    valueRange = 0.1f..5.0f,
                    modifier = Modifier.testTag("fade_duration_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = StudioCyan,
                        activeTrackColor = StudioCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onApplyFade(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fade_in_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Fade In ◢")
                    }

                    Button(
                        onClick = { onApplyFade(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fade_out_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Fade Out ◣")
                    }
                }
            }
        }

        // ─── Region & Editing Operations ───────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DESTRUCTIVE EDIT ACTIONS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Trim
                    Button(
                        onClick = onTrim,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .border(1.dp, StudioPurple, RoundedCornerShape(20.dp))
                            .testTag("trim_button")
                    ) {
                        Icon(imageVector = Icons.Default.Crop, contentDescription = null, tint = StudioPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trim Selection", color = StudioPurple)
                    }

                    // Cut / Delete
                    Button(
                        onClick = onCut,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp))
                            .testTag("cut_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cut / Delete", color = MaterialTheme.colorScheme.error)
                    }

                    // Silence
                    Button(
                        onClick = onSilence,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .border(1.dp, StudioYellow, RoundedCornerShape(20.dp))
                            .testTag("silence_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, tint = StudioYellow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Silence Region", color = StudioYellow)
                    }

                    // Peak Normalize
                    Button(
                        onClick = onNormalize,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .border(1.dp, StudioGreen, RoundedCornerShape(20.dp))
                            .testTag("normalize_button")
                    ) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = StudioGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Peak Normalize (-0.3dB)", color = StudioGreen)
                    }

                    // Reverse
                    Button(
                        onClick = onReverse,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .border(1.dp, StudioCyan, RoundedCornerShape(20.dp))
                            .testTag("reverse_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reverse", color = StudioCyan)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Undo & Reset row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("undo_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Undo Edit")
                    }

                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_original_button")
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Original")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
