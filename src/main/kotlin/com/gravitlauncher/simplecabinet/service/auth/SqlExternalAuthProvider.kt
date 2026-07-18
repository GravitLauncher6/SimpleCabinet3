package com.gravitlauncher.simplecabinet.service.auth

import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SqlExternalAuthProvider(
    override val id: String,
    private val properties: SqlExternalAuthProperties,
    private val passwordEncoder: PasswordEncoder,
    fingerprintSecret: String
) : PasswordExternalAuthProvider {
    private val table = identifier(properties.table)
    private val subjectColumn = identifier(properties.subjectColumn)
    private val usernameColumn = identifier(properties.usernameColumn)
    private val passwordColumn = identifier(properties.passwordColumn)
    private val emailColumn = optionalIdentifier(properties.emailColumn)
    private val rolesColumn = optionalIdentifier(properties.rolesColumn)
    private val enabledColumn = optionalIdentifier(properties.enabledColumn)
    private val fingerprintKey = SecretKeySpec(fingerprintSecret.toByteArray(), "HmacSHA256")

    init {
        require(properties.jdbcUrl.isNotBlank()) { "external-auth.sql.jdbc-url is required" }
        require(fingerprintSecret.isNotBlank()) {
            "external-auth.credential-fingerprint-secret is required for SQL external auth"
        }
        properties.driverClassName.takeIf(String::isNotBlank)?.let { Class.forName(it) }
    }

    override fun authenticate(request: ExternalPasswordAuthentication): ExternalAuthentication? {
        val row = findBy("$usernameColumn = ?", request.username) ?: return null
        if (!row.enabled || !passwordMatches(request.password, row.passwordHash)) return null
        return ExternalAuthentication(
            identity = ExternalIdentity(id, row.subject, row.username, row.email, row.roles),
            sessionState = ExternalSessionState("credential-fingerprint", payload = fingerprint(row))
        )
    }

    override fun validateSession(
        identity: ExternalIdentityReference,
        state: ExternalSessionState
    ): ExternalSessionValidation {
        if (state.type != "credential-fingerprint") return ExternalSessionValidation.ReauthenticationRequired
        val row = findBy("CAST($subjectColumn AS VARCHAR(255)) = ?", identity.subject)
            ?: return ExternalSessionValidation.ReauthenticationRequired
        return if (row.enabled && constantTimeEquals(state.payload, fingerprint(row))) {
            ExternalSessionValidation.Valid()
        } else {
            ExternalSessionValidation.ReauthenticationRequired
        }
    }

    private fun findBy(predicate: String, value: String): ExternalRow? {
        val selected = listOfNotNull(
            "$subjectColumn AS ext_subject",
            "$usernameColumn AS ext_username",
            "$passwordColumn AS ext_password",
            emailColumn?.let { "$it AS ext_email" },
            rolesColumn?.let { "$it AS ext_roles" },
            enabledColumn?.let { "$it AS ext_enabled" }
        )
        val sql = "SELECT ${selected.joinToString(", ")} FROM $table WHERE $predicate"
        DriverManager.getConnection(properties.jdbcUrl, properties.username, properties.password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, value)
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return ExternalRow(
                        subject = result.getString("ext_subject"),
                        username = result.getString("ext_username"),
                        passwordHash = result.getString("ext_password") ?: "",
                        email = emailColumn?.let { result.getString("ext_email") },
                        roles = rolesColumn?.let {
                            result.getString("ext_roles").orEmpty().split(properties.roleSeparator)
                                .map(String::trim).filter(String::isNotEmpty).toSet()
                        }.orEmpty(),
                        enabled = enabledColumn == null || result.getBoolean("ext_enabled")
                    )
                }
            }
        }
    }

    private fun passwordMatches(raw: String, encoded: String): Boolean = when (properties.passwordEncoding) {
        ExternalPasswordEncoding.BCRYPT -> passwordEncoder.matches(raw, encoded)
        ExternalPasswordEncoding.PLAIN -> raw == encoded
    }

    private fun fingerprint(row: ExternalRow): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(fingerprintKey)
        return Base64.getEncoder().encodeToString(
            mac.doFinal("$id\u0000${row.subject}\u0000${row.passwordHash}".toByteArray())
        )
    }

    private fun constantTimeEquals(left: String, right: String) =
        java.security.MessageDigest.isEqual(left.toByteArray(), right.toByteArray())

    private fun optionalIdentifier(value: String) = value.takeIf(String::isNotBlank)?.let(::identifier)

    private fun identifier(value: String): String {
        require(IDENTIFIER.matches(value)) { "Invalid SQL identifier: $value" }
        return value
    }

    private data class ExternalRow(
        val subject: String,
        val username: String,
        val passwordHash: String,
        val email: String?,
        val roles: Set<String>,
        val enabled: Boolean
    )

    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")
    }
}
