package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

abstract class BusinessException(
    val errorCode: ErrorCode,
    val httpStatus: HttpStatus,
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message ?: errorCode.message, cause) {
    
    fun getErrorCodeString(): String = errorCode.code
    
    fun getDefaultMessage(): String = errorCode.message
}
