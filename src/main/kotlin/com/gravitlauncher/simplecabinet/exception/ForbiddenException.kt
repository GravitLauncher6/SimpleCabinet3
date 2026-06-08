package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

class ForbiddenException(
    errorCode: ErrorCode = ErrorCode.ACCESS_DENIED,
    message: String? = null
) : BusinessException(
    errorCode = errorCode,
    httpStatus = HttpStatus.FORBIDDEN,
    message = message
)
