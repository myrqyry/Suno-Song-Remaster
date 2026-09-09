package com.example.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.dsp.AudioBuffer
import com.example.dsp.AudioConstants
import com.example.dsp.BiquadFilter
import com.example.dsp.BiquadType
import com.example.dsp.StereoProcessor
import com.example.model.MasteringSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class MeterData(
    val leftDb: Float = -60f,
    val rightDb: Float = -60f,
    val leftPeakDb: Float = -60f,
    val rightPeakDb: Float = -60f,
    val leftClip: Boolean = false,
    val rightClip: Boolean = false,
    val spectrumBands: FloatArray = FloatArray(16)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeterData) return false
        return leftDb == other.leftDb && rightDb == other.rightDb
    }

    override fun hashCode(): Int {
        var result = leftDb.hashCode()
        result = 31 * result + rightDb.hashCode()
        return result
    }
}

class MasteringPlayer(private val coroutineScope: CoroutineScope) {

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    var currentBuffer: AudioBuffer? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0.0)
    val currentPositionSec: StateFlow<Double> = _currentPositionSec.asStateFlow()

    private val _durationSec = MutableStateFlow(0.0)
    val durationSec: StateFlow<Double> = _durationSec.asStateFlow()

    private val _isBypass = MutableStateFlow(false)
    val isBypass: StateFlow<Boolean> = _isBypass.asStateFlow()

    private val _meterData = MutableStateFlow(MeterData())
    val meterData: StateFlow<MeterData> = _meterData.asStateFlow()

    var settings: MasteringSettings = MasteringSettings()
    var normGain: Float = 1.0f

    // Loop points in seconds
    var loopEnabled: Boolean = false
    var loopStartSec: Double = 0.0
    var loopEndSec: Double = 0.0

    @Volatile
    private var currentSamplePosition = 0

    // Peak holds
    private var leftPeakHold = -60f
    private var rightPeakHold = -60f
    private var lastPeakDecayTime = System.currentTimeMillis()

    fun loadBuffer(buffer: AudioBuffer) {
        stop()
        currentBuffer = buffer
        _durationSec.value = buffer.durationSeconds
        _currentPositionSec.value = 0.0
        currentSamplePosition = 0
        initTrack(buffer.sampleRate)
    }

    private fun initTrack(sampleRate: Int) {
        audioTrack?.release()
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize * 2, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun play() {
        val buffer = currentBuffer ?: return
        if (_isPlaying.value) return

        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initTrack(buffer.sampleRate)
        }

        try {
            audioTrack?.play()
        } catch (_: Exception) {
            initTrack(buffer.sampleRate)
            audioTrack?.play()
        }

        _isPlaying.value = true

        playbackJob = coroutineScope.launch(Dispatchers.Default) {
            runPlaybackLoop()
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
        _meterData.value = MeterData()
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (_: Exception) {}
        currentSamplePosition = 0
        _currentPositionSec.value = 0.0
        _meterData.value = MeterData()
    }

    fun seekTo(seconds: Double) {
        val buffer = currentBuffer ?: return
        val clampedSec = seconds.coerceIn(0.0, buffer.durationSeconds)
        currentSamplePosition = (clampedSec * buffer.sampleRate).toInt().coerceIn(0, buffer.length)
        _currentPositionSec.value = clampedSec
    }

    fun toggleBypass() {
        _isBypass.value = !_isBypass.value
    }

    fun setBypass(bypass: Boolean) {
        _isBypass.value = bypass
    }

    private fun runPlaybackLoop() {
        val buffer = currentBuffer ?: return
        val sampleRate = buffer.sampleRate
        val chunkSize = 1024
        val shortBuffer = ShortArray(chunkSize * 2)

        // Local filters for streaming
        val srD = sampleRate.toDouble()
        val hpFilter = BiquadFilter(BiquadType.HIGHPASS, AudioConstants.HIGHPASS_FREQ, srD, q = 0.707)
        val eqLowFilter = BiquadFilter(BiquadType.LOWSHELF, AudioConstants.FREQ_LOW, srD)
        val eqLowMidFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_LOW_MID, srD, q = 1.0)
        val eqMidFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_MID, srD, q = 1.0)
        val eqHighMidFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.FREQ_HIGH_MID, srD, q = 1.0)
        val eqHighFilter = BiquadFilter(BiquadType.HIGHSHELF, AudioConstants.FREQ_HIGH, srD)
        val mudFilter = BiquadFilter(BiquadType.PEAKING, AudioConstants.MUD_CUT_FREQ, srD, q = 1.5, gainDb = -3.0)
        val airFilter = BiquadFilter(BiquadType.HIGHSHELF, AudioConstants.AIR_FREQ, srD, gainDb = 2.5)
        val harsh1 = BiquadFilter(BiquadType.PEAKING, AudioConstants.HARSHNESS_FREQ_1, srD, q = AudioConstants.HARSHNESS_Q_4K, gainDb = AudioConstants.HARSHNESS_GAIN_4K)
        val harsh2 = BiquadFilter(BiquadType.PEAKING, AudioConstants.HARSHNESS_FREQ_2, srD, q = AudioConstants.HARSHNESS_Q_6K, gainDb = AudioConstants.HARSHNESS_GAIN_6K)
        val sideHp = BiquadFilter(BiquadType.HIGHPASS, AudioConstants.BASS_MONO_FREQ, srD, q = 0.707)

        while (coroutineScope.isActive && _isPlaying.value) {
            val totalFrames = buffer.length
            if (currentSamplePosition >= totalFrames) {
                if (loopEnabled) {
                    val loopStartSample = (loopStartSec * sampleRate).toInt().coerceIn(0, totalFrames)
                    currentSamplePosition = loopStartSample
                } else {
                    stop()
                    break
                }
            }

            if (loopEnabled && loopEndSec > loopStartSec) {
                val endSample = (loopEndSec * sampleRate).toInt()
                if (currentSamplePosition >= endSample) {
                    currentSamplePosition = (loopStartSec * sampleRate).toInt().coerceIn(0, totalFrames)
                }
            }

            val framesToRead = (totalFrames - currentSamplePosition).coerceAtMost(chunkSize)
            if (framesToRead <= 0) break

            val currentSettings = settings
            val bypass = _isBypass.value

            // Update real-time filter params
            eqLowFilter.update(gainDb = currentSettings.eqLow.toDouble())
            eqLowMidFilter.update(gainDb = currentSettings.eqLowMid.toDouble())
            eqMidFilter.update(gainDb = currentSettings.eqMid.toDouble())
            eqHighMidFilter.update(gainDb = currentSettings.eqHighMid.toDouble())
            eqHighFilter.update(gainDb = currentSettings.eqHigh.toDouble())

            val linearInputGain = 10.0.pow(currentSettings.inputGain.toDouble() / 20.0).toFloat()
            val ceilingLinear = 10.0.pow(currentSettings.truePeakCeiling.toDouble() / 20.0).toFloat()
            val stereoWidthFactor = currentSettings.stereoWidth / 100f

            val leftChannel = buffer.getChannel(0)
            val rightChannel = if (buffer.channels > 1) buffer.getChannel(1) else leftChannel

            var sumSqL = 0.0
            var sumSqR = 0.0
            var peakL = 0f
            var peakR = 0f

            val spectrumBins = FloatArray(16)

            for (i in 0 until framesToRead) {
                val sampleIdx = currentSamplePosition + i
                var l = leftChannel[sampleIdx]
                var r = rightChannel[sampleIdx]

                if (!bypass) {
                    // 1. Input gain
                    l *= linearInputGain
                    r *= linearInputGain

                    // 2. Highpass
                    if (currentSettings.cleanLowEnd) {
                        l = hpFilter.processSample(l, 0)
                        r = hpFilter.processSample(r, 1)
                    }

                    // 3. EQ
                    if (currentSettings.eqLow != 0f) {
                        l = eqLowFilter.processSample(l, 0)
                        r = eqLowFilter.processSample(r, 1)
                    }
                    if (currentSettings.eqLowMid != 0f) {
                        l = eqLowMidFilter.processSample(l, 0)
                        r = eqLowMidFilter.processSample(r, 1)
                    }
                    if (currentSettings.eqMid != 0f) {
                        l = eqMidFilter.processSample(l, 0)
                        r = eqMidFilter.processSample(r, 1)
                    }
                    if (currentSettings.eqHighMid != 0f) {
                        l = eqHighMidFilter.processSample(l, 0)
                        r = eqHighMidFilter.processSample(r, 1)
                    }
                    if (currentSettings.eqHigh != 0f) {
                        l = eqHighFilter.processSample(l, 0)
                        r = eqHighFilter.processSample(r, 1)
                    }

                    // 4. Character Filters
                    if (currentSettings.cutMud) {
                        l = mudFilter.processSample(l, 0)
                        r = mudFilter.processSample(r, 1)
                    }
                    if (currentSettings.addAir) {
                        l = airFilter.processSample(l, 0)
                        r = airFilter.processSample(r, 1)
                    }
                    if (currentSettings.tameHarsh) {
                        l = harsh1.processSample(l, 0)
                        r = harsh1.processSample(r, 1)
                        l = harsh2.processSample(l, 0)
                        r = harsh2.processSample(r, 1)
                    }

                    // 5. Mid/Side & Center Bass
                    val mid = (l + r) * 0.5f
                    var side = (l - r) * 0.5f
                    if (currentSettings.centerBass) {
                        side = sideHp.processSample(side, 0)
                    }
                    side *= stereoWidthFactor
                    l = mid + side
                    r = mid - side

                    // 6. Normalization
                    if (currentSettings.normalizeLoudness) {
                        l *= normGain
                        r *= normGain
                    }

                    // 7. Ceiling
                    if (currentSettings.truePeakLimit) {
                        l = l.coerceIn(-ceilingLinear, ceilingLinear)
                        r = r.coerceIn(-ceilingLinear, ceilingLinear)
                    }
                }

                // Level meter metrics
                val absL = abs(l)
                val absR = abs(r)
                sumSqL += absL * absL
                sumSqR += absR * absR
                if (absL > peakL) peakL = absL
                if (absR > peakR) peakR = absR

                // Spectrum bin distribution estimation
                val binIdx = (i % 16)
                spectrumBins[binIdx] += (absL + absR) * 0.5f

                // Convert to 16-bit PCM
                val shortL = (l.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                val shortR = (r.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                shortBuffer[i * 2] = shortL
                shortBuffer[i * 2 + 1] = shortR
            }

            // Write to AudioTrack
            audioTrack?.write(shortBuffer, 0, framesToRead * 2)

            currentSamplePosition += framesToRead
            _currentPositionSec.value = currentSamplePosition.toDouble() / sampleRate

            // Calculate RMS dBFS
            val rmsL = sqrt(sumSqL / framesToRead).toFloat()
            val rmsR = sqrt(sumSqR / framesToRead).toFloat()
            val dbL = if (rmsL > 1e-4f) (20f * log10(rmsL)).coerceIn(-60f, 3f) else -60f
            val dbR = if (rmsR > 1e-4f) (20f * log10(rmsR)).coerceIn(-60f, 3f) else -60f
            val peakDbL = if (peakL > 1e-4f) (20f * log10(peakL)).coerceIn(-60f, 3f) else -60f
            val peakDbR = if (peakR > 1e-4f) (20f * log10(peakR)).coerceIn(-60f, 3f) else -60f

            // Peak hold decay
            val now = System.currentTimeMillis()
            if (now - lastPeakDecayTime > 80) {
                leftPeakHold = max(peakDbL, leftPeakHold - 1.5f)
                rightPeakHold = max(peakDbR, rightPeakHold - 1.5f)
                lastPeakDecayTime = now
            } else {
                leftPeakHold = max(peakDbL, leftPeakHold)
                rightPeakHold = max(peakDbR, rightPeakHold)
            }

            // Normalize spectrum bins
            val normSpectrum = FloatArray(16) { idx ->
                (spectrumBins[idx] / (framesToRead / 16f) * 2.5f).coerceIn(0f, 1f)
            }

            _meterData.value = MeterData(
                leftDb = dbL,
                rightDb = dbR,
                leftPeakDb = leftPeakHold,
                rightPeakDb = rightPeakHold,
                leftClip = peakL >= 0.999f,
                rightClip = peakR >= 0.999f,
                spectrumBands = normSpectrum
            )
        }

        _isPlaying.value = false
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
