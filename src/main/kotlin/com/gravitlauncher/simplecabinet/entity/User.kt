package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_external_identity",
            columnNames = ["external_provider_id", "external_subject"]
        )
    ]
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false, length = 100)
    var username: String,

    @Column(unique = true, nullable = false, length = 255)
    var email: String,

    @Column(name = "minecraft_profile_id", unique = true, nullable = false, length = 32)
    var minecraftProfileId: String = UUID.randomUUID().toString().replace("-", ""),

    @Column(nullable = false)
    var password: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var userType: UserType = UserType.LOCAL,

    @Column(name = "external_provider_id", length = 100)
    var externalProviderId: String? = null,

    @Column(name = "external_subject", length = 255)
    var externalSubject: String? = null,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var userRoles: MutableList<UserRole> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var assets: MutableList<UserAsset> = mutableListOf(),

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserType {
    LOCAL,    // User managed locally in this system
    EXTERNAL  // User managed by external system
}
