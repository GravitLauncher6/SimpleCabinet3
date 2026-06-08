package com.gravitlauncher.simplecabinet.exception

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class ErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val error: String,
    val errorCode: String,
    val message: String,
    val path: String
) {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val formattedTimestamp: LocalDateTime = timestamp
}
