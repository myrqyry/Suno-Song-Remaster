package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Small real-time frequency analyzer for the 16 UI bars.
 *
 * Uses a Hann window plus one Goertzel detector per logarithmically spaced
 * center frequency. The bars therefore represent actual frequency energy
 * instead of distributing sample amplitudes by array index.
 */
object SpectrumAnalyzer {
    private const val MIN_FREQUENCY = 40.0
    private const val MAX_FREQUENCY = 16_000.0
    private const val FLOOR_DB = -60.0

    fun analyzeMono(samples: FloatArray, sampleRate: Int, bandCount: Int = 16): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0 || bandCount <= 0) {
            return FloatArray(bandCount.coerceAtLeast(0))
        }

        val windowed = DoubleArray(samples.size)
        val denominator = (samples.size - 1).coerceAtLeast(1).toDouble()
        for (i in samples.indices) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / denominator)
            windowed[i] = samples[i] * window
        }

        val nyquistSafe = sampleRate * 0.45
        val maxFrequency = minOf(MAX_FREQUENCY, nyquistSafe).coerceAtLeast(MIN_FREQUENCY)
        val ratio = if (bandCount > 1) {
            (maxFrequency / MIN_FREQUENCY).pow(1.0 / (bandCount - 1))
        } else {
            1.0
        }

        return FloatArray(bandCount) { band ->
            val frequency = (MIN_FREQUENCY * ratio.pow(band.toDouble()))
                .coerceAtMost(nyquistSafe)
            val omega = 2.0 * PI * frequency / sampleRate
            val coefficient = 2.0 * cos(omega)

            var q1 = 0.0
            var q2 = 0.0
            for (sample in windowed) {
                val q0 = sample + coefficient * q1 - q2
                q2 = q1
                q1 = q0
            }

            val power = (q1 * q1 + q2 * q2 - coefficient * q1 * q2).coerceAtLeast(0.0)
            val magnitude = (2.0 * sqrt(power) / samples.size).coerceAtLeast(1e-6)
            val db = 20.0 * log10(magnitude)
            ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
