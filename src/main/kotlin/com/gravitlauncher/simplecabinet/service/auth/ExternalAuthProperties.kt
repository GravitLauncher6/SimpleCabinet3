package com.gravitlauncher.simplecabinet.service.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "external-auth")
data class ExternalAuthProperties(
    val enabled: Boolean = false,
    val providerId: String = "default",
    val stateEncryptionSecret: String = "",
    val credentialFingerprintSecret: String = "",
    val oauth: OAuthExternalAuthProperties = OAuthExternalAuthProperties(),
    val type: ExternalAuthType = ExternalAuthType.SQL,
    val sql: SqlExternalAuthProperties = SqlExternalAuthProperties(),
    val http: HttpExternalAuthProperties = HttpExternalAuthProperties()
)

enum class ExternalAuthType {
    SQL,
    HTTP
}

data class OAuthExternalAuthProperties(
    val callbackUrl: String = "http://localhost:8080/auth/oauth/callback/{providerId}"
)

data class SqlExternalAuthProperties(
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
    val driverClassName: String = "",
    val table: String = "users",
    val subjectColumn: String = "id",
    val usernameColumn: String = "username",
    val passwordColumn: String = "password",
    val emailColumn: String = "email",
    val rolesColumn: String = "roles",
    val enabledColumn: String = "",
    val roleSeparator: String = ",",
    val passwordEncoding: ExternalPasswordEncoding = ExternalPasswordEncoding.BCRYPT
)

enum class ExternalPasswordEncoding {
    BCRYPT,
    PLAIN
}

data class HttpExternalAuthProperties(
    val baseUrl: String = "",
    val authenticateEndpoint: String = "/auth/login",
    val refreshEndpoint: String = "/auth/refresh",
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10),
    val headers: Map<String, String> = emptyMap(),
    val fields: HttpExternalAuthFields = HttpExternalAuthFields()
)

data class HttpExternalAuthFields(
    val usernameRequest: String = "username",
    val passwordRequest: String = "password",
    val refreshTokenRequest: String = "refreshToken",
    val subjectResponse: String = "id",
    val successResponse: String = "success",
    val usernameResponse: String = "username",
    val emailResponse: String = "email",
    val rolesResponse: String = "roles",
    val errorResponse: String = "errorMessage",
    val refreshTokenResponse: String = "refreshToken",
    val accessTokenResponse: String = "accessToken"
)
