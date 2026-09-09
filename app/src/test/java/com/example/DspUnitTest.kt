package com.example

import com.example.dsp.AudioBuffer
import com.example.dsp.AudioConstants
import com.example.dsp.AudioMasteringEngine
import com.example.dsp.BiquadFilter
import com.example.dsp.BiquadType
import com.example.dsp.DynamicsProcessor
import com.example.dsp.LufsMeter
import com.example.dsp.SampleAudioGenerator
import com.example.dsp.StereoProcessor
import com.example.dsp.WavEncoder
import com.example.model.AudioMetadata
import com.example.model.MasteringSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspUnitTest {

    @Test
    fun testAudioBufferCreationAndSlice() {
        val buffer = AudioBuffer(channels = 2, length = 1000, sampleRate = 44100)
        assertEquals(2, buffer.channels)
        assertEquals(1000, buffer.length)
        assertEquals(44100, buffer.sampleRate)

        val left = buffer.getChannel(0)
        left[100] = 0.5f
        left[200] = -0.8f

        assertEquals(0.8f, buffer.peak(), 0.001f)

        val slice = buffer.slice(50, 150)
        assertEquals(100, slice.length)
        assertEquals(0.5f, slice.getChannel(0)[50], 0.001f)
    }

    @Test
    fun testBiquadFilterProcessing() {
        val buffer = AudioBuffer(2, 44100, 44100)
        // Fill with 100 Hz sine wave
        val left = buffer.getChannel(0)
        for (i in 0 until 44100) {
            left[i] = kotlin.math.sin(2.0 * Math.PI * 100.0 * i / 44100.0).toFloat()
        }

        // Apply highpass at 1000 Hz
        val hp = BiquadFilter(BiquadType.HIGHPASS, 1000.0, 44100.0)
        val filtered = hp.processBuffer(buffer)

        // 100Hz should be significantly attenuated by 1000Hz HP filter
        val peakBefore = buffer.peak()
        val peakAfter = filtered.peak()
        assertTrue(peakAfter < peakBefore * 0.2f)
    }

    @Test
    fun testStereoProcessorCenterBass() {
        val buffer = AudioBuffer(2, 44100, 44100)
        val left = buffer.getChannel(0)
        val right = buffer.getChannel(1)

        // Put out-of-phase 60 Hz bass in L & R (Side only)
        for (i in 0 until 44100) {
            val s = kotlin.math.sin(2.0 * Math.PI * 60.0 * i / 44100.0).toFloat()
            left[i] = s
            right[i] = -s
        }

        val stereoProc = StereoProcessor(44100)
        // centerBass = true should filter out sub-120Hz out-of-phase side signal
        val processed = stereoProc.process(buffer, stereoWidthPercent = 100, centerBass = true)
        assertTrue(processed.peak() < 0.25f)
    }

    @Test
    fun testLufsMeterAndNormalization() {
        val sample = SampleAudioGenerator.generateSample("synthwave")
        val result = LufsMeter.measure(sample, targetLufs = -14)

        assertNotNull(result)
        assertTrue("LUFS should be measured accurately", result.integratedLufs > -50.0f)
        assertTrue("Gain should be a valid positive float", result.normalizationGain > 0f)
    }

    @Test
    fun testOfflineMasteringPipelineAndWavEncoding() {
        val input = SampleAudioGenerator.generateSample("synthwave")
        val settings = MasteringSettings(
            cleanLowEnd = true,
            eqLow = 1.0f,
            eqHigh = 1.5f,
            cutMud = true,
            addAir = true,
            glueCompression = true,
            centerBass = true,
            normalizeLoudness = true,
            targetLufs = -14,
            truePeakLimit = true,
            truePeakCeiling = -1.0f,
            bitDepth = 16
        )

        val mastered = AudioMasteringEngine.processOffline(input, settings)
        assertEquals(input.length, mastered.length)
        assertEquals(input.channels, mastered.channels)

        // Peak must not exceed ceiling
        val maxPeak = mastered.peak()
        assertTrue("Max peak $maxPeak should respect ceiling", maxPeak <= 1.05f)

        // Test WAV encoding with metadata
        val metadata = AudioMetadata(title = "Test Anthem", artist = "AI Studio", album = "Demo")
        val wavBytes = WavEncoder.encode(mastered, bitDepth = 16, applyDither = true, metadata = metadata)

        assertTrue(wavBytes.isNotEmpty())
        assertEquals('R'.code.toByte(), wavBytes[0])
        assertEquals('I'.code.toByte(), wavBytes[1])
        assertEquals('F'.code.toByte(), wavBytes[2])
        assertEquals('F'.code.toByte(), wavBytes[3])
    }
}
