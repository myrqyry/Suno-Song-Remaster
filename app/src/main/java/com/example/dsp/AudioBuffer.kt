package com.example.dsp

import kotlin.math.abs
import kotlin.math.max

class AudioBuffer(
    val channels: Int,
    val length: Int,
    val sampleRate: Int,
    val channelData: Array<FloatArray>
) {
    constructor(channels: Int, length: Int, sampleRate: Int) : this(
        channels,
        length,
        sampleRate,
        Array(channels) { FloatArray(length) }
    )

    val durationSeconds: Double
        get() = if (sampleRate > 0) length.toDouble() / sampleRate else 0.0

    fun getChannel(ch: Int): FloatArray {
        return channelData[ch]
    }

    fun copy(): AudioBuffer {
        val newCopy = AudioBuffer(channels, length, sampleRate)
        for (c in 0 until channels) {
            System.arraycopy(channelData[c], 0, newCopy.channelData[c], 0, length)
        }
        return newCopy
    }

    fun slice(startSample: Int, endSample: Int): AudioBuffer {
        val s = max(0, startSample)
        val e = length.coerceAtMost(max(s, endSample))
        val newLen = e - s
        val sliced = AudioBuffer(channels, newLen, sampleRate)
        for (c in 0 until channels) {
            System.arraycopy(channelData[c], s, sliced.channelData[c], 0, newLen)
        }
        return sliced
    }

    fun peak(): Float {
        var maxVal = 0f
        for (c in 0 until channels) {
            val data = channelData[c]
            for (i in 0 until length) {
                val a = abs(data[i])
                if (a > maxVal) maxVal = a
            }
        }
        return maxVal
    }

    fun interleave(): FloatArray {
        val out = FloatArray(length * channels)
        for (i in 0 until length) {
            for (c in 0 until channels) {
                out[i * channels + c] = channelData[c][i]
            }
        }
        return out
    }
}
