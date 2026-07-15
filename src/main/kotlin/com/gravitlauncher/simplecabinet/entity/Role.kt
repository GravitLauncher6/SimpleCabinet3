package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "roles",
    indexes = [Index(name = "idx_role_parent", columnList = "parent_role_id")]
)
data class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val name: String,

    @Column(length = 500)
    val description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    val parent: Role? = null,

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], orphanRemoval = true)
    val permissions: MutableList<Permission> = mutableListOf(),

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], orphanRemoval = true)
    val userRoles: MutableList<UserRole> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
