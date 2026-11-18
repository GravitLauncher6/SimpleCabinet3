package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "sessions", indexes = [
    Index(name = "idx_session_token", columnList = "sessionToken"),
    Index(name = "idx_session_user", columnList = "user_id"),
    Index(name = "idx_session_expiry", columnList = "expiresAt")
])
data class Session(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 255)
    var sessionToken: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var lastAccessedAt: Instant = Instant.now(),

    @Column(length = 100)
    var ipAddress: String? = null,

    @Column(length = 255)
    var userAgent: String? = null,

    @Column(length = 50)
    var deviceType: String? = null,

    @Column(length = 255)
    var serverId: String? = null,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column
    var revokedAt: Instant? = null,

    @Column(length = 255)
    var revokedReason: String? = null,

    @Column(name = "external_provider_id", length = 100)
    var externalProviderId: String? = null,

    @Column(name = "external_state", columnDefinition = "TEXT")
    var externalState: String? = null
) {
    companion object {
        const val ADMIN_CLI_DEVICE_TYPE = "admin-cli"
    }
}
