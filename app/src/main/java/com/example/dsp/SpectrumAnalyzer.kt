package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small real-time FFT analyzer for the 16 UI bars.
 *
 * Audio is Hann-windowed, transformed with an in-place radix-2 FFT, then FFT
 * bins from 40 Hz to 16 kHz are grouped into logarithmically spaced bands.
 * This makes every displayed bar represent real spectral energy and avoids the
 * large frequency gaps caused by probing only one center frequency per band.
 */
object SpectrumAnalyzer {
    private const val MIN_FREQUENCY = 40.0
    private const val MAX_FREQUENCY = 16_000.0
    private const val FLOOR_DB = -72.0

    fun analyzeMono(samples: FloatArray, sampleRate: Int, bandCount: Int = 16): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0 || bandCount <= 0) {
            return FloatArray(bandCount.coerceAtLeast(0))
        }

        val fftSize = largestPowerOfTwo(samples.size)
        if (fftSize < 2) return FloatArray(bandCount)

        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        val denominator = (fftSize - 1).coerceAtLeast(1).toDouble()
        for (i in 0 until fftSize) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / denominator)
            real[i] = samples[i] * window
        }

        fft(real, imag)

        val nyquistSafe = sampleRate * 0.45
        val maxFrequency = minOf(MAX_FREQUENCY, nyquistSafe)
        if (maxFrequency <= MIN_FREQUENCY) return FloatArray(bandCount)

        val logRange = ln(maxFrequency / MIN_FREQUENCY)
        val sumPower = DoubleArray(bandCount)
        val binCount = IntArray(bandCount)

        // Ignore DC and the Nyquist bin. Each magnitude is normalized to the
        // FFT length; averaging power within a band prevents wider high bands
        // from appearing louder simply because they contain more FFT bins.
        for (bin in 1 until fftSize / 2) {
            val frequency = bin.toDouble() * sampleRate / fftSize
            if (frequency < MIN_FREQUENCY || frequency > maxFrequency) continue

            val normalized = ln(frequency / MIN_FREQUENCY) / logRange
            val band = floor(normalized * bandCount)
                .toInt()
                .coerceIn(0, bandCount - 1)
            val magnitude = 2.0 * hypot(real[bin], imag[bin]) / fftSize
            sumPower[band] += magnitude * magnitude
            binCount[band]++
        }

        return FloatArray(bandCount) { band ->
            if (binCount[band] == 0) {
                0f
            } else {
                val rmsMagnitude = sqrt(sumPower[band] / binCount[band]).coerceAtLeast(1e-9)
                val db = 20.0 * log10(rmsMagnitude)
                ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
            }
        }
    }

    private fun largestPowerOfTwo(size: Int): Int {
        var value = 1
        while (value <= size / 2) value *= 2
        return value
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realTmp = real[i]
                real[i] = real[j]
                real[j] = realTmp

                val imagTmp = imag[i]
                imag[i] = imag[j]
                imag[j] = imagTmp
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wLenReal = cos(angle)
            val wLenImag = sin(angle)
            val half = length / 2

            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImag = 0.0
                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half

                    val oddReal = real[odd] * wReal - imag[odd] * wImag
                    val oddImag = real[odd] * wImag + imag[odd] * wReal

                    val evenReal = real[even]
                    val evenImag = imag[even]
                    real[even] = evenReal + oddReal
                    imag[even] = evenImag + oddImag
                    real[odd] = evenReal - oddReal
                    imag[odd] = evenImag - oddImag

                    val nextWReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
