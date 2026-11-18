package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_role_user_role",
            columnNames = ["user_id", "role_id"]
        )
    ],
    indexes = [
        Index(name = "idx_user_role_user_expiry", columnList = "user_id, expires_at"),
        Index(name = "idx_user_role_role", columnList = "role_id")
    ]
)
data class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role,

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
)
