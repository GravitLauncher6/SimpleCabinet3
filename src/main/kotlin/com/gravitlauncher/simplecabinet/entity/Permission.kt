package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "permissions",
    indexes = [
        Index(name = "idx_permission_role", columnList = "role_id"),
        Index(name = "idx_permission_key", columnList = "permission_key")
    ]
)
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @Column(name = "permission_key", nullable = false)
    val permissionKey: String,

    @Column(name = "permission_value", length = 1000)
    val permissionValue: String? = null
)
