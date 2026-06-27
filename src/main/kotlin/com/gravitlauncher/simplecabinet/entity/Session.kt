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
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 255)
    val sessionToken: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    val lastAccessedAt: Instant = Instant.now(),

    @Column(length = 100)
    val ipAddress: String? = null,

    @Column(length = 255)
    val userAgent: String? = null,

    @Column(length = 50)
    val deviceType: String? = null,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column
    val revokedAt: Instant? = null,

    @Column(length = 255)
    val revokedReason: String? = null
)
