package com.example.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList

object AudioDecoder {

    fun decodeFromUri(context: Context, uri: Uri): AudioBuffer {
        // First try fast direct WAV decoder
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = decodeWavStream(stream)
                if (buffer != null) return buffer
            }
        } catch (_: Exception) {
        }

        // Use Android MediaExtractor + MediaCodec
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                throw IllegalArgumentException("No audio track found in media file")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmChunks = ArrayList<FloatArray>()
            var totalSamples = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val kTimeOutUs = 10000L

            while (!isEos) {
                val inputIndex = codec.dequeueInputBuffer(kTimeOutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeOutUs)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val numShorts = shortBuf.remaining()
                        val floatChunk = FloatArray(numShorts)
                        for (s in 0 until numShorts) {
                            floatChunk[s] = shortBuf.get() / 32768f
                        }
                        pcmChunks.add(floatChunk)
                        totalSamples += numShorts
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            // De-interleave into AudioBuffer
            val frameCount = totalSamples / channelCount
            val audioBuffer = AudioBuffer(channelCount, frameCount, sampleRate)
            var frameIdx = 0

            for (chunk in pcmChunks) {
                var cIdx = 0
                while (cIdx < chunk.size && frameIdx < frameCount) {
                    for (ch in 0 until channelCount) {
                        audioBuffer.getChannel(ch)[frameIdx] = chunk[cIdx++]
                    }
                    frameIdx++
                }
            }

            return audioBuffer
        } catch (e: Exception) {
            extractor.release()
            throw e
        }
    }

    private fun decodeWavStream(inputStream: InputStream): AudioBuffer? {
        val bytes = inputStream.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

        var offset = 12
        var channels = 2
        var sampleRate = 44100
        var bitDepth = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 8

            if (chunkId == "fmt ") {
                val formatCode = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                if (formatCode != 1 && formatCode != 3) return null // PCM (1) or IEEE Float (3)
                channels = ByteBuffer.wrap(bytes, offset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                sampleRate = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                bitDepth = ByteBuffer.wrap(bytes, offset + 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            } else if (chunkId == "data") {
                dataOffset = offset
                dataSize = chunkSize.coerceAtMost(bytes.size - offset)
                break
            }
            offset += chunkSize + (chunkSize % 2)
        }

        if (dataOffset < 0 || channels <= 0) return null

        val bytesPerSample = bitDepth / 8
        val frameSize = channels * bytesPerSample
        val totalFrames = dataSize / frameSize
        val out = AudioBuffer(channels, totalFrames, sampleRate)
        val byteBuf = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

        if (bitDepth == 16) {
            for (i in 0 until totalFrames) {
                for (c in 0 until channels) {
                    out.getChannel(c)[i] = byteBuf.short / 32768f
                }
            }
        } else if (bitDepth == 24) {
            for (i in 0 until totalFrames) {
                for (c in 0 until channels) {
                    val b0 = byteBuf.get().toInt() and 0xFF
                    val b1 = byteBuf.get().toInt() and 0xFF
                    val b2 = byteBuf.get().toInt()
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    out.getChannel(c)[i] = sample24 / 8388608f
                }
            }
        } else if (bitDepth == 32) {
            for (i in 0 until totalFrames) {
                for (c in 0 until channels) {
                    out.getChannel(c)[i] = byteBuf.float
                }
            }
        }

        return out
    }
}
