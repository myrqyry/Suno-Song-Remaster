package com.example.dsp

import com.example.model.MasteringSettings
import kotlin.math.pow

object AudioMasteringEngine {

    /**
     * Runs every stage that occurs before loudness normalization.
     *
     * Only one full-track working copy is created. Tone, dynamics and stereo
     * stages then mutate that copy in place so loading a normal-length song
     * does not transiently allocate several complete PCM buffers at once.
     */
    fun processBeforeNormalization(
        inputBuffer: AudioBuffer,
        settings: MasteringSettings,
        onProgress: ((Float) -> Unit)? = null
    ): AudioBuffer {
        val sr = inputBuffer.sampleRate.toDouble()
        var current = inputBuffer.copy()

        // 1. Input Gain
        if (settings.inputGain != 0.0f) {
            val linearGain = 10.0.pow(settings.inputGain.toDouble() / 20.0).toFloat()
            for (c in 0 until current.channels) {
                val data = current.getChannel(c)
                for (i in 0 until current.length) data[i] *= linearGain
            }
        }
        onProgress?.invoke(0.15f)

        // 2. Clean Low End (30 Hz high-pass)
        if (settings.cleanLowEnd) {
            BiquadFilter(
                BiquadType.HIGHPASS,
                AudioConstants.HIGHPASS_FREQ,
                sr,
                q = 0.707
            ).processBufferInPlace(current)
        }
        onProgress?.invoke(0.3f)

        // 3. 5-band EQ
        if (settings.eqLow != 0.0f) {
            BiquadFilter(
                BiquadType.LOWSHELF,
                AudioConstants.FREQ_LOW,
                sr,
                gainDb = settings.eqLow.toDouble()
            ).processBufferInPlace(current)
        }
        if (settings.eqLowMid != 0.0f) {
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_LOW_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqLowMid.toDouble()
            ).processBufferInPlace(current)
        }
        if (settings.eqMid != 0.0f) {
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqMid.toDouble()
            ).processBufferInPlace(current)
        }
        if (settings.eqHighMid != 0.0f) {
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.FREQ_HIGH_MID,
                sr,
                q = 1.0,
                gainDb = settings.eqHighMid.toDouble()
            ).processBufferInPlace(current)
        }
        if (settings.eqHigh != 0.0f) {
            BiquadFilter(
                BiquadType.HIGHSHELF,
                AudioConstants.FREQ_HIGH,
                sr,
                gainDb = settings.eqHigh.toDouble()
            ).processBufferInPlace(current)
        }
        onProgress?.invoke(0.5f)

        // 4. Character filters
        if (settings.cutMud) {
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.MUD_CUT_FREQ,
                sr,
                q = 1.5,
                gainDb = -3.0
            ).processBufferInPlace(current)
        }
        if (settings.addAir) {
            BiquadFilter(
                BiquadType.HIGHSHELF,
                AudioConstants.AIR_FREQ,
                sr,
                gainDb = 2.5
            ).processBufferInPlace(current)
        }
        if (settings.tameHarsh) {
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.HARSHNESS_FREQ_1,
                sr,
                q = AudioConstants.HARSHNESS_Q_4K,
                gainDb = AudioConstants.HARSHNESS_GAIN_4K
            ).processBufferInPlace(current)
            BiquadFilter(
                BiquadType.PEAKING,
                AudioConstants.HARSHNESS_FREQ_2,
                sr,
                q = AudioConstants.HARSHNESS_Q_6K,
                gainDb = AudioConstants.HARSHNESS_GAIN_6K
            ).processBufferInPlace(current)
        }
        onProgress?.invoke(0.65f)

        // 5. Glue compressor
        if (settings.glueCompression) {
            DynamicsProcessor(current.sampleRate).processGlueCompressionInPlace(current)
        }
        onProgress?.invoke(0.8f)

        // 6. Mid/side stereo processing & center bass. Stereo input is mutated
        // in place; mono input necessarily expands to a new stereo buffer.
        current = StereoProcessor(current.sampleRate).processInPlace(
            current,
            settings.stereoWidth,
            settings.centerBass
        )
        onProgress?.invoke(1.0f)

        return current
    }

    /**
     * Executes the complete offline mastering pipeline.
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
        var current = processBeforeNormalization(inputBuffer, settings) { preProgress ->
            onProgress?.invoke(preProgress * 0.72f)
        }

        // 7. Loudness normalization
        if (settings.normalizeLoudness) {
            val normGain = precalculatedLufsGain ?: LufsMeter
                .measure(current, settings.targetLufs)
                .normalizationGain
            for (c in 0 until current.channels) {
                val data = current.getChannel(c)
                for (i in 0 until current.length) data[i] *= normGain
            }
        }
        onProgress?.invoke(0.82f)

        // 8. Export sample-rate conversion. The output-format control changes
        // the actual AudioBuffer rate and frame count.
        if (settings.sampleRate != current.sampleRate) {
            current = AudioResampler.resample(current, settings.sampleRate)
        }
        onProgress?.invoke(0.92f)

        // 9. Final inter-sample ceiling at the exported sample rate.
        if (settings.truePeakLimit) {
            current = DynamicsProcessor(current.sampleRate)
                .processLimiter(current, settings.truePeakCeiling)
        }

        onProgress?.invoke(1.0f)
        return current
    }
}
