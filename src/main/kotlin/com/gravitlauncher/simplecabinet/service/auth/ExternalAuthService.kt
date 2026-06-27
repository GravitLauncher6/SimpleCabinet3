package com.gravitlauncher.simplecabinet.service.auth

/**
 * Interface for external authentication systems
 * Implement this interface to integrate with external CMS/database systems
 */
interface ExternalAuthService {
    
    /**
     * Verify user credentials with external system
     * @param username The username to authenticate
     * @param password The password to verify
     * @return ExternalAuthResult with authentication result and user data
     */
    fun authenticate(username: String, password: String): ExternalAuthResult
    
    /**
     * Verify refresh token with external system
     * @param username The username to verify
     * @param refreshToken The refresh token to verify
     * @return true if the refresh token is valid in the external system
     */
    fun verifyRefreshToken(username: String, refreshToken: String): Boolean
    
    /**
     * Notify external system about new refresh token
     * @param username The username
     * @param refreshToken The new refresh token
     */
    fun notifyRefreshToken(username: String, refreshToken: String)
}

data class ExternalAuthResult(
    val success: Boolean,
    val username: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val errorMessage: String? = null
)
