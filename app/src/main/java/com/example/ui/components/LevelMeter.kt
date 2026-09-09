package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.MeterData
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioYellow

@Composable
fun LevelMeter(
    meterData: MeterData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("stereo_level_meter")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OUTPUT LEVEL (dBFS)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Clip indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ClipBadge(label = "CLIP L", isClipped = meterData.leftClip)
                ClipBadge(label = "CLIP R", isClipped = meterData.rightClip)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Left Channel
        ChannelBar(label = "L", db = meterData.leftDb, peakDb = meterData.leftPeakDb)

        Spacer(modifier = Modifier.height(6.dp))

        // Right Channel
        ChannelBar(label = "R", db = meterData.rightDb, peakDb = meterData.rightPeakDb)

        Spacer(modifier = Modifier.height(6.dp))

        // dB scale labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("-60", "-36", "-24", "-18", "-12", "-6", "0", "+3").forEach { label ->
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ClipBadge(label: String, isClipped: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (isClipped) StudioRed else MaterialTheme.colorScheme.surfaceVariant,
        label = "clip_bg"
    )
    val textColor = if (isClipped) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun ChannelBar(
    label: String,
    db: Float,
    peakDb: Float
) {
    // Map -60 dB to 0.0, 0 dB to 0.88, +3 dB to 1.0
    val normalizedVal = ((db + 60f) / 63f).coerceIn(0f, 1f)
    val normalizedPeak = ((peakDb + 60f) / 63f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(20.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                // Meter gradient: Green (-60 to -12) -> Yellow (-12 to -3) -> Red (-3 to +3)
                val fillWidth = width * normalizedVal
                if (fillWidth > 0) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to StudioGreen,
                            0.75f to StudioGreen,
                            0.88f to StudioYellow,
                            1.0f to StudioRed
                        ),
                        size = Size(fillWidth, height)
                    )
                }

                // Peak hold line
                val peakX = width * normalizedPeak
                if (peakX > 0) {
                    drawLine(
                        color = if (peakDb >= -0.5f) StudioRed else Color.White,
                        start = Offset(peakX, 0f),
                        end = Offset(peakX, height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (db > -59f) "${db.toInt()} dB" else "-∞ dB",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )
    }
}
