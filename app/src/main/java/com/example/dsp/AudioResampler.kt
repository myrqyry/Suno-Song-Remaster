package com.example.dsp

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Lightweight offline resampler for export.
 *
 * Uses cubic interpolation for the rate conversion and applies two low-pass
 * biquad stages before downsampling to reduce aliasing above the target Nyquist
 * region. This is intentionally simple and deterministic for the Android port;
 * it is materially better than exposing a dead sample-rate control, but it is
 * not marketed as mastering-grade SRC.
 */
object AudioResampler {

    fun resample(input: AudioBuffer, targetSampleRate: Int): AudioBuffer {
        require(targetSampleRate > 0) { "targetSampleRate must be positive" }
        if (input.sampleRate == targetSampleRate) return input.copy()
        if (input.length == 0) return AudioBuffer(input.channels, 0, targetSampleRate)

        val prepared = if (targetSampleRate < input.sampleRate) {
            val cutoffHz = (targetSampleRate * 0.45).coerceAtMost(input.sampleRate * 0.45)
            val first = BiquadFilter(
                BiquadType.LOWPASS,
                cutoffHz,
                input.sampleRate.toDouble(),
                q = 0.707
            ).processBuffer(input)
            BiquadFilter(
                BiquadType.LOWPASS,
                cutoffHz,
                input.sampleRate.toDouble(),
                q = 0.707
            ).processBuffer(first)
        } else {
            input
        }

        val ratio = targetSampleRate.toDouble() / prepared.sampleRate.toDouble()
        val outputLength = (prepared.length * ratio).roundToInt().coerceAtLeast(1)
        val output = AudioBuffer(prepared.channels, outputLength, targetSampleRate)

        for (channel in 0 until prepared.channels) {
            val src = prepared.getChannel(channel)
            val dst = output.getChannel(channel)

            for (outIndex in 0 until outputLength) {
                val srcPosition = outIndex.toDouble() / ratio
                val i1 = floor(srcPosition).toInt().coerceIn(0, src.lastIndex)
                val t = srcPosition - i1
                val i0 = (i1 - 1).coerceAtLeast(0)
                val i2 = (i1 + 1).coerceAtMost(src.lastIndex)
                val i3 = (i1 + 2).coerceAtMost(src.lastIndex)

                dst[outIndex] = catmullRom(
                    src[i0].toDouble(),
                    src[i1].toDouble(),
                    src[i2].toDouble(),
                    src[i3].toDouble(),
                    t
                ).toFloat()
            }
        }

        return output
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
