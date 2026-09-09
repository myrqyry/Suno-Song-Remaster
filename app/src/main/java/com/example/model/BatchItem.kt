package com.example.model

import android.net.Uri

enum class BatchStatus {
    PENDING,
    PROCESSING,
    DONE,
    ERROR
}

data class BatchItem(
    val id: String,
    val name: String,
    val uri: Uri? = null,
    val sampleTrackType: String? = null,
    val status: BatchStatus = BatchStatus.PENDING,
    val metadata: AudioMetadata = AudioMetadata(),
    val errorMessage: String? = null,
    val progress: Float = 0f
)
