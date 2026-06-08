package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

class UnauthorizedException(
    errorCode: ErrorCode = ErrorCode.UNAUTHORIZED,
    message: String? = null
) : BusinessException(
    errorCode = errorCode,
    httpStatus = HttpStatus.UNAUTHORIZED,
    message = message
)
