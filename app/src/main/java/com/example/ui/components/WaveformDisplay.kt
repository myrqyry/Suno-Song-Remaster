package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dsp.AudioBuffer
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPurple
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun WaveformDisplay(
    buffer: AudioBuffer?,
    currentPositionSec: Double,
    durationSec: Double,
    selectionStartSec: Double? = null,
    selectionEndSec: Double? = null,
    onSeek: (Double) -> Unit,
    onSelectRange: ((Double, Double) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    heightDp: Int = 100
) {
    // Cache waveform peak bars
    val peakData = remember(buffer) {
        if (buffer == null || buffer.length == 0) floatArrayOf()
        else {
            val numBuckets = 200
            val bucketSize = max(1, buffer.length / numBuckets)
            val channel = buffer.getChannel(0)
            val peaks = FloatArray(numBuckets)
            for (b in 0 until numBuckets) {
                var p = 0f
                val start = b * bucketSize
                val end = (start + bucketSize).coerceAtMost(buffer.length)
                for (i in start until end) {
                    val a = abs(channel[i])
                    if (a > p) p = a
                }
                peaks[b] = p.coerceIn(0.02f, 1.0f)
            }
            peaks
        }
    }

    var dragStartPct by remember { mutableStateOf<Float?>(null) }
    var dragCurrentPct by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("waveform_canvas_box")
            .pointerInput(buffer, durationSec) {
                detectTapGestures { offset ->
                    val pct = (offset.x / size.width).coerceIn(0f, 1f)
                    val seekTarget = pct * durationSec
                    onSeek(seekTarget)
                    onClearSelection?.invoke()
                }
            }
            .pointerInput(buffer, durationSec) {
                if (onSelectRange != null) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val pct = (offset.x / size.width).coerceIn(0f, 1f)
                            dragStartPct = pct
                            dragCurrentPct = pct
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pct = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragCurrentPct = pct
                            val s = dragStartPct ?: pct
                            val startP = min(s, pct)
                            val endP = max(s, pct)
                            onSelectRange(startP * durationSec, endP * durationSec)
                        },
                        onDragEnd = {
                            dragStartPct = null
                            dragCurrentPct = null
                        },
                        onDragCancel = {
                            dragStartPct = null
                            dragCurrentPct = null
                        }
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Center line
            drawLine(
                color = StudioPurple.copy(alpha = 0.2f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Draw selection background if present
            if (selectionStartSec != null && selectionEndSec != null && durationSec > 0) {
                val startX = ((selectionStartSec / durationSec) * w).toFloat()
                val endX = ((selectionEndSec / durationSec) * w).toFloat()
                val selW = max(2f, endX - startX)

                drawRect(
                    color = StudioPurple.copy(alpha = 0.35f),
                    topLeft = Offset(startX, 0f),
                    size = Size(selW, h)
                )

                // Selection boundary lines
                drawLine(
                    color = StudioCyan,
                    start = Offset(startX, 0f),
                    end = Offset(startX, h),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = StudioCyan,
                    start = Offset(endX, 0f),
                    end = Offset(endX, h),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw waveform peaks
            val playheadProgress = if (durationSec > 0) (currentPositionSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f
            val playheadX = playheadProgress * w

            val numBars = peakData.size
            if (numBars > 0) {
                val barWidth = (w / numBars).coerceAtLeast(1.5f)

                for (i in 0 until numBars) {
                    val x = i * (w / numBars)
                    val amp = peakData[i] * (h * 0.42f)
                    val isPlayed = x <= playheadX

                    val barColor = if (isPlayed) {
                        StudioPurple
                    } else {
                        StudioPurple.copy(alpha = 0.4f)
                    }

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, midY - amp),
                        size = Size(max(1f, barWidth - 1f), amp * 2f)
                    )
                }
            }

            // Playhead indicator
            if (durationSec > 0) {
                drawLine(
                    color = Color.White,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, h),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = StudioCyan,
                    radius = 4.dp.toPx(),
                    center = Offset(playheadX, midY)
                )
            }
        }
    }
}
