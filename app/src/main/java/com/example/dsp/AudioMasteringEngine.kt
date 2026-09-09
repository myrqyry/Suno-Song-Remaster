package com.example.dsp

import com.example.model.MasteringSettings
import kotlin.math.pow

object AudioMasteringEngine {

    /**
     * Executes the complete offline mastering DSP pipeline on an AudioBuffer.
     */
    fun processOffline(
        inputBuffer: AudioBuffer,
        settings: MasteringSettings,
        precalculatedLufsGain: Float? = null,
        onProgress: ((Float) -> Unit)? = null
    ): AudioBuffer {
        val sr = inputBuffer.sampleRate.toDouble()
        onProgress?.invoke(0.1f)

        // 1. Input Gain
        var current = inputBuffer.copy()
        if (settings.inputGain != 0.0f) {
            val linearGain = 10.0.pow(settings.inputGain.toDouble() / 20.0).toFloat()
            for (c in 0 until current.channels) {
                val data = current.getChannel(c)
                for (i in 0 until current.length) {
                    data[i] *= linearGain
                }
            }
        }
        onProgress?.invoke(0.2f)

        // 2. Clean Low End (30Hz Highpass)
        if (settings.cleanLowEnd) {
            val hp = BiquadFilter(BiquadType.HIGHPASS, AudioConstants.HIGHPASS_FREQ, sr, q = 0.707)
            current = hp.processBuffer(current)
        }
        onProgress?.invoke(0.3f)

        // 3. 5-Band Equalizer
        if (settings.eqLow != 0.0f) {
            val lowFilter = BiquadFilter(BiquadType.LOWSHELF, AudioConstants.FREQ_LOW, sr, gainDb = settings.eqLow.toDouble())
            current = lowFilter.processBuffer(current)
        }
        if (settings.eqLowMid != 0.0f) {
            val lowMidFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_LOW_MID, sr, q = 1.0, gainDb = settings.eqLowMid.toDouble())
            current = lowMidFilter.processBuffer(current)
        }
        if (settings.eqMid != 0.0f) {
            val midFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_MID, sr, q = 1.0, gainDb = settings.eqMid.toDouble())
            current = midFilter.processBuffer(current)
        }
        if (settings.eqHighMid != 0.0f) {
            val highMidFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_HIGH_MID, sr, q = 1.0, gainDb = settings.eqHighMid.toDouble())
            current = highMidFilter.processBuffer(current)
        }
        if (settings.eqHigh != 0.0f) {
            val highFilter = BiquadFilter(BiquadType.HIGHSHELF, AudioConstants.FREQ_HIGH, sr, gainDb = settings.eqHigh.toDouble())
            current = highFilter.processBuffer(current)
        }
        onProgress?.invoke(0.45f)

        // 4. Character Filters: Cut Mud, Add Air, Tame Harsh
        if (settings.cutMud) {
            val mud = BiquadFilter(BiquadType.PEAKING, AudioConstants.MUD_CUT_FREQ, sr, q = 1.5, gainDb = -3.0)
            current = mud.processBuffer(current)
        }
        if (settings.addAir) {
            val air = BiquadFilter(BiquadType.HIGHSHELF, AudioConstants.AIR_FREQ, sr, gainDb = 2.5)
            current = air.processBuffer(current)
        }
        if (settings.tameHarsh) {
            val harsh1 = BiquadFilter(BiquadType.PEAKING, AudioConstants.HARSHNESS_FREQ_1, sr, q = AudioConstants.HARSHNESS_Q_4K, gainDb = AudioConstants.HARSHNESS_GAIN_4K)
            current = harsh1.processBuffer(current)
            val harsh2 = BiquadFilter(BiquadType.PEAKING, AudioConstants.HARSHNESS_FREQ_2, sr, q = AudioConstants.HARSHNESS_Q_6K, gainDb = AudioConstants.HARSHNESS_GAIN_6K)
            current = harsh2.processBuffer(current)
        }
        onProgress?.invoke(0.6f)

        // 5. Glue Compressor
        val dyn = DynamicsProcessor(inputBuffer.sampleRate)
        if (settings.glueCompression) {
            current = dyn.processGlueCompression(current)
        }
        onProgress?.invoke(0.7f)

        // 6. Mid-Side Stereo Processing & Center Bass
        val stereo = StereoProcessor(inputBuffer.sampleRate)
        current = stereo.process(current, settings.stereoWidth, settings.centerBass)
        onProgress?.invoke(0.8f)

        // 7. Loudness Normalization (ITU-R BS.1770-4)
        if (settings.normalizeLoudness) {
            val normGain = precalculatedLufsGain ?: run {
                val lufsRes = LufsMeter.measure(current, settings.targetLufs)
                lufsRes.normalizationGain
            }
            for (c in 0 until current.channels) {
                val data = current.getChannel(c)
                for (i in 0 until current.length) {
                    data[i] *= normGain
                }
            }
        }
        onProgress?.invoke(0.9f)

        // 8. True Peak Limiter & Ceiling
        if (settings.truePeakLimit) {
            current = dyn.processLimiter(current, settings.truePeakCeiling)
        }

        onProgress?.invoke(1.0f)
        return current
    }
}
