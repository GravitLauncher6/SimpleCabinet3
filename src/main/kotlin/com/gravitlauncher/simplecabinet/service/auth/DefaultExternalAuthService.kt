package com.gravitlauncher.simplecabinet.service.auth

import org.springframework.stereotype.Service

/**
 * Default implementation of ExternalAuthService
 * This is a stub implementation that always denies authentication
 * Replace this with your actual external system integration
 */
@Service
class DefaultExternalAuthService : ExternalAuthService {
    
    override fun authenticate(username: String, password: String): ExternalAuthResult {
        // TODO: Implement actual external system authentication
        // Example: HTTP request to external API, database query, etc.
        return ExternalAuthResult(
            success = false,
            errorMessage = "External authentication not configured"
        )
    }
    
    override fun verifyRefreshToken(username: String, refreshToken: String): Boolean {
        // TODO: Implement actual refresh token verification with external system
        return false
    }
    
    override fun notifyRefreshToken(username: String, refreshToken: String) {
        // TODO: Notify external system about new refresh token
        // This can be used to sync refresh tokens with external system
    }
}
