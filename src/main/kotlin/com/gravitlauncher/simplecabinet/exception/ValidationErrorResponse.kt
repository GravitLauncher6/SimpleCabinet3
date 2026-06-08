package com.gravitlauncher.simplecabinet.exception

import java.time.LocalDateTime

data class ValidationErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val error: String,
    val errorCode: String,
    val message: String,
    val path: String,
    val fieldErrors: List<FieldError>
)

data class FieldError(
    val field: String,
    val rejectedValue: Any?,
    val message: String
)
