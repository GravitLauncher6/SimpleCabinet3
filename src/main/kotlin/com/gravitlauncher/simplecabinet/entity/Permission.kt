package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*

@Entity
@Table(name = "permissions")
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @Column(nullable = false)
    val permissionKey: String,

    @Column(length = 1000)
    val permissionValue: String? = null
)
