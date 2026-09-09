package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.model.MasteringSettings
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPurple
import com.example.ui.theme.StudioPurpleLight
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasteringConsoleScreen(
    settings: MasteringSettings,
    onSettingsChanged: (MasteringSettings) -> Unit,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ─── EQ Presets ────────────────────────────────────────────────────────
        SectionCard(title = "EQ PRESETS", icon = Icons.Default.Tune) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MasteringSettings.PRESETS.forEach { (key, preset) ->
                    val isSelected = settings.activePreset == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(key) },
                        label = { Text(preset.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioPurple,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("preset_chip_$key")
                    )
                }
            }
        }

        // ─── 5-Band Equalizer ──────────────────────────────────────────────────
        SectionCard(
            title = "5-BAND MASTERING EQUALIZER",
            trailing = {
                IconButton(
                    onClick = { onPresetSelected("flat") },
                    modifier = Modifier.testTag("reset_eq_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset EQ",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EqSliderRow("Low 80Hz", settings.eqLow, "eq_low_slider") {
                    onSettingsChanged(settings.copy(eqLow = it, activePreset = "custom"))
                }
                EqSliderRow("Low-Mid 250Hz", settings.eqLowMid, "eq_low_mid_slider") {
                    onSettingsChanged(settings.copy(eqLowMid = it, activePreset = "custom"))
                }
                EqSliderRow("Mid 1kHz", settings.eqMid, "eq_mid_slider") {
                    onSettingsChanged(settings.copy(eqMid = it, activePreset = "custom"))
                }
                EqSliderRow("High-Mid 4kHz", settings.eqHighMid, "eq_high_mid_slider") {
                    onSettingsChanged(settings.copy(eqHighMid = it, activePreset = "custom"))
                }
                EqSliderRow("High 12kHz", settings.eqHigh, "eq_high_slider") {
                    onSettingsChanged(settings.copy(eqHigh = it, activePreset = "custom"))
                }
            }
        }

        // ─── DSP Processing Modules ────────────────────────────────────────────
        SectionCard(title = "PROCESSING MODULES") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleRow(
                    title = "Clean Low End",
                    subtitle = "30 Hz Highpass filter removes inaudible sub-rumble",
                    checked = settings.cleanLowEnd,
                    tag = "clean_low_end_toggle"
                ) { onSettingsChanged(settings.copy(cleanLowEnd = it)) }

                ToggleRow(
                    title = "Cut Mud",
                    subtitle = "Gentle -3 dB notch at 250 Hz for clarity and punch",
                    checked = settings.cutMud,
                    tag = "cut_mud_toggle"
                ) { onSettingsChanged(settings.copy(cutMud = it)) }

                ToggleRow(
                    title = "Add Air",
                    subtitle = "+2.5 dB High Shelf boost at 12 kHz for shimmer",
                    checked = settings.addAir,
                    tag = "add_air_toggle"
                ) { onSettingsChanged(settings.copy(addAir = it)) }

                ToggleRow(
                    title = "Tame Harshness",
                    subtitle = "Dual narrow notches at 4 kHz & 6 kHz to smooth sibilance",
                    checked = settings.tameHarsh,
                    tag = "tame_harsh_toggle"
                ) { onSettingsChanged(settings.copy(tameHarsh = it)) }

                ToggleRow(
                    title = "Glue Compression",
                    subtitle = "3:1 bus compressor (-18 dB threshold, 20ms attack) gels the mix",
                    checked = settings.glueCompression,
                    tag = "glue_compression_toggle"
                ) { onSettingsChanged(settings.copy(glueCompression = it)) }

                ToggleRow(
                    title = "Center Bass (Mono Sub)",
                    subtitle = "Collapses stereo frequencies below 120 Hz to tight mono",
                    checked = settings.centerBass,
                    tag = "center_bass_toggle"
                ) { onSettingsChanged(settings.copy(centerBass = it)) }

                ToggleRow(
                    title = "True Peak Limiter",
                    subtitle = "Brickwall limiter prevents inter-sample clipping and distortion",
                    checked = settings.truePeakLimit,
                    tag = "true_peak_limit_toggle"
                ) { onSettingsChanged(settings.copy(truePeakLimit = it)) }
            }
        }

        // ─── Mastering Parameters ──────────────────────────────────────────────
        SectionCard(title = "MASTERING TARGETS & STEREO") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Target LUFS
                ParamSliderRow(
                    label = "Target Loudness",
                    valueStr = "${settings.targetLufs} LUFS",
                    value = settings.targetLufs.toFloat(),
                    range = -24f..-6f,
                    tag = "target_lufs_slider"
                ) { onSettingsChanged(settings.copy(targetLufs = it.toInt())) }

                // True Peak Ceiling
                ParamSliderRow(
                    label = "True Peak Ceiling",
                    valueStr = String.format(Locale.US, "%.1f dBTP", settings.truePeakCeiling),
                    value = settings.truePeakCeiling,
                    range = -3.0f..0.0f,
                    tag = "true_peak_ceiling_slider"
                ) { onSettingsChanged(settings.copy(truePeakCeiling = it)) }

                // Stereo Width
                ParamSliderRow(
                    label = "Stereo Width",
                    valueStr = "${settings.stereoWidth}%",
                    value = settings.stereoWidth.toFloat(),
                    range = 0f..200f,
                    tag = "stereo_width_slider"
                ) { onSettingsChanged(settings.copy(stereoWidth = it.toInt())) }

                // Input Gain
                ParamSliderRow(
                    label = "Input Gain",
                    valueStr = String.format(Locale.US, "%+.1f dB", settings.inputGain),
                    value = settings.inputGain,
                    range = -12.0f..12.0f,
                    tag = "input_gain_slider"
                ) { onSettingsChanged(settings.copy(inputGain = it)) }
            }
        }

        // ─── Output Format ─────────────────────────────────────────────────────
        SectionCard(title = "EXPORT FORMAT SPECIFICATIONS") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sample Rate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(44100, 48000).forEach { rate ->
                        val isSel = settings.sampleRate == rate
                        FilterChip(
                            selected = isSel,
                            onClick = { onSettingsChanged(settings.copy(sampleRate = rate)) },
                            label = { Text(if (rate == 44100) "44.1 kHz" else "48.0 kHz") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bit Depth",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(16, 24).forEach { bits ->
                        val isSel = settings.bitDepth == bits
                        FilterChip(
                            selected = isSel,
                            onClick = { onSettingsChanged(settings.copy(bitDepth = bits)) },
                            label = { Text(if (bits == 16) "16-bit (Dithered)" else "24-bit Hi-Res") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StudioPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = StudioPurple,
                            modifier = Modifier.width(18.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailing?.invoke()
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EqSliderRow(
    label: String,
    value: Float,
    tag: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(115.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -12.0f..12.0f,
            modifier = Modifier
                .weight(1f)
                .testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = StudioPurple,
                activeTrackColor = StudioPurple,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        Text(
            text = String.format(Locale.US, "%+.1f dB", value),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (value != 0f) StudioPurpleLight else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(55.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StudioPurple,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ParamSliderRow(
    label: String,
    valueStr: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueStr,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = StudioCyan
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = StudioCyan,
                activeTrackColor = StudioCyan,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    }
}
