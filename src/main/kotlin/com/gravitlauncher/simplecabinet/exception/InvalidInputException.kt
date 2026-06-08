package com.gravitlauncher.simplecabinet.exception

import org.springframework.http.HttpStatus

class InvalidInputException(
    errorCode: ErrorCode = ErrorCode.INVALID_INPUT,
    message: String? = null
) : BusinessException(
    errorCode = errorCode,
    httpStatus = HttpStatus.BAD_REQUEST,
    message = message
)
