package com.example.dsp

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.ArrayList

object AudioDecoder {

    fun decodeFromUri(context: Context, uri: Uri): AudioBuffer {
        // Only invoke the whole-file WAV parser after a tiny 12-byte sniff.
        // Previously every MP3 was read fully into a ByteArray before being
        // rejected as non-WAV, creating a large avoidable memory spike.
        if (looksLikeWav(context, uri)) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = decodeWavStream(stream)
                    if (buffer != null) return buffer
                }
            } catch (_: Exception) {
                // Fall through to MediaCodec for unsupported/malformed WAV.
            }
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("No audio track found in media file")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Audio track has no MIME type")

            var decodedSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var decodedChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val pcmChunks = ArrayList<FloatArray>()
            var totalSamples = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            val timeoutUs = 10_000L

            // Input EOS only means there are no more compressed packets to feed.
            // The decoder can still hold delayed PCM, so keep draining until the
            // OUTPUT buffer carries BUFFER_FLAG_END_OF_STREAM.
            while (!outputEos) {
                if (!inputEos) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            decodedSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            decodedChannelCount = outputFormat
                                .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                .coerceIn(1, 2)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            val reportedEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            if (reportedEncoding == AudioFormat.ENCODING_PCM_8BIT ||
                                reportedEncoding == AudioFormat.ENCODING_PCM_16BIT ||
                                reportedEncoding == AudioFormat.ENCODING_PCM_FLOAT
                            ) {
                                pcmEncoding = reportedEncoding
                            }
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val floatChunk = decodePcmChunk(outputBuffer, pcmEncoding)
                            if (floatChunk.isNotEmpty()) {
                                pcmChunks.add(floatChunk)
                                totalSamples += floatChunk.size
                            }
                        }

                        val flags = bufferInfo.flags
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                    }
                }
            }

            val frameCount = totalSamples / decodedChannelCount
            if (frameCount <= 0) {
                throw IllegalArgumentException("Decoder produced no PCM audio")
            }

            val audioBuffer = AudioBuffer(decodedChannelCount, frameCount, decodedSampleRate)
            var frameIdx = 0
            var channelIdx = 0

            for (chunk in pcmChunks) {
                for (sample in chunk) {
                    if (frameIdx >= frameCount) break
                    audioBuffer.getChannel(channelIdx)[frameIdx] = sample
                    channelIdx++
                    if (channelIdx == decodedChannelCount) {
                        channelIdx = 0
                        frameIdx++
                    }
                }
            }

            // Drop references to the temporary decoded chunks before returning
            // the final planar AudioBuffer so GC can reclaim them immediately.
            pcmChunks.clear()
            return audioBuffer
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun looksLikeWav(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                var offset = 0
                while (offset < header.size) {
                    val read = stream.read(header, offset, header.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                offset == 12 &&
                    String(header, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
                    String(header, 8, 4, StandardCharsets.US_ASCII) == "WAVE"
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun decodePcmChunk(buffer: ByteBuffer, pcmEncoding: Int): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                FloatArray(floats.remaining()).also { floats.get(it) }
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                FloatArray(buffer.remaining()) {
                    ((buffer.get().toInt() and 0xFF) - 128) / 128f
                }
            }

            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = buffer.asShortBuffer()
                FloatArray(shorts.remaining()) {
                    shorts.get() / 32768f
                }
            }

            else -> throw IllegalArgumentException(
                "Unsupported decoded PCM encoding: $pcmEncoding"
            )
        }
    }

    private fun decodeWavStream(inputStream: InputStream): AudioBuffer? {
        val bytes = inputStream.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

        var offset = 12
        var formatCode = 1
        var channels = 2
        var sampleRate = 44100
        var bitDepth = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = ByteBuffer
                .wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            offset += 8

            if (chunkSize < 0 || offset + chunkSize > bytes.size) return null

            if (chunkId == "fmt ") {
                if (chunkSize < 16) return null
                formatCode = ByteBuffer
                    .wrap(bytes, offset, 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                    .toInt()
                if (formatCode != 1 && formatCode != 3) return null

                channels = ByteBuffer
                    .wrap(bytes, offset + 2, 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                    .toInt()
                sampleRate = ByteBuffer
                    .wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                bitDepth = ByteBuffer
                    .wrap(bytes, offset + 14, 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                    .toInt()
            } else if (chunkId == "data") {
                dataOffset = offset
                dataSize = chunkSize.coerceAtMost(bytes.size - offset)
                break
            }
            offset += chunkSize + (chunkSize % 2)
        }

        if (dataOffset < 0 || channels !in 1..2 || sampleRate <= 0) return null
        if (bitDepth !in setOf(8, 16, 24, 32)) return null
        if (formatCode == 3 && bitDepth != 32) return null

        val bytesPerSample = bitDepth / 8
        val frameSize = channels * bytesPerSample
        if (frameSize <= 0) return null
        val totalFrames = dataSize / frameSize
        val out = AudioBuffer(channels, totalFrames, sampleRate)
        val byteBuf = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalFrames) {
            for (c in 0 until channels) {
                out.getChannel(c)[i] = when (bitDepth) {
                    8 -> ((byteBuf.get().toInt() and 0xFF) - 128) / 128f
                    16 -> byteBuf.short / 32768f
                    24 -> {
                        val b0 = byteBuf.get().toInt() and 0xFF
                        val b1 = byteBuf.get().toInt() and 0xFF
                        val b2 = byteBuf.get().toInt()
                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                        sample24 / 8388608f
                    }

                    32 -> if (formatCode == 3) {
                        byteBuf.float
                    } else {
                        (byteBuf.int.toDouble() / 2147483648.0).toFloat()
                    }

                    else -> 0f
                }
            }
        }

        return out
    }
}
