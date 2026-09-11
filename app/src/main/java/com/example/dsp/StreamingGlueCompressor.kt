package com.example.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Stateful sample-by-sample version of the offline glue compressor for preview. */
class StreamingGlueCompressor(sampleRate: Int) {
    private val thresholdDb = AudioConstants.GLUE_THRESHOLD
    private val ratio = AudioConstants.GLUE_RATIO
    private val kneeDb = 6.0
    private val alphaAttack = exp(-1.0 / (AudioConstants.GLUE_ATTACK * sampleRate))
    private val alphaRelease = exp(-1.0 / (AudioConstants.GLUE_RELEASE * sampleRate))

    private var envelopeDb = -96.0

    fun reset() {
        envelopeDb = -96.0
    }

    fun gainFor(left: Float, right: Float): Float {
        val maxVal = max(abs(left), abs(right))
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

        val delta = envelopeDb - thresholdDb
        val gainReductionDb = when {
            delta > kneeDb / 2.0 -> delta * (1.0 - 1.0 / ratio)
            delta > -kneeDb / 2.0 -> {
                val x = delta + kneeDb / 2.0
                ((1.0 - 1.0 / ratio) * (x * x)) / (2.0 * kneeDb)
            }
            else -> 0.0
        }

        return 10.0.pow(-gainReductionDb / 20.0).toFloat()
    }
}
