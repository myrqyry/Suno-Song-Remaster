package com.example.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class LufsResult(
    val integratedLufs: Double,
    val samplePeakDb: Double,
    val truePeakDb: Double,
    val normalizationGain: Float
)

object LufsMeter {

    private val STAGE1_48K = doubleArrayOf(
        1.53512485958697, -2.69169618940638, 1.19839281085285,
        1.0, -1.69065929318241, 0.73248077421585
    )
    private val STAGE1_44K = doubleArrayOf(
        1.53090250011119, -2.65096950211299, 1.16907907994155,
        1.0, -1.66367351134087, 0.71268558928114
    )

    private val STAGE2_48K = doubleArrayOf(
        1.0, -2.0, 1.0,
        1.0, -1.99004745483398, 0.99007225034111
    )
    private val STAGE2_44K = doubleArrayOf(
        1.0, -2.0, 1.0,
        1.0, -1.98916967949171, 0.98919538321370
    )

    private fun applyIir(input: FloatArray, b: DoubleArray, a: DoubleArray): DoubleArray {
        val out = DoubleArray(input.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        val b0 = b[0]
        val b1 = b[1]
        val b2 = b[2]
        val a1 = a[1]
        val a2 = a[2]

        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            out[i] = y0
        }
        return out
    }

    /**
     * Integrated loudness measurement using the existing validated 44.1/48 kHz
     * K-weighting coefficient sets.
     *
     * Inputs at other sample rates are first converted to 44.1 or 48 kHz so we
     * never apply coefficients designed for one rate directly to another rate.
     * truePeakDb remains a 4x cubic inter-sample estimate on the original signal
     * and is not advertised as standards-certified dBTP.
     */
    fun measure(
        buffer: AudioBuffer,
        targetLufs: Int = AudioConstants.TARGET_LUFS_DEFAULT
    ): LufsResult {
        var originalSamplePeak = 0.0
        for (ch in 0 until buffer.channels) {
            for (value in buffer.getChannel(ch)) {
                originalSamplePeak = maxOf(originalSamplePeak, abs(value.toDouble()))
            }
        }
        val samplePeakDb = if (originalSamplePeak > 1e-6) {
            20.0 * log10(originalSamplePeak)
        } else {
            -96.0
        }
        val truePeakDb = TruePeakEstimator.dbPeak(buffer)

        val measurementBuffer = when (buffer.sampleRate) {
            AudioConstants.SAMPLE_RATE_44K,
            AudioConstants.SAMPLE_RATE_48K -> buffer

            else -> AudioResampler.resample(
                buffer,
                if (buffer.sampleRate >= 46_000) {
                    AudioConstants.SAMPLE_RATE_48K
                } else {
                    AudioConstants.SAMPLE_RATE_44K
                }
            )
        }

        val sampleRate = measurementBuffer.sampleRate
        val is48k = sampleRate == AudioConstants.SAMPLE_RATE_48K
        val s1Coeffs = if (is48k) STAGE1_48K else STAGE1_44K
        val s2Coeffs = if (is48k) STAGE2_48K else STAGE2_44K

        val b1 = doubleArrayOf(s1Coeffs[0], s1Coeffs[1], s1Coeffs[2])
        val a1 = doubleArrayOf(s1Coeffs[3], s1Coeffs[4], s1Coeffs[5])
        val b2 = doubleArrayOf(s2Coeffs[0], s2Coeffs[1], s2Coeffs[2])
        val a2 = doubleArrayOf(s2Coeffs[3], s2Coeffs[4], s2Coeffs[5])

        val numChannels = measurementBuffer.channels.coerceAtMost(2)
        val filtered = Array(numChannels) { DoubleArray(measurementBuffer.length) }

        for (ch in 0 until numChannels) {
            val raw = measurementBuffer.getChannel(ch)
            val stage1 = applyIir(raw, b1, a1)

            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            val b20 = b2[0]
            val b21 = b2[1]
            val b22 = b2[2]
            val a21 = a2[1]
            val a22 = a2[2]
            val dst = filtered[ch]

            for (i in stage1.indices) {
                val x0 = stage1[i]
                val y0 = b20 * x0 + b21 * x1 + b22 * x2 - a21 * y1 - a22 * y2
                x2 = x1
                x1 = x0
                y2 = y1
                y1 = y0
                dst[i] = y0
            }
        }

        val blockSize = (0.4 * sampleRate).roundToInt().coerceAtLeast(1)
        val hopSize = (0.1 * sampleRate).roundToInt().coerceAtLeast(1)
        val numBlocks = (measurementBuffer.length - blockSize) / hopSize + 1

        if (numBlocks <= 0) {
            return LufsResult(-96.0, samplePeakDb, truePeakDb, 1.0f)
        }

        val blockLoudness = DoubleArray(numBlocks)
        val blockMeanSquare = DoubleArray(numBlocks)

        for (block in 0 until numBlocks) {
            val start = block * hopSize
            var sumSquare = 0.0

            for (ch in 0 until numChannels) {
                val data = filtered[ch]
                var channelSum = 0.0
                val end = (start + blockSize).coerceAtMost(data.size)
                for (i in start until end) {
                    val sample = data[i]
                    channelSum += sample * sample
                }
                sumSquare += channelSum / blockSize
            }

            blockMeanSquare[block] = sumSquare
            blockLoudness[block] = if (sumSquare > 1e-12) {
                -0.691 + 10.0 * log10(sumSquare)
            } else {
                -96.0
            }
        }

        var absCount = 0
        var absSum = 0.0
        for (block in 0 until numBlocks) {
            if (blockLoudness[block] > AudioConstants.ABSOLUTE_THRESHOLD_LUFS) {
                absSum += blockMeanSquare[block]
                absCount++
            }
        }

        if (absCount == 0) {
            return LufsResult(-96.0, samplePeakDb, truePeakDb, 1.0f)
        }

        val absLoudness = -0.691 + 10.0 * log10(absSum / absCount)
        val relThreshold = absLoudness + AudioConstants.RELATIVE_THRESHOLD_LU

        var relCount = 0
        var relSum = 0.0
        for (block in 0 until numBlocks) {
            if (
                blockLoudness[block] > AudioConstants.ABSOLUTE_THRESHOLD_LUFS &&
                blockLoudness[block] > relThreshold
            ) {
                relSum += blockMeanSquare[block]
                relCount++
            }
        }

        val integratedLufs = if (relCount > 0) {
            -0.691 + 10.0 * log10(relSum / relCount)
        } else {
            absLoudness
        }

        return LufsResult(
            integratedLufs = integratedLufs,
            samplePeakDb = samplePeakDb,
            truePeakDb = truePeakDb,
            normalizationGain = calculateNormalizationGain(
                integratedLufs,
                targetLufs.toDouble()
            )
        )
    }

    fun calculateNormalizationGain(
        currentLufs: Double,
        targetLufs: Double = AudioConstants.TARGET_LUFS_DEFAULT.toDouble()
    ): Float {
        if (currentLufs <= -70.0 || !currentLufs.isFinite()) return 1.0f
        val gainDb = targetLufs - currentLufs
        val gain = 10.0.pow(gainDb / 20.0)
        return gain.coerceIn(0.1, 10.0).toFloat()
    }
}
