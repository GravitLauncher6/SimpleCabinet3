package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "permissions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_permission_role_key",
            columnNames = ["role_id", "permission_key"]
        )
    ],
    indexes = [
        Index(name = "idx_permission_role", columnList = "role_id"),
        Index(name = "idx_permission_key", columnList = "permission_key")
    ]
)
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role,

    @Column(name = "permission_key", nullable = false)
    var permissionKey: String,

    @Column(name = "permission_value", length = 1000)
    var permissionValue: String? = null
)
