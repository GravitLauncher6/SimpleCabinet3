package com.gravitlauncher.simplecabinet.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError as SpringFieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * Handle custom business exceptions
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Business exception: ${ex.errorCode.code} - ${ex.message}", ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = ex.httpStatus.value(),
            error = ex.httpStatus.reasonPhrase,
            errorCode = ex.errorCode.code,
            message = ex.message ?: ex.errorCode.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(ex.httpStatus).body(errorResponse)
    }
    
    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ValidationErrorResponse> {
        logger.warn("Validation error: ${ex.bindingResult.errorCount} field(s) failed validation")
        
        val fieldErrors = ex.bindingResult.allErrors.mapNotNull { error ->
            if (error is SpringFieldError) {
                FieldError(
                    field = error.field,
                    rejectedValue = error.rejectedValue,
                    message = error.defaultMessage ?: "Invalid value"
                )
            } else null
        }
        
        val errorResponse = ValidationErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            errorCode = ErrorCode.VALIDATION_ERROR.code,
            message = "Validation failed for ${fieldErrors.size} field(s)",
            path = extractPath(request),
            fieldErrors = fieldErrors
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
    
    /**
     * Handle NoSuchElementException (typically from repository findById().get())
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(
        ex: NoSuchElementException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            errorCode = ErrorCode.RESOURCE_NOT_FOUND.code,
            message = ex.message ?: ErrorCode.RESOURCE_NOT_FOUND.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }
    
    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid argument: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            errorCode = ErrorCode.INVALID_INPUT.code,
            message = ex.message ?: ErrorCode.INVALID_INPUT.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
    
    /**
     * Handle Spring Security AuthenticationException
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            errorCode = ErrorCode.UNAUTHORIZED.code,
            message = ex.message ?: ErrorCode.UNAUTHORIZED.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }
    
    /**
     * Handle Spring Security AccessDeniedException
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.FORBIDDEN.value(),
            error = HttpStatus.FORBIDDEN.reasonPhrase,
            errorCode = ErrorCode.ACCESS_DENIED.code,
            message = ex.message ?: ErrorCode.ACCESS_DENIED.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }
    
    /**
     * Handle ResponseStatusException (used in PermissionAspect)
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Response status exception: ${ex.statusCode} - ${ex.reason}")
        
        val errorCode = when (ex.statusCode.value()) {
            HttpStatus.UNAUTHORIZED.value() -> ErrorCode.UNAUTHORIZED
            HttpStatus.FORBIDDEN.value() -> ErrorCode.ACCESS_DENIED
            HttpStatus.NOT_FOUND.value() -> ErrorCode.RESOURCE_NOT_FOUND
            HttpStatus.BAD_REQUEST.value() -> ErrorCode.BAD_REQUEST
            else -> ErrorCode.INTERNAL_ERROR
        }
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = ex.statusCode.value(),
            error = ex.statusCode.toString(),
            errorCode = errorCode.code,
            message = ex.reason ?: errorCode.message,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(ex.statusCode).body(errorResponse)
    }
    
    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception: ${ex.message}", ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            errorCode = ErrorCode.INTERNAL_ERROR.code,
            message = "An unexpected error occurred",
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
    
    /**
     * Extract request path from WebRequest
     */
    private fun extractPath(request: WebRequest): String {
        return request.getDescription(false).removePrefix("uri=")
    }
}
