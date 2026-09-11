package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsp.AudioBuffer
import com.example.dsp.AudioDecoder
import com.example.dsp.AudioMasteringEngine
import com.example.dsp.LufsMeter
import com.example.dsp.LufsResult
import com.example.dsp.MetadataReader
import com.example.dsp.SampleAudioGenerator
import com.example.dsp.WavEncoder
import com.example.model.AudioMetadata
import com.example.model.BatchItem
import com.example.model.BatchStatus
import com.example.model.MasteringSettings
import com.example.player.MasteringPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class UiStatusMessage(
    val text: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class MasteringViewModel : ViewModel() {

    val player = MasteringPlayer(viewModelScope)

    private val _activeBuffer = MutableStateFlow<AudioBuffer?>(null)
    val activeBuffer: StateFlow<AudioBuffer?> = _activeBuffer.asStateFlow()

    private var originalBuffer: AudioBuffer? = null
    private val editHistory = mutableListOf<AudioBuffer>()
    private var lufsJob: Job? = null

    private val _currentFileName = MutableStateFlow("Demo - Synthwave Groove")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    private val _settings = MutableStateFlow(MasteringSettings())
    val settings: StateFlow<MasteringSettings> = _settings.asStateFlow()

    private val _lufsResult = MutableStateFlow<LufsResult?>(null)
    val lufsResult: StateFlow<LufsResult?> = _lufsResult.asStateFlow()

    private val _metadata = MutableStateFlow(
        AudioMetadata(
            title = "Synthwave Anthem",
            artist = "Suno Song Remaster",
            album = "Remastered Hits",
            genre = "Synthwave / Electronic",
            year = "2026",
            track = "1",
            comment = "Mastered with Suno Song Remaster"
        )
    )
    val metadata: StateFlow<AudioMetadata> = _metadata.asStateFlow()

    private val _selectionStart = MutableStateFlow<Double?>(null)
    val selectionStart: StateFlow<Double?> = _selectionStart.asStateFlow()

    private val _selectionEnd = MutableStateFlow<Double?>(null)
    val selectionEnd: StateFlow<Double?> = _selectionEnd.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _fadeDuration = MutableStateFlow(1.0f)
    val fadeDuration: StateFlow<Float> = _fadeDuration.asStateFlow()

    private val _loopEnabled = MutableStateFlow(false)
    val loopEnabled: StateFlow<Boolean> = _loopEnabled.asStateFlow()

    private val _batchQueue = MutableStateFlow<List<BatchItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchItem>> = _batchQueue.asStateFlow()

    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing: StateFlow<Boolean> = _isBatchProcessing.asStateFlow()

    private val _batchProgress = MutableStateFlow(0f)
    val batchProgress: StateFlow<Float> = _batchProgress.asStateFlow()

    private val _batchStatusText = MutableStateFlow("")
    val batchStatusText: StateFlow<String> = _batchStatusText.asStateFlow()

    private val _statusMessage = MutableStateFlow<UiStatusMessage?>(null)
    val statusMessage: StateFlow<UiStatusMessage?> = _statusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDemoTrack("synthwave")
    }

    fun showStatus(text: String, isError: Boolean = false) {
        _statusMessage.value = UiStatusMessage(text, isError)
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun loadDemoTrack(type: String = "synthwave") {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            val buffer = SampleAudioGenerator.generateSample(type)
            withContext(Dispatchers.Main) {
                _currentFileName.value = when (type) {
                    "acoustic" -> "Demo - Acoustic Sunset.wav"
                    "lofi" -> "Demo - Lo-Fi Chill Beat.wav"
                    else -> "Demo - Synthwave Groove.wav"
                }
                setNewWorkingBuffer(buffer, isInitial = true)
                _isLoading.value = false
                showStatus("Loaded demo track: ${_currentFileName.value}")
            }
        }
    }

    fun loadAudioFromUri(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val buffer = AudioDecoder.decodeFromUri(context, uri)
                val meta = MetadataReader.readFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    _currentFileName.value = fileName
                    _metadata.value = if (meta.hasAny()) {
                        meta
                    } else {
                        AudioMetadata(title = fileName.substringBeforeLast("."))
                    }
                    setNewWorkingBuffer(buffer, isInitial = true)
                    _isLoading.value = false
                    showStatus("Loaded audio file: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    showStatus(
                        "Error loading audio: ${e.localizedMessage ?: "Unknown error"}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun setNewWorkingBuffer(buffer: AudioBuffer, isInitial: Boolean = false) {
        if (isInitial) {
            originalBuffer = buffer.copy()
            editHistory.clear()
            _canUndo.value = false
        }
        _activeBuffer.value = buffer
        player.loadBuffer(buffer)
        player.settings = _settings.value
        recomputeLufs(buffer)
        clearSelection()
    }

    private fun recomputeLufs(buffer: AudioBuffer) {
        lufsJob?.cancel()
        val settingsSnapshot = _settings.value
        lufsJob = viewModelScope.launch(Dispatchers.Default) {
            // Measure the same pre-normalization signal that offline export uses.
            // This keeps preview loudness gain aligned with EQ, glue compression,
            // stereo width, center-bass and character-filter changes.
            val preNormalized = AudioMasteringEngine.processBeforeNormalization(
                buffer,
                settingsSnapshot
            )
            val result = LufsMeter.measure(preNormalized, settingsSnapshot.targetLufs)
            _lufsResult.value = result
            player.normGain = result.normalizationGain
        }
    }

    fun updateSettings(newSettings: MasteringSettings) {
        _settings.value = newSettings.clamped()
        player.settings = _settings.value
        _activeBuffer.value?.let { buffer -> recomputeLufs(buffer) }
    }

    fun setPreset(presetKey: String) {
        val preset = MasteringSettings.PRESETS[presetKey] ?: return
        updateSettings(
            _settings.value.copy(
                eqLow = preset.low,
                eqLowMid = preset.lowMid,
                eqMid = preset.mid,
                eqHighMid = preset.highMid,
                eqHigh = preset.high,
                activePreset = presetKey
            )
        )
        showStatus("Preset applied: ${preset.name}")
    }

    fun toggleBypass() {
        player.toggleBypass()
        val isBypassed = player.isBypass.value
        showStatus(
            if (isBypassed) {
                "Mastering Bypassed (Original Audio)"
            } else {
                "Mastering Active (Processed Audio)"
            }
        )
    }

    fun setSelection(startSec: Double?, endSec: Double?) {
        if (startSec != null && endSec != null && endSec > startSec) {
            _selectionStart.value = startSec
            _selectionEnd.value = endSec
            player.loopStartSec = startSec
            player.loopEndSec = endSec
        } else {
            clearSelection()
        }
    }

    fun clearSelection() {
        _selectionStart.value = null
        _selectionEnd.value = null
        player.loopStartSec = 0.0
        player.loopEndSec = _activeBuffer.value?.durationSeconds ?: 0.0
    }

    fun setFadeDuration(sec: Float) {
        _fadeDuration.value = sec.coerceIn(0.1f, 10f)
    }

    fun toggleLoop() {
        _loopEnabled.value = !_loopEnabled.value
        player.loopEnabled = _loopEnabled.value
    }

    private fun pushHistory() {
        _activeBuffer.value?.let {
            editHistory.add(it.copy())
            if (editHistory.size > 25) editHistory.removeAt(0)
            _canUndo.value = true
        }
    }

    fun applyFade(isFadeIn: Boolean) {
        val current = _activeBuffer.value ?: return
        val sr = current.sampleRate
        val selStart = _selectionStart.value
        val selEnd = _selectionEnd.value
        val fadeSec = _fadeDuration.value.toDouble()

        val startSample: Int
        val endSample: Int

        if (selStart != null && selEnd != null && selEnd > selStart) {
            startSample = (selStart * sr).toInt().coerceIn(0, current.length)
            endSample = (selEnd * sr).toInt().coerceIn(startSample, current.length)
        } else if (isFadeIn) {
            startSample = 0
            endSample = (fadeSec * sr).toInt().coerceIn(0, current.length)
        } else {
            endSample = current.length
            startSample = (current.length - (fadeSec * sr).toInt()).coerceIn(0, endSample)
        }

        val range = endSample - startSample
        if (range <= 0) return

        pushHistory()
        val newBuf = current.copy()
        for (c in 0 until newBuf.channels) {
            val data = newBuf.getChannel(c)
            for (i in startSample until endSample) {
                val progress = (i - startSample).toDouble() / range
                val gain = if (isFadeIn) {
                    sin(progress * PI / 2.0)
                } else {
                    cos(progress * PI / 2.0)
                }
                data[i] = (data[i] * gain).toFloat()
            }
        }
        setNewWorkingBuffer(newBuf)
        showStatus("${if (isFadeIn) "Fade In" else "Fade Out"} applied")
    }

    fun trimToSelection() {
        val current = _activeBuffer.value ?: return
        val startSec = _selectionStart.value
        val endSec = _selectionEnd.value
        if (startSec == null || endSec == null || endSec <= startSec) {
            showStatus("Select a region on the waveform first to trim", isError = true)
            return
        }

        val sr = current.sampleRate
        val startSample = (startSec * sr).toInt().coerceIn(0, current.length)
        val endSample = (endSec * sr).toInt().coerceIn(startSample, current.length)
        if (endSample - startSample < 100) return

        pushHistory()
        setNewWorkingBuffer(current.slice(startSample, endSample))
        showStatus("Trimmed to selection")
    }

    fun deleteSelection() {
        val current = _activeBuffer.value ?: return
        val startSec = _selectionStart.value
        val endSec = _selectionEnd.value
        if (startSec == null || endSec == null || endSec <= startSec) {
            showStatus("Select a region to cut / delete", isError = true)
            return
        }

        val sr = current.sampleRate
        val start = (startSec * sr).toInt().coerceIn(0, current.length)
        val end = (endSec * sr).toInt().coerceIn(start, current.length)
        val newLength = current.length - (end - start)
        if (newLength <= 0) {
            showStatus("Cannot delete the entire audio track", isError = true)
            return
        }

        pushHistory()
        val newBuf = AudioBuffer(current.channels, newLength, sr)
        for (c in 0 until current.channels) {
            val src = current.getChannel(c)
            val dst = newBuf.getChannel(c)
            System.arraycopy(src, 0, dst, 0, start)
            System.arraycopy(src, end, dst, start, current.length - end)
        }

        setNewWorkingBuffer(newBuf)
        showStatus("Selection deleted")
    }

    fun silenceSelection() {
        val current = _activeBuffer.value ?: return
        val startSec = _selectionStart.value
        val endSec = _selectionEnd.value
        if (startSec == null || endSec == null || endSec <= startSec) {
            showStatus("Select a region to silence", isError = true)
            return
        }

        val sr = current.sampleRate
        val start = (startSec * sr).toInt().coerceIn(0, current.length)
        val end = (endSec * sr).toInt().coerceIn(start, current.length)

        pushHistory()
        val newBuf = current.copy()
        for (c in 0 until newBuf.channels) {
            val data = newBuf.getChannel(c)
            for (i in start until end) data[i] = 0f
        }
        setNewWorkingBuffer(newBuf)
        showStatus("Selection silenced")
    }

    fun normalizePeak() {
        val current = _activeBuffer.value ?: return
        val peak = current.peak()
        if (peak <= 1e-5f) {
            showStatus("Track is silent — nothing to normalize", isError = true)
            return
        }

        pushHistory()
        val targetLinear = 10.0.pow(-0.3 / 20.0).toFloat()
        val gain = targetLinear / peak

        val newBuf = current.copy()
        for (c in 0 until newBuf.channels) {
            val data = newBuf.getChannel(c)
            for (i in data.indices) data[i] *= gain
        }
        setNewWorkingBuffer(newBuf)
        showStatus("Peak normalized to -0.3 dBFS")
    }

    fun reverseSelectionOrAll() {
        val current = _activeBuffer.value ?: return
        val startSec = _selectionStart.value
        val endSec = _selectionEnd.value
        val sr = current.sampleRate

        val start = if (startSec != null && endSec != null && endSec > startSec) {
            (startSec * sr).toInt().coerceIn(0, current.length)
        } else {
            0
        }
        val end = if (startSec != null && endSec != null && endSec > startSec) {
            (endSec * sr).toInt().coerceIn(start, current.length)
        } else {
            current.length
        }

        pushHistory()
        val newBuf = current.copy()
        for (c in 0 until newBuf.channels) {
            val data = newBuf.getChannel(c)
            var low = start
            var high = end - 1
            while (low < high) {
                val tmp = data[low]
                data[low] = data[high]
                data[high] = tmp
                low++
                high--
            }
        }
        setNewWorkingBuffer(newBuf)
        showStatus(
            if (start == 0 && end == current.length) {
                "Track reversed"
            } else {
                "Selection reversed"
            }
        )
    }

    fun undoEdit() {
        if (editHistory.isEmpty()) return
        val previous = editHistory.removeAt(editHistory.lastIndex)
        _canUndo.value = editHistory.isNotEmpty()
        setNewWorkingBuffer(previous)
        showStatus("Edit undone")
    }

    fun resetToOriginal() {
        val original = originalBuffer ?: return
        editHistory.clear()
        _canUndo.value = false
        setNewWorkingBuffer(original.copy())
        showStatus("Reverted to original audio")
    }

    fun updateMetadata(newMeta: AudioMetadata) {
        _metadata.value = newMeta
    }

    fun applyMetadataToAllBatch() {
        val currentMeta = _metadata.value
        _batchQueue.value = _batchQueue.value.map { item ->
            item.copy(metadata = currentMeta)
        }
        showStatus("Applied metadata to all ${_batchQueue.value.size} tracks in batch")
    }

    fun addBatchItem(name: String, uri: Uri?, sampleType: String? = null) {
        val item = BatchItem(
            id = UUID.randomUUID().toString(),
            name = name,
            uri = uri,
            sampleTrackType = sampleType,
            metadata = _metadata.value
        )
        _batchQueue.value = _batchQueue.value + item
        showStatus("Added to batch: $name")
    }

    fun removeBatchItem(id: String) {
        _batchQueue.value = _batchQueue.value.filter { it.id != id }
    }

    fun clearBatch() {
        _batchQueue.value = emptyList()
    }

    fun runBatchProcessing(
        context: Context,
        outputStreamProvider: (BatchItem) -> OutputStream?
    ) {
        val queue = _batchQueue.value
        if (queue.isEmpty() || _isBatchProcessing.value) return

        viewModelScope.launch(Dispatchers.Default) {
            _isBatchProcessing.value = true
            val total = queue.size
            var completedCount = 0
            var errorCount = 0

            for (i in queue.indices) {
                val item = queue[i]
                _batchStatusText.value = "Processing ${i + 1}/$total: ${item.name}"
                _batchProgress.value = i.toFloat() / total
                _batchQueue.value = _batchQueue.value.map {
                    if (it.id == item.id) it.copy(status = BatchStatus.PROCESSING) else it
                }

                try {
                    val inputBuf = if (item.uri != null) {
                        AudioDecoder.decodeFromUri(context, item.uri)
                    } else {
                        SampleAudioGenerator.generateSample(item.sampleTrackType ?: "synthwave")
                    }

                    val masteredBuf = AudioMasteringEngine.processOffline(
                        inputBuffer = inputBuf,
                        settings = _settings.value
                    )
                    val wavData = WavEncoder.encode(
                        buffer = masteredBuf,
                        bitDepth = _settings.value.bitDepth,
                        applyDither = _settings.value.bitDepth == 16,
                        metadata = item.metadata
                    )

                    val outStream = outputStreamProvider(item)
                        ?: throw IllegalStateException("Could not create batch output file")
                    outStream.use { stream ->
                        stream.write(wavData)
                        stream.flush()
                    }

                    completedCount++
                    _batchQueue.value = _batchQueue.value.map {
                        if (it.id == item.id) it.copy(status = BatchStatus.DONE) else it
                    }
                } catch (e: Exception) {
                    errorCount++
                    _batchQueue.value = _batchQueue.value.map {
                        if (it.id == item.id) {
                            it.copy(status = BatchStatus.ERROR, errorMessage = e.message)
                        } else {
                            it
                        }
                    }
                }
            }

            _batchProgress.value = 1f
            _isBatchProcessing.value = false
            _batchStatusText.value = "Done! $completedCount exported" +
                if (errorCount > 0) ", $errorCount failed" else ""
            withContext(Dispatchers.Main) {
                showStatus("Batch complete: $completedCount/$total files mastered successfully.")
            }
        }
    }

    fun exportActiveTrack(outputStream: OutputStream, onComplete: () -> Unit) {
        val current = _activeBuffer.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            try {
                val mastered = AudioMasteringEngine.processOffline(current, _settings.value)
                val wavBytes = WavEncoder.encode(
                    buffer = mastered,
                    bitDepth = _settings.value.bitDepth,
                    applyDither = _settings.value.bitDepth == 16,
                    metadata = _metadata.value
                )
                outputStream.use { stream ->
                    stream.write(wavBytes)
                    stream.flush()
                }

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    showStatus("Exported mastered WAV successfully!")
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    showStatus("Export failed: ${e.localizedMessage}", isError = true)
                }
            }
        }
    }

    override fun onCleared() {
        lufsJob?.cancel()
        player.release()
        super.onCleared()
    }
}
