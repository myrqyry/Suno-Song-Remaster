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

    /**
     * Integrated loudness measurement using the existing validated 44.1/48 kHz
     * K-weighting coefficient sets.
     *
     * The K-weighting and 400 ms / 100 ms gating pass is streamed. Earlier
     * versions allocated multiple full-song DoubleArrays per channel, which
     * could push a normal MP3 load over Android's heap limit immediately after
     * decoding. This implementation keeps only per-channel filter state, a
     * 400 ms rolling power window, and the small per-block gating arrays.
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
        val s1 = if (is48k) STAGE1_48K else STAGE1_44K
        val s2 = if (is48k) STAGE2_48K else STAGE2_44K
        val numChannels = measurementBuffer.channels.coerceAtMost(2)

        val blockSize = (0.4 * sampleRate).roundToInt().coerceAtLeast(1)
        val hopSize = (0.1 * sampleRate).roundToInt().coerceAtLeast(1)
        val numBlocks = (measurementBuffer.length - blockSize) / hopSize + 1

        if (numBlocks <= 0 || numChannels <= 0) {
            return LufsResult(-96.0, samplePeakDb, truePeakDb, 1.0f)
        }

        // Per-channel state for the two cascaded K-weighting IIR sections.
        val s1x1 = DoubleArray(numChannels)
        val s1x2 = DoubleArray(numChannels)
        val s1y1 = DoubleArray(numChannels)
        val s1y2 = DoubleArray(numChannels)
        val s2x1 = DoubleArray(numChannels)
        val s2x2 = DoubleArray(numChannels)
        val s2y1 = DoubleArray(numChannels)
        val s2y2 = DoubleArray(numChannels)

        // One rolling 400 ms window replaces several full-track DoubleArrays.
        // Each entry is the channel-summed K-weighted power for one frame.
        val powerRing = DoubleArray(blockSize)
        var rollingPower = 0.0

        val blockLoudness = DoubleArray(numBlocks)
        val blockMeanSquare = DoubleArray(numBlocks)
        var blockIndex = 0

        for (i in 0 until measurementBuffer.length) {
            var framePower = 0.0

            for (ch in 0 until numChannels) {
                val x0 = measurementBuffer.getChannel(ch)[i].toDouble()

                val yStage1 = s1[0] * x0 +
                    s1[1] * s1x1[ch] +
                    s1[2] * s1x2[ch] -
                    s1[4] * s1y1[ch] -
                    s1[5] * s1y2[ch]

                s1x2[ch] = s1x1[ch]
                s1x1[ch] = x0
                s1y2[ch] = s1y1[ch]
                s1y1[ch] = yStage1

                val yStage2 = s2[0] * yStage1 +
                    s2[1] * s2x1[ch] +
                    s2[2] * s2x2[ch] -
                    s2[4] * s2y1[ch] -
                    s2[5] * s2y2[ch]

                s2x2[ch] = s2x1[ch]
                s2x1[ch] = yStage1
                s2y2[ch] = s2y1[ch]
                s2y1[ch] = yStage2

                framePower += yStage2 * yStage2
            }

            val ringIndex = i % blockSize
            if (i >= blockSize) {
                rollingPower -= powerRing[ringIndex]
            }
            powerRing[ringIndex] = framePower
            rollingPower += framePower

            if (i + 1 >= blockSize) {
                val blockStart = i + 1 - blockSize
                if (blockStart % hopSize == 0 && blockIndex < numBlocks) {
                    val meanSquare = rollingPower / blockSize
                    blockMeanSquare[blockIndex] = meanSquare
                    blockLoudness[blockIndex] = if (meanSquare > 1e-12) {
                        -0.691 + 10.0 * log10(meanSquare)
                    } else {
                        -96.0
                    }
                    blockIndex++
                }
            }
        }

        var absCount = 0
        var absSum = 0.0
        for (block in 0 until blockIndex) {
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
        for (block in 0 until blockIndex) {
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
