package com.example

import com.example.dsp.AudioBuffer
import com.example.dsp.AudioMasteringEngine
import com.example.dsp.AudioResampler
import com.example.dsp.BiquadFilter
import com.example.dsp.BiquadType
import com.example.dsp.DynamicsProcessor
import com.example.dsp.LufsMeter
import com.example.dsp.SampleAudioGenerator
import com.example.dsp.SpectrumAnalyzer
import com.example.dsp.StereoProcessor
import com.example.dsp.TruePeakEstimator
import com.example.dsp.WavEncoder
import com.example.model.AudioMetadata
import com.example.model.MasteringSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

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
        val left = buffer.getChannel(0)
        for (i in left.indices) {
            left[i] = sin(2.0 * PI * 100.0 * i / 44100.0).toFloat()
        }

        val hp = BiquadFilter(BiquadType.HIGHPASS, 1000.0, 44100.0)
        val filtered = hp.processBuffer(buffer)
        assertTrue(filtered.peak() < buffer.peak() * 0.2f)
    }

    @Test
    fun testStereoProcessorCenterBass() {
        val buffer = AudioBuffer(2, 44100, 44100)
        val left = buffer.getChannel(0)
        val right = buffer.getChannel(1)

        for (i in left.indices) {
            val sample = sin(2.0 * PI * 60.0 * i / 44100.0).toFloat()
            left[i] = sample
            right[i] = -sample
        }

        val processed = StereoProcessor(44100).process(
            buffer,
            stereoWidthPercent = 100,
            centerBass = true
        )
        assertTrue(processed.peak() < 0.25f)
    }

    @Test
    fun testLufsMeterReturnsFiniteUsefulValues() {
        val sample = SampleAudioGenerator.generateSample("synthwave")
        val result = LufsMeter.measure(sample, targetLufs = -14)

        assertNotNull(result)
        assertTrue(result.integratedLufs.isFinite())
        assertTrue(result.integratedLufs in -50.0..0.0)
        assertTrue(result.normalizationGain > 0f)
        assertTrue(result.truePeakDb >= result.samplePeakDb - 0.01)
    }

    @Test
    fun testInterSampleEstimatorActuallyLooksBetweenSamples() {
        val buffer = AudioBuffer(1, 4, 44100)
        val data = buffer.getChannel(0)
        data[0] = -0.8f
        data[1] = 0.8f
        data[2] = 0.8f
        data[3] = -0.8f

        val samplePeak = buffer.peak().toDouble()
        val interSamplePeak = TruePeakEstimator.linearPeak(buffer)

        assertTrue("inter-sample peak should exceed sample peak", interSamplePeak > samplePeak + 0.1)
        assertTrue("expected cubic overshoot near full scale", interSamplePeak > 0.95)
    }

    @Test
    fun testLimiterEnforcesMeasuredInterSampleCeiling() {
        val buffer = AudioBuffer(1, 400, 44100)
        val pattern = floatArrayOf(-0.8f, 0.8f, 0.8f, -0.8f)
        val data = buffer.getChannel(0)
        for (i in data.indices) data[i] = pattern[i % pattern.size]

        val ceilingDb = -1.0f
        val ceilingLinear = 10.0.pow(ceilingDb / 20.0)
        val limited = DynamicsProcessor(44100).processLimiter(buffer, ceilingDb)
        val measured = TruePeakEstimator.linearPeak(limited)

        assertTrue("4x inter-sample peak $measured exceeds $ceilingLinear", measured <= ceilingLinear + 1e-4)
    }

    @Test
    fun testExportResamplerChangesRateAndPreservesDuration() {
        val sourceRate = 48000
        val targetRate = 44100
        val buffer = AudioBuffer(1, sourceRate, sourceRate)
        val data = buffer.getChannel(0)
        for (i in data.indices) {
            data[i] = (0.5 * sin(2.0 * PI * 440.0 * i / sourceRate)).toFloat()
        }

        val resampled = AudioResampler.resample(buffer, targetRate)
        assertEquals(targetRate, resampled.sampleRate)
        assertEquals(targetRate, resampled.length)
        assertEquals(buffer.durationSeconds, resampled.durationSeconds, 1e-3)
        assertTrue(resampled.peak() > 0.3f)
    }

    @Test
    fun testSpectrumBandsRespondToFrequency() {
        fun tone(frequency: Double): FloatArray {
            return FloatArray(1024) { i ->
                sin(2.0 * PI * frequency * i / 44100.0).toFloat()
            }
        }

        val low = SpectrumAnalyzer.analyzeMono(tone(100.0), 44100)
        val high = SpectrumAnalyzer.analyzeMono(tone(8000.0), 44100)
        val lowMax = low.indices.maxByOrNull { low[it] } ?: -1
        val highMax = high.indices.maxByOrNull { high[it] } ?: -1

        assertTrue("100 Hz should land in lower bands, got $lowMax", lowMax in 0..5)
        assertTrue("8 kHz should land in upper bands, got $highMax", highMax in 10..15)
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
            sampleRate = input.sampleRate,
            bitDepth = 16
        )

        val mastered = AudioMasteringEngine.processOffline(input, settings)
        assertEquals(input.length, mastered.length)
        assertEquals(input.channels, mastered.channels)
        assertEquals(input.sampleRate, mastered.sampleRate)

        val ceilingLinear = 10.0.pow(settings.truePeakCeiling / 20.0)
        val interSamplePeak = TruePeakEstimator.linearPeak(mastered)
        assertTrue(
            "Inter-sample peak $interSamplePeak should respect ceiling $ceilingLinear",
            interSamplePeak <= ceilingLinear + 1e-4
        )

        val metadata = AudioMetadata(title = "Test Anthem", artist = "Studio", album = "Demo")
        val wavBytes = WavEncoder.encode(
            mastered,
            bitDepth = 16,
            applyDither = true,
            metadata = metadata
        )

        assertTrue(wavBytes.isNotEmpty())
        assertEquals('R'.code.toByte(), wavBytes[0])
        assertEquals('I'.code.toByte(), wavBytes[1])
        assertEquals('F'.code.toByte(), wavBytes[2])
        assertEquals('F'.code.toByte(), wavBytes[3])
    }
}
