package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

class ResourceNotFoundException(
    errorCode: ErrorCode,
    message: String? = null
) : BusinessException(
    errorCode = errorCode,
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)
