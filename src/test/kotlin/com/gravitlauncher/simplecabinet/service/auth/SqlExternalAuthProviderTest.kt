package com.gravitlauncher.simplecabinet.service.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.sql.DriverManager

class SqlExternalAuthProviderTest {
    private val jdbcUrl = "jdbc:h2:mem:external-auth;DB_CLOSE_DELAY=-1"

    @BeforeEach
    fun setUp() {
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS external_users")
                statement.execute(
                    "CREATE TABLE external_users (" +
                        "user_id VARCHAR(100), login VARCHAR(100), password_hash VARCHAR(100), " +
                        "mail VARCHAR(255), role_names VARCHAR(255), active BOOLEAN)"
                )
                statement.execute(
                    "INSERT INTO external_users VALUES " +
                        "('42', 'alice', 'secret', 'alice@example.com', 'USER,ADMIN', TRUE)"
                )
            }
        }
    }

    @Test
    fun `authenticates and invalidates state after password change`() {
        val provider = provider()
        val authentication = provider.authenticate(ExternalPasswordAuthentication("alice", "secret"))

        assertNotNull(authentication)
        assertEquals("42", authentication!!.identity.subject)
        assertEquals(setOf("USER", "ADMIN"), authentication.identity.roles)
        assertSame(
            ExternalSessionValidation.Valid::class,
            provider.validateSession(
                ExternalIdentityReference("legacy", "42"),
                authentication.sessionState
            )::class
        )

        DriverManager.getConnection(jdbcUrl, "sa", "").use {
            it.createStatement().execute("UPDATE external_users SET password_hash = 'changed' WHERE user_id = '42'")
        }
        assertSame(
            ExternalSessionValidation.ReauthenticationRequired,
            provider.validateSession(
                ExternalIdentityReference("legacy", "42"),
                authentication.sessionState
            )
        )
        assertNull(provider.authenticate(ExternalPasswordAuthentication("alice", "secret")))
    }

    @Test
    fun `rejects invalid credentials and disabled users`() {
        val provider = provider()

        assertNull(provider.authenticate(ExternalPasswordAuthentication("alice", "wrong")))

        DriverManager.getConnection(jdbcUrl, "sa", "").use {
            it.createStatement().execute("UPDATE external_users SET active = FALSE WHERE user_id = '42'")
        }
        assertNull(provider.authenticate(ExternalPasswordAuthentication("alice", "secret")))
    }

    @Test
    fun `returns reauthentication when external identity does not exist`() {
        val provider = provider()

        assertSame(
            ExternalSessionValidation.ReauthenticationRequired,
            provider.validateSession(
                ExternalIdentityReference("legacy", "missing"),
                ExternalSessionState("credential-fingerprint", "state")
            )
        )
    }

    private fun provider() = SqlExternalAuthProvider(
        "legacy",
        SqlExternalAuthProperties(
            jdbcUrl = jdbcUrl,
            username = "sa",
            table = "external_users",
            subjectColumn = "user_id",
            usernameColumn = "login",
            passwordColumn = "password_hash",
            emailColumn = "mail",
            rolesColumn = "role_names",
            enabledColumn = "active",
            passwordEncoding = ExternalPasswordEncoding.PLAIN
        ),
        BCryptPasswordEncoder(),
        "test-fingerprint-secret"
    )
}
