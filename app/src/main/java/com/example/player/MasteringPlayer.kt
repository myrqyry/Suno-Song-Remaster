package com.example.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.dsp.AudioBuffer
import com.example.dsp.AudioConstants
import com.example.dsp.BiquadFilter
import com.example.dsp.BiquadType
import com.example.dsp.SpectrumAnalyzer
import com.example.dsp.StreamingGlueCompressor
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
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
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
        return leftDb == other.leftDb &&
            rightDb == other.rightDb &&
            leftPeakDb == other.leftPeakDb &&
            rightPeakDb == other.rightPeakDb &&
            leftClip == other.leftClip &&
            rightClip == other.rightClip &&
            spectrumBands.contentEquals(other.spectrumBands)
    }

    override fun hashCode(): Int {
        var result = leftDb.hashCode()
        result = 31 * result + rightDb.hashCode()
        result = 31 * result + leftPeakDb.hashCode()
        result = 31 * result + rightPeakDb.hashCode()
        result = 31 * result + leftClip.hashCode()
        result = 31 * result + rightClip.hashCode()
        result = 31 * result + spectrumBands.contentHashCode()
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

    var loopEnabled: Boolean = false
    var loopStartSec: Double = 0.0
    var loopEndSec: Double = 0.0

    @Volatile
    private var currentSamplePosition = 0

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

    /**
     * Drops references to the current PCM track and native AudioTrack. This is
     * used before decoding a replacement file so the old demo/song does not
     * compete with the incoming decoded PCM for heap space.
     */
    fun unloadBuffer() {
        stop()
        currentBuffer = null
        _durationSec.value = 0.0
        loopStartSec = 0.0
        loopEndSec = 0.0
        audioTrack?.release()
        audioTrack = null
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
        } catch (_: Exception) {
        }
        _meterData.value = MeterData()
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (_: Exception) {
        }
        currentSamplePosition = 0
        _currentPositionSec.value = 0.0
        _meterData.value = MeterData()
    }

    fun seekTo(seconds: Double) {
        val buffer = currentBuffer ?: return
        val clampedSec = seconds.coerceIn(0.0, buffer.durationSeconds)
        currentSamplePosition = (clampedSec * buffer.sampleRate)
            .toInt()
            .coerceIn(0, buffer.length)
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

        val srD = sampleRate.toDouble()
        val hpFilter = BiquadFilter(
            BiquadType.HIGHPASS,
            AudioConstants.HIGHPASS_FREQ,
            srD,
            q = 0.707
        )
        val eqLowFilter = BiquadFilter(BiquadType.LOWSHELF, AudioConstants.FREQ_LOW, srD)
        val eqLowMidFilter = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.FREQ_LOW_MID,
            srD,
            q = 1.0
        )
        val eqMidFilter = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.FREQ_MID,
            srD,
            q = 1.0
        )
        val eqHighMidFilter = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.FREQ_HIGH_MID,
            srD,
            q = 1.0
        )
        val eqHighFilter = BiquadFilter(BiquadType.HIGHSHELF, AudioConstants.FREQ_HIGH, srD)
        val mudFilter = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.MUD_CUT_FREQ,
            srD,
            q = 1.5,
            gainDb = -3.0
        )
        val airFilter = BiquadFilter(
            BiquadType.HIGHSHELF,
            AudioConstants.AIR_FREQ,
            srD,
            gainDb = 2.5
        )
        val harsh1 = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.HARSHNESS_FREQ_1,
            srD,
            q = AudioConstants.HARSHNESS_Q_4K,
            gainDb = AudioConstants.HARSHNESS_GAIN_4K
        )
        val harsh2 = BiquadFilter(
            BiquadType.PEAKING,
            AudioConstants.HARSHNESS_FREQ_2,
            srD,
            q = AudioConstants.HARSHNESS_Q_6K,
            gainDb = AudioConstants.HARSHNESS_GAIN_6K
        )
        val sideHp = BiquadFilter(
            BiquadType.HIGHPASS,
            AudioConstants.BASS_MONO_FREQ,
            srD,
            q = 0.707
        )
        val glueCompressor = StreamingGlueCompressor(sampleRate)

        while (coroutineScope.isActive && _isPlaying.value) {
            val totalFrames = buffer.length
            if (currentSamplePosition >= totalFrames) {
                if (loopEnabled) {
                    currentSamplePosition = (loopStartSec * sampleRate)
                        .toInt()
                        .coerceIn(0, totalFrames)
                } else {
                    stop()
                    break
                }
            }

            if (loopEnabled && loopEndSec > loopStartSec) {
                val endSample = (loopEndSec * sampleRate).toInt()
                if (currentSamplePosition >= endSample) {
                    currentSamplePosition = (loopStartSec * sampleRate)
                        .toInt()
                        .coerceIn(0, totalFrames)
                }
            }

            val framesToRead = (totalFrames - currentSamplePosition).coerceAtMost(chunkSize)
            if (framesToRead <= 0) break

            val currentSettings = settings
            val bypass = _isBypass.value

            eqLowFilter.update(gainDb = currentSettings.eqLow.toDouble())
            eqLowMidFilter.update(gainDb = currentSettings.eqLowMid.toDouble())
            eqMidFilter.update(gainDb = currentSettings.eqMid.toDouble())
            eqHighMidFilter.update(gainDb = currentSettings.eqHighMid.toDouble())
            eqHighFilter.update(gainDb = currentSettings.eqHigh.toDouble())

            val linearInputGain = 10.0
                .pow(currentSettings.inputGain.toDouble() / 20.0)
                .toFloat()
            val ceilingLinear = 10.0
                .pow(currentSettings.truePeakCeiling.toDouble() / 20.0)
                .toFloat()
            val stereoWidthFactor = currentSettings.stereoWidth / 100f

            val leftChannel = buffer.getChannel(0)
            val rightChannel = if (buffer.channels > 1) buffer.getChannel(1) else leftChannel

            var sumSqL = 0.0
            var sumSqR = 0.0
            var peakL = 0f
            var peakR = 0f
            val spectrumInput = FloatArray(framesToRead)

            for (i in 0 until framesToRead) {
                val sampleIdx = currentSamplePosition + i
                var left = leftChannel[sampleIdx]
                var right = rightChannel[sampleIdx]

                if (!bypass) {
                    // 1. Input gain
                    left *= linearInputGain
                    right *= linearInputGain

                    // 2. Highpass
                    if (currentSettings.cleanLowEnd) {
                        left = hpFilter.processSample(left, 0)
                        right = hpFilter.processSample(right, 1)
                    }

                    // 3. EQ
                    if (currentSettings.eqLow != 0f) {
                        left = eqLowFilter.processSample(left, 0)
                        right = eqLowFilter.processSample(right, 1)
                    }
                    if (currentSettings.eqLowMid != 0f) {
                        left = eqLowMidFilter.processSample(left, 0)
                        right = eqLowMidFilter.processSample(right, 1)
                    }
                    if (currentSettings.eqMid != 0f) {
                        left = eqMidFilter.processSample(left, 0)
                        right = eqMidFilter.processSample(right, 1)
                    }
                    if (currentSettings.eqHighMid != 0f) {
                        left = eqHighMidFilter.processSample(left, 0)
                        right = eqHighMidFilter.processSample(right, 1)
                    }
                    if (currentSettings.eqHigh != 0f) {
                        left = eqHighFilter.processSample(left, 0)
                        right = eqHighFilter.processSample(right, 1)
                    }

                    // 4. Character filters
                    if (currentSettings.cutMud) {
                        left = mudFilter.processSample(left, 0)
                        right = mudFilter.processSample(right, 1)
                    }
                    if (currentSettings.addAir) {
                        left = airFilter.processSample(left, 0)
                        right = airFilter.processSample(right, 1)
                    }
                    if (currentSettings.tameHarsh) {
                        left = harsh1.processSample(left, 0)
                        right = harsh1.processSample(right, 1)
                        left = harsh2.processSample(left, 0)
                        right = harsh2.processSample(right, 1)
                    }

                    // 5. Glue compressor. This was previously missing in preview.
                    if (currentSettings.glueCompression) {
                        val gain = glueCompressor.gainFor(left, right)
                        left *= gain
                        right *= gain
                    }

                    // 6. Mid/Side & Center Bass
                    val mid = (left + right) * 0.5f
                    var side = (left - right) * 0.5f
                    if (currentSettings.centerBass) {
                        side = sideHp.processSample(side, 0)
                    }
                    side *= stereoWidthFactor
                    left = mid + side
                    right = mid - side

                    // 7. Loudness normalization gain is computed from the same
                    // pre-normalization mastering chain by the ViewModel.
                    if (currentSettings.normalizeLoudness) {
                        left *= normGain
                        right *= normGain
                    }

                    // 8. Realtime preview uses a sample ceiling. Offline export
                    // performs the additional 4x inter-sample safety pass.
                    if (currentSettings.truePeakLimit) {
                        left = left.coerceIn(-ceilingLinear, ceilingLinear)
                        right = right.coerceIn(-ceilingLinear, ceilingLinear)
                    }
                }

                val absL = abs(left)
                val absR = abs(right)
                sumSqL += absL * absL
                sumSqR += absR * absR
                if (absL > peakL) peakL = absL
                if (absR > peakR) peakR = absR
                spectrumInput[i] = (left + right) * 0.5f

                shortBuffer[i * 2] = (left.coerceIn(-1.0f, 1.0f) * 32767f)
                    .toInt()
                    .toShort()
                shortBuffer[i * 2 + 1] = (right.coerceIn(-1.0f, 1.0f) * 32767f)
                    .toInt()
                    .toShort()
            }

            audioTrack?.write(shortBuffer, 0, framesToRead * 2)

            currentSamplePosition += framesToRead
            _currentPositionSec.value = currentSamplePosition.toDouble() / sampleRate

            val rmsL = sqrt(sumSqL / framesToRead).toFloat()
            val rmsR = sqrt(sumSqR / framesToRead).toFloat()
            val dbL = if (rmsL > 1e-4f) {
                (20f * log10(rmsL)).coerceIn(-60f, 3f)
            } else {
                -60f
            }
            val dbR = if (rmsR > 1e-4f) {
                (20f * log10(rmsR)).coerceIn(-60f, 3f)
            } else {
                -60f
            }
            val peakDbL = if (peakL > 1e-4f) {
                (20f * log10(peakL)).coerceIn(-60f, 3f)
            } else {
                -60f
            }
            val peakDbR = if (peakR > 1e-4f) {
                (20f * log10(peakR)).coerceIn(-60f, 3f)
            } else {
                -60f
            }

            val now = System.currentTimeMillis()
            if (now - lastPeakDecayTime > 80) {
                leftPeakHold = max(peakDbL, leftPeakHold - 1.5f)
                rightPeakHold = max(peakDbR, rightPeakHold - 1.5f)
                lastPeakDecayTime = now
            } else {
                leftPeakHold = max(peakDbL, leftPeakHold)
                rightPeakHold = max(peakDbR, rightPeakHold)
            }

            _meterData.value = MeterData(
                leftDb = dbL,
                rightDb = dbR,
                leftPeakDb = leftPeakHold,
                rightPeakDb = rightPeakHold,
                leftClip = peakL >= 0.999f,
                rightClip = peakR >= 0.999f,
                spectrumBands = SpectrumAnalyzer.analyzeMono(spectrumInput, sampleRate)
            )
        }

        _isPlaying.value = false
    }

    fun release() {
        unloadBuffer()
    }
}
