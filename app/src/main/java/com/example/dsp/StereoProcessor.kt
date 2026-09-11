package com.example.dsp

class StereoProcessor(private val sampleRate: Int) {
    private val sideHighpass = BiquadFilter(
        type = BiquadType.HIGHPASS,
        frequency = AudioConstants.BASS_MONO_FREQ,
        sampleRate = sampleRate.toDouble(),
        q = 0.707
    )

    fun process(
        input: AudioBuffer,
        stereoWidthPercent: Int,
        centerBass: Boolean
    ): AudioBuffer {
        val working = input.copy()
        return processInPlace(working, stereoWidthPercent, centerBass)
    }

    /**
     * Mutates stereo input directly. Mono input necessarily expands to a new
     * stereo buffer because AudioBuffer channel count is fixed at construction.
     */
    fun processInPlace(
        input: AudioBuffer,
        stereoWidthPercent: Int,
        centerBass: Boolean
    ): AudioBuffer {
        val width = (stereoWidthPercent / 100f).coerceIn(0f, 2f)

        sideHighpass.update(sampleRate = input.sampleRate.toDouble())
        sideHighpass.reset()

        if (input.channels == 1) {
            val source = input.getChannel(0)
            val out = AudioBuffer(2, input.length, input.sampleRate)
            val outL = out.getChannel(0)
            val outR = out.getChannel(1)

            for (i in 0 until input.length) {
                val mid = source[i]
                // Mono has no side content. Width/center-bass therefore leave
                // both expanded channels identical.
                outL[i] = mid
                outR[i] = mid
            }
            return out
        }

        val left = input.getChannel(0)
        val right = input.getChannel(1)

        for (i in 0 until input.length) {
            val l = left[i]
            val r = right[i]
            val mid = (l + r) * 0.5f
            var side = (l - r) * 0.5f

            if (centerBass) {
                side = sideHighpass.processSample(side, 0)
            }

            side *= width
            left[i] = mid + side
            right[i] = mid - side
        }

        return input
    }
}
