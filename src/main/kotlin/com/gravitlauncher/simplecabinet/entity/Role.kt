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
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    var parent: Role? = null,

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], orphanRemoval = true)
    var permissions: MutableList<Permission> = mutableListOf(),

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], orphanRemoval = true)
    var userRoles: MutableList<UserRole> = mutableListOf(),

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
