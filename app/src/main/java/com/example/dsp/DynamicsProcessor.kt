package com.example.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

class DynamicsProcessor(private val sampleRate: Int) {

    /**
     * Glue bus compressor:
     * - Threshold: -18 dBFS
     * - Ratio: 3:1
     * - Attack: 20 ms
     * - Release: 250 ms
     * - Soft knee: 6 dB
     */
    fun processGlueCompression(input: AudioBuffer): AudioBuffer {
        val out = input.copy()
        val numChannels = out.channels
        val length = out.length

        val thresholdDb = AudioConstants.GLUE_THRESHOLD
        val ratio = AudioConstants.GLUE_RATIO
        val attackSec = AudioConstants.GLUE_ATTACK
        val releaseSec = AudioConstants.GLUE_RELEASE
        val kneeDb = 6.0

        val alphaAttack = exp(-1.0 / (attackSec * sampleRate))
        val alphaRelease = exp(-1.0 / (releaseSec * sampleRate))

        var envelopeDb = -96.0

        for (i in 0 until length) {
            var maxVal = 0f
            for (c in 0 until numChannels) {
                val a = abs(out.getChannel(c)[i])
                if (a > maxVal) maxVal = a
            }

            val inputLevelDb = if (maxVal > 1e-6f) {
                20.0 * log10(maxVal.toDouble())
            } else {
                -96.0
            }

            envelopeDb = if (inputLevelDb > envelopeDb) {
                alphaAttack * envelopeDb + (1.0 - alphaAttack) * inputLevelDb
            } else {
                alphaRelease * envelopeDb + (1.0 - alphaRelease) * inputLevelDb
            }

            var grDb = 0.0
            val delta = envelopeDb - thresholdDb
            if (delta > kneeDb / 2.0) {
                grDb = delta * (1.0 - 1.0 / ratio)
            } else if (delta > -kneeDb / 2.0) {
                val x = delta + kneeDb / 2.0
                grDb = ((1.0 - 1.0 / ratio) * (x * x)) / (2.0 * kneeDb)
            }

            val gain = 10.0.pow(-grDb / 20.0).toFloat()
            for (c in 0 until numChannels) {
                out.getChannel(c)[i] *= gain
            }
        }

        return out
    }

    /**
     * Peak limiter with a final 4x inter-sample safety pass.
     *
     * The envelope stage controls local dynamics at the native sample rate.
     * Afterwards TruePeakEstimator checks between samples and, only when
     * necessary, applies a small global safety trim so the measured 4x
     * inter-sample peak does not exceed the requested ceiling.
     *
     * This is deliberately described as an inter-sample ceiling rather than a
     * standards-certified dBTP limiter.
     */
    fun processLimiter(input: AudioBuffer, ceilingDb: Float): AudioBuffer {
        val out = input.copy()
        val numChannels = out.channels
        val length = out.length

        val ceilingLinear = 10.0.pow(ceilingDb.toDouble() / 20.0).toFloat()
        val attackSec = AudioConstants.LIMITER_ATTACK
        val releaseSec = AudioConstants.LIMITER_RELEASE

        val alphaAttack = exp(-1.0 / (attackSec * sampleRate))
        val alphaRelease = exp(-1.0 / (releaseSec * sampleRate))

        var envelopeDb = -96.0
        val thresholdDb = ceilingDb.toDouble()

        for (i in 0 until length) {
            var maxVal = 0f
            for (c in 0 until numChannels) {
                val a = abs(out.getChannel(c)[i])
                if (a > maxVal) maxVal = a
            }

            val inputDb = if (maxVal > 1e-6f) {
                20.0 * log10(maxVal.toDouble())
            } else {
                -96.0
            }

            envelopeDb = if (inputDb > envelopeDb) {
                alphaAttack * envelopeDb + (1.0 - alphaAttack) * inputDb
            } else {
                alphaRelease * envelopeDb + (1.0 - alphaRelease) * inputDb
            }

            var gain = 1.0f
            if (envelopeDb > thresholdDb) {
                val grDb = (envelopeDb - thresholdDb) *
                    (1.0 - 1.0 / AudioConstants.LIMITER_RATIO)
                gain = 10.0.pow(-grDb / 20.0).toFloat()
            }

            for (c in 0 until numChannels) {
                var sample = out.getChannel(c)[i] * gain
                sample = sample.coerceIn(-ceilingLinear, ceilingLinear)
                out.getChannel(c)[i] = sample
            }
        }

        val interSamplePeak = TruePeakEstimator.linearPeak(out)
        if (interSamplePeak > ceilingLinear && interSamplePeak > 0.0) {
            val safetyGain = (ceilingLinear / interSamplePeak * 0.9999).toFloat()
            for (c in 0 until numChannels) {
                val data = out.getChannel(c)
                for (i in data.indices) {
                    data[i] *= safetyGain
                }
            }
        }

        return out
    }
}
