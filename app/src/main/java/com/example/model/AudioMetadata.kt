package com.example.model

data class AudioMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val comment: String = ""
) {
    fun hasAny(): Boolean {
        return title.isNotBlank() || artist.isNotBlank() || album.isNotBlank() ||
                genre.isNotBlank() || year.isNotBlank() || track.isNotBlank() || comment.isNotBlank()
    }
}
