package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadType {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
    PEAKING,
    LOWSHELF,
    HIGHSHELF
}

class BiquadFilter(
    var type: BiquadType,
    var frequency: Double,
    var sampleRate: Double,
    var q: Double = 0.707,
    var gainDb: Double = 0.0
) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Filter states for 2 channels
    private var x1 = DoubleArray(2)
    private var x2 = DoubleArray(2)
    private var y1 = DoubleArray(2)
    private var y2 = DoubleArray(2)

    init {
        recomputeCoefficients()
    }

    fun update(
        frequency: Double = this.frequency,
        q: Double = this.q,
        gainDb: Double = this.gainDb,
        sampleRate: Double = this.sampleRate
    ) {
        this.frequency = frequency
        this.q = q
        this.gainDb = gainDb
        this.sampleRate = sampleRate
        recomputeCoefficients()
    }

    fun reset() {
        x1.fill(0.0)
        x2.fill(0.0)
        y1.fill(0.0)
        y2.fill(0.0)
    }

    fun recomputeCoefficients() {
        if (sampleRate <= 0.0 || frequency <= 0.0) return

        val nyquist = sampleRate * 0.5
        val safeFreq = frequency.coerceIn(1.0, nyquist - 100.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val safeQ = q.coerceAtLeast(0.01)
        val alpha = sinW0 / (2.0 * safeQ)
        val aLinear = 10.0.pow(gainDb / 40.0) // sqrt(10^(gainDb/20))

        var a0 = 1.0

        when (type) {
            BiquadType.LOWPASS -> {
                b0 = (1.0 - cosW0) * 0.5
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) * 0.5
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            BiquadType.HIGHPASS -> {
                b0 = (1.0 + cosW0) * 0.5
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) * 0.5
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            BiquadType.BANDPASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            BiquadType.PEAKING -> {
                b0 = 1.0 + alpha * aLinear
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * aLinear
                a0 = 1.0 + alpha / aLinear
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / aLinear
            }
            BiquadType.LOWSHELF -> {
                val sqrtA = sqrt(aLinear)
                b0 = aLinear * ((aLinear + 1.0) - (aLinear - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = 2.0 * aLinear * ((aLinear - 1.0) - (aLinear + 1.0) * cosW0)
                b2 = aLinear * ((aLinear + 1.0) - (aLinear - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (aLinear + 1.0) + (aLinear - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = -2.0 * ((aLinear - 1.0) + (aLinear + 1.0) * cosW0)
                a2 = (aLinear + 1.0) + (aLinear - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            BiquadType.HIGHSHELF -> {
                val sqrtA = sqrt(aLinear)
                b0 = aLinear * ((aLinear + 1.0) + (aLinear - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = -2.0 * aLinear * ((aLinear - 1.0) + (aLinear + 1.0) * cosW0)
                b2 = aLinear * ((aLinear + 1.0) + (aLinear - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (aLinear + 1.0) - (aLinear - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = 2.0 * ((aLinear - 1.0) - (aLinear + 1.0) * cosW0)
                a2 = (aLinear + 1.0) - (aLinear - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
        }

        // Normalize
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
    }

    fun processSample(input: Float, ch: Int): Float {
        val channel = if (ch in 0..1) ch else 0
        val x0 = input.toDouble()
        val y0 = b0 * x0 + b1 * x1[channel] + b2 * x2[channel] - a1 * y1[channel] - a2 * y2[channel]

        x2[channel] = x1[channel]
        x1[channel] = x0
        y2[channel] = y1[channel]
        y1[channel] = y0

        return y0.toFloat()
    }

    fun processBuffer(buffer: AudioBuffer): AudioBuffer {
        val out = buffer.copy()
        processBufferInPlace(out)
        return out
    }

    /**
     * Applies the filter without allocating a second full-track AudioBuffer.
     * Each channel uses local state so offline processing remains deterministic
     * and independent of any prior processSample() calls on this filter object.
     */
    fun processBufferInPlace(buffer: AudioBuffer) {
        for (c in 0 until buffer.channels) {
            val data = buffer.getChannel(c)
            var stateX1 = 0.0
            var stateX2 = 0.0
            var stateY1 = 0.0
            var stateY2 = 0.0

            for (i in 0 until buffer.length) {
                val x0 = data[i].toDouble()
                val y0 = b0 * x0 + b1 * stateX1 + b2 * stateX2 - a1 * stateY1 - a2 * stateY2

                stateX2 = stateX1
                stateX1 = x0
                stateY2 = stateY1
                stateY1 = y0

                data[i] = y0.toFloat()
            }
        }
    }
}
