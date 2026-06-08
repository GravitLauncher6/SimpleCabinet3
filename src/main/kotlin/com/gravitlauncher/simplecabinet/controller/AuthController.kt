package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.dto.AuthResponse
import com.gravitlauncher.simplecabinet.dto.LoginRequest
import com.gravitlauncher.simplecabinet.dto.MessageResponse
import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import com.gravitlauncher.simplecabinet.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> {
        return try {
            val response = userService.register(request)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MessageResponse(e.message ?: "Registration failed"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Internal server error"))
        }
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<Any> {
        return try {
            val response = userService.login(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MessageResponse("Invalid username or password"))
        }
    }
}
