package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.dto.AuthResponse
import com.gravitlauncher.simplecabinet.dto.LoginRequest
import com.gravitlauncher.simplecabinet.dto.MessageResponse
import com.gravitlauncher.simplecabinet.dto.RefreshTokenRequest
import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import com.gravitlauncher.simplecabinet.service.UserService
import com.gravitlauncher.simplecabinet.service.auth.ExternalAuthProperties
import com.gravitlauncher.simplecabinet.service.auth.OAuthStateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService,
    private val externalAuthProperties: ExternalAuthProperties,
    private val oauthStateService: OAuthStateService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = userService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = userService.login(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/login_with_oauth/{providerId}")
    fun loginWithOAuth(@PathVariable providerId: String): ResponseEntity<Void> {
        val callbackUrl = oauthCallbackUrl(providerId)
        val state = oauthStateService.create(providerId)
        val authorizationUrl = userService.oauthAuthorizationUrl(providerId, state, callbackUrl)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(java.net.URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/oauth/callback/{providerId}")
    fun oauthCallback(
        @PathVariable providerId: String,
        @RequestParam code: String,
        @RequestParam state: String
    ): ResponseEntity<AuthResponse> {
        if (!oauthStateService.verify(state, providerId)) {
            throw com.gravitlauncher.simplecabinet.exception.UnauthorizedException(
                com.gravitlauncher.simplecabinet.exception.ErrorCode.INVALID_CREDENTIALS,
                "Invalid OAuth state"
            )
        }
        return ResponseEntity.ok(
            userService.loginWithOAuthCallback(providerId, code, oauthCallbackUrl(providerId))
        )
    }

    private fun oauthCallbackUrl(providerId: String): String =
        externalAuthProperties.oauth.callbackUrl.replace("{providerId}", providerId)

    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<AuthResponse> {
        val response = userService.refreshToken(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<MessageResponse> {
        userService.logout(request.refreshToken)
        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }
}
