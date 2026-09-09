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
        val width = (stereoWidthPercent / 100f).coerceIn(0f, 2f)

        // If mono, expand to stereo
        val leftSrc: FloatArray
        val rightSrc: FloatArray
        if (input.channels == 1) {
            leftSrc = input.getChannel(0)
            rightSrc = input.getChannel(0)
        } else {
            leftSrc = input.getChannel(0)
            rightSrc = input.getChannel(1)
        }

        val out = AudioBuffer(2, input.length, input.sampleRate)
        val outL = out.getChannel(0)
        val outR = out.getChannel(1)

        sideHighpass.update(sampleRate = input.sampleRate.toDouble())
        sideHighpass.reset()

        for (i in 0 until input.length) {
            val l = leftSrc[i]
            val r = rightSrc[i]

            val mid = (l + r) * 0.5f
            var side = (l - r) * 0.5f

            if (centerBass) {
                side = sideHighpass.processSample(side, 0)
            }

            side *= width

            outL[i] = mid + side
            outR[i] = mid - side
        }

        return out
    }
}
