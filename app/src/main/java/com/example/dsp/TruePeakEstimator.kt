package com.example.dsp

import kotlin.math.abs
import kotlin.math.log10

/**
 * Estimates inter-sample peak amplitude by evaluating a Catmull-Rom cubic
 * interpolant at 4x the source sample grid (0, 0.25, 0.5, 0.75).
 *
 * This is a real inter-sample measurement, but it is intentionally not
 * advertised as standards-certified ITU/EBU dBTP metering. A standards-grade
 * implementation would require a specified oversampling filter response.
 */
object TruePeakEstimator {
    const val OVERSAMPLE_FACTOR = 4

    fun linearPeak(buffer: AudioBuffer): Double {
        if (buffer.length == 0 || buffer.channels == 0) return 0.0

        var peak = 0.0
        for (channel in 0 until buffer.channels) {
            val data = buffer.getChannel(channel)
            if (data.isEmpty()) continue

            for (i in data.indices) {
                peak = maxOf(peak, abs(data[i].toDouble()))
            }

            if (data.size < 2) continue
            for (i in 0 until data.lastIndex) {
                val p0 = data[(i - 1).coerceAtLeast(0)].toDouble()
                val p1 = data[i].toDouble()
                val p2 = data[i + 1].toDouble()
                val p3 = data[(i + 2).coerceAtMost(data.lastIndex)].toDouble()

                for (phase in 1 until OVERSAMPLE_FACTOR) {
                    val t = phase.toDouble() / OVERSAMPLE_FACTOR
                    val sample = catmullRom(p0, p1, p2, p3, t)
                    peak = maxOf(peak, abs(sample))
                }
            }
        }
        return peak
    }

    fun dbPeak(buffer: AudioBuffer): Double {
        val peak = linearPeak(buffer)
        return if (peak > 1e-12) 20.0 * log10(peak) else -96.0
    }

    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * (
            (2.0 * p1) +
                (-p0 + p2) * t +
                (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
                (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
            )
    }
}
