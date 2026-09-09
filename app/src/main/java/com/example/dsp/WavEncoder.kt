package com.example.dsp

import com.example.model.AudioMetadata
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

object WavEncoder {

    fun encode(
        buffer: AudioBuffer,
        bitDepth: Int = 16,
        applyDither: Boolean = true,
        metadata: AudioMetadata? = null
    ): ByteArray {
        val channels = buffer.channels
        val length = buffer.length
        val sampleRate = buffer.sampleRate
        val bytesPerSample = bitDepth / 8
        val blockAlign = channels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val dataSize = length * blockAlign

        val dataBytes = ByteArray(dataSize)
        val byteBuf = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)

        val rng = Random(42)

        if (bitDepth == 16) {
            val scale = 32767f
            for (i in 0 until length) {
                for (c in 0 until channels) {
                    var s = buffer.getChannel(c)[i]
                    if (applyDither) {
                        // TPDF dither: (rand - rand) / 32768
                        val dither = (rng.nextFloat() - rng.nextFloat()) / 32768f
                        s += dither
                    }
                    val intVal = (s * scale).coerceIn(-32768f, 32767f).toInt().toShort()
                    byteBuf.putShort(intVal)
                }
            }
        } else {
            // 24-bit PCM
            val scale = 8388607f
            for (i in 0 until length) {
                for (c in 0 until channels) {
                    var s = buffer.getChannel(c)[i]
                    if (applyDither) {
                        val dither = (rng.nextFloat() - rng.nextFloat()) / 8388608f
                        s += dither
                    }
                    val intVal = (s * scale).coerceIn(-8388608f, 8388607f).toInt()
                    byteBuf.put((intVal and 0xFF).toByte())
                    byteBuf.put(((intVal shr 8) and 0xFF).toByte())
                    byteBuf.put(((intVal shr 16) and 0xFF).toByte())
                }
            }
        }

        // Build RIFF / WAVE structure with LIST/INFO metadata
        val listChunk = buildListInfoChunk(metadata)
        val listSize = listChunk?.size ?: 0

        val totalRiffSize = 4 + (8 + 16) + (8 + dataSize) + (if (listSize > 0) 8 + listSize else 0)

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(totalRiffSize)
        header.put("WAVE".toByteArray())

        // fmt chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // chunk size
        header.putShort(1) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitDepth.toShort())

        // data chunk header
        header.put("data".toByteArray())
        header.putInt(dataSize)

        out.write(header.array())
        out.write(dataBytes)

        if (listChunk != null && listChunk.isNotEmpty()) {
            val listHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            listHeader.put("LIST".toByteArray())
            listHeader.putInt(listChunk.size)
            out.write(listHeader.array())
            out.write(listChunk)
            if (listChunk.size % 2 != 0) {
                out.write(0) // padding byte
            }
        }

        return out.toByteArray()
    }

    private fun buildListInfoChunk(metadata: AudioMetadata?): ByteArray? {
        if (metadata == null || !metadata.hasAny()) return null

        val infoStream = ByteArrayOutputStream()
        infoStream.write("INFO".toByteArray())

        fun writeSubChunk(fourCc: String, value: String) {
            if (value.isBlank()) return
            val strBytes = (value + "\u0000").toByteArray(Charsets.UTF_8)
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.put(fourCc.toByteArray())
            header.putInt(strBytes.size)
            infoStream.write(header.array())
            infoStream.write(strBytes)
            if (strBytes.size % 2 != 0) {
                infoStream.write(0) // pad to even boundary
            }
        }

        writeSubChunk("INAM", metadata.title)
        writeSubChunk("IART", metadata.artist)
        writeSubChunk("IPRD", metadata.album)
        writeSubChunk("IGNR", metadata.genre)
        writeSubChunk("ICRD", metadata.year)
        writeSubChunk("ITRK", metadata.track)
        writeSubChunk("ICMT", metadata.comment)

        return infoStream.toByteArray()
    }
}
