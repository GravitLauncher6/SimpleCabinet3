package com.gravitlauncher.simplecabinet.service.auth

data class ExternalPasswordAuthentication(
    val username: String,
    val password: String
)

data class ExternalIdentity(
    val providerId: String,
    val subject: String,
    val username: String,
    val email: String?,
    val roles: Set<String> = emptySet()
)

data class ExternalSessionState(
    val type: String,
    val version: Int = 1,
    val payload: String
)

data class ExternalAuthentication(
    val identity: ExternalIdentity,
    val sessionState: ExternalSessionState
)

sealed interface ExternalSessionValidation {
    data class Valid(val updatedState: ExternalSessionState? = null) : ExternalSessionValidation
    data object ReauthenticationRequired : ExternalSessionValidation
}

interface ExternalAuthProvider {
    val id: String

    fun validateSession(
        identity: ExternalIdentityReference,
        state: ExternalSessionState
    ): ExternalSessionValidation
}

interface PasswordExternalAuthProvider : ExternalAuthProvider {
    fun authenticate(request: ExternalPasswordAuthentication): ExternalAuthentication?
}

data class ExternalIdentityReference(
    val providerId: String,
    val subject: String
)

interface OAuthExternalAuthProvider : ExternalAuthProvider {
    fun authorizationUrl(state: String, redirectUri: String): String

    fun authenticateCallback(code: String, redirectUri: String): ExternalAuthentication
}
