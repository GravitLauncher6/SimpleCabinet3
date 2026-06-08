package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

class DuplicateResourceException(
    errorCode: ErrorCode,
    message: String? = null
) : BusinessException(
    errorCode = errorCode,
    httpStatus = HttpStatus.BAD_REQUEST,
    message = message
)
