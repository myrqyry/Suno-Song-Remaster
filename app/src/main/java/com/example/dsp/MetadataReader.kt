package com.example.dsp

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.AudioMetadata
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MetadataReader {

    fun readFromUri(context: Context, uri: Uri): AudioMetadata {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: ""
            retriever.release()

            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                track = track
            )
        } catch (e: Exception) {
            AudioMetadata()
        }
    }

    fun parseWavListInfo(inputStream: InputStream): AudioMetadata {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 12) return AudioMetadata()

            var offset = 12 // Skip "RIFF....WAVE"
            var title = ""; var artist = ""; var album = ""; var genre = ""; var year = ""; var track = ""; var comment = ""

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4)
                val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                offset += 8

                if (chunkId == "LIST" && offset + 4 <= bytes.size) {
                    val listType = String(bytes, offset, 4)
                    if (listType == "INFO") {
                        var subOffset = offset + 4
                        val end = (offset + chunkSize).coerceAtMost(bytes.size)
                        while (subOffset + 8 <= end) {
                            val subId = String(bytes, subOffset, 4)
                            val subSize = ByteBuffer.wrap(bytes, subOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            subOffset += 8
                            if (subOffset + subSize <= end) {
                                val text = String(bytes, subOffset, subSize).trimEnd('\u0000', ' ')
                                when (subId) {
                                    "INAM" -> title = text
                                    "IART" -> artist = text
                                    "IPRD" -> album = text
                                    "IGNR" -> genre = text
                                    "ICRD" -> year = text
                                    "ITRK" -> track = text
                                    "ICMT" -> comment = text
                                }
                            }
                            subOffset += subSize + (subSize % 2)
                        }
                    }
                }
                offset += chunkSize + (chunkSize % 2)
            }

            AudioMetadata(title, artist, album, genre, year, track, comment)
        } catch (e: Exception) {
            AudioMetadata()
        }
    }
}
