package com.example.dsp

import com.example.model.MasteringSettings
import kotlin.math.pow

object AudioMasteringEngine {

    /**
     * Executes the complete offline mastering DSP pipeline on an AudioBuffer.
     *
     * Export sample-rate conversion happens after tone/dynamics/loudness work
     * and before the final inter-sample limiter so the ceiling is enforced on
     * the actual exported sample grid.
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
            val hp = BiquadFilter(
                BiquadType.HIGHPASS,
                AudioConstants.HIGHPASS_FREQ,
                sr,
                q = 0.707
            )
            current = hp.processBuffer(current)
        }
        onProgress?.invoke(0.3f)

        // 3. 5-Band Equalizer
        if (settings.eqLow != 0.0f) {
            current = BiquadFilter(
                BiquadType.LOWSHELF,
                AudioConstants.FREQ_LOW,
                sr,
                gainDb = settings.eqLow.toDouble()
            ).processBuffer(current)
        }
        if (settings.eqLowMid != 0.0f) {
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_LOW_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqLowMid.toDouble()
            ).processBuffer(current)
        }
        if (settings.eqMid != 0.0f) {
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqMid.toDouble()
            ).processBuffer(current)
        }
        if (settings.eqHighMid != 0.0f) {
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_HIGH_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqHighMid.toDouble()
            ).processBuffer(current)
        }
        if (settings.eqHigh != 0.0f) {
            current = BiquadFilter(
                BiquadType.HIGHSHELF,
                AudioConstants.FREQ_HIGH,
                sr,
                gainDb = settings.eqHigh.toDouble()
            ).processBuffer(current)
        }
        onProgress?.invoke(0.45f)

        // 4. Character Filters: Cut Mud, Add Air, Tame Harsh
        if (settings.cutMud) {
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.MUD_CUT_FREQ,
                sr,
                q = 1.5,
                gainDb = -3.0
            ).processBuffer(current)
        }
        if (settings.addAir) {
            current = BiquadFilter(
                BiquadType.HIGHSHELF,
                AudioConstants.AIR_FREQ,
                sr,
                gainDb = 2.5
            ).processBuffer(current)
        }
        if (settings.tameHarsh) {
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.HARSHNESS_FREQ_1,
                sr,
                q = AudioConstants.HARSHNESS_Q_4K,
                gainDb = AudioConstants.HARSHNESS_GAIN_4K
            ).processBuffer(current)
            current = BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.HARSHNESS_FREQ_2,
                sr,
                q = AudioConstants.HARSHNESS_Q_6K,
                gainDb = AudioConstants.HARSHNESS_GAIN_6K
            ).processBuffer(current)
        }
        onProgress?.invoke(0.6f)

        // 5. Glue Compressor
        if (settings.glueCompression) {
            current = DynamicsProcessor(current.sampleRate).processGlueCompression(current)
        }
        onProgress?.invoke(0.7f)

        // 6. Mid-Side Stereo Processing & Center Bass
        current = StereoProcessor(current.sampleRate).process(
            current,
            settings.stereoWidth,
            settings.centerBass
        )
        onProgress?.invoke(0.8f)

        // 7. Loudness Normalization
        if (settings.normalizeLoudness) {
            val normGain = precalculatedLufsGain ?: run {
                LufsMeter.measure(current, settings.targetLufs).normalizationGain
            }
            for (c in 0 until current.channels) {
                val data = current.getChannel(c)
                for (i in 0 until current.length) {
                    data[i] *= normGain
                }
            }
        }
        onProgress?.invoke(0.88f)

        // 8. Export sample-rate conversion. This control now changes the bytes
        // that are written instead of being UI-only state.
        if (settings.sampleRate != current.sampleRate) {
            current = AudioResampler.resample(current, settings.sampleRate)
        }
        onProgress?.invoke(0.94f)

        // 9. Final inter-sample ceiling at the exported sample rate.
        if (settings.truePeakLimit) {
            current = DynamicsProcessor(current.sampleRate)
                .processLimiter(current, settings.truePeakCeiling)
        }

        onProgress?.invoke(1.0f)
        return current
    }
}
