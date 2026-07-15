package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_roles",
    indexes = [
        Index(name = "idx_user_role_user_expiry", columnList = "user_id, expires_at"),
        Index(name = "idx_user_role_role", columnList = "role_id")
    ]
)
data class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)
