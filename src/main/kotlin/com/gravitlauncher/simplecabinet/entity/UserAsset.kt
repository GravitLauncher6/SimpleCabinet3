package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class UserAssetType {
    SKIN,
    CAPE
}

@Entity
@Table(name = "user_assets", indexes = [
    Index(name = "idx_user_asset_user_type", columnList = "user_id,type"),
    Index(name = "idx_user_asset_active", columnList = "user_id,type,active")
])
data class UserAsset(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 36)
    val assetId: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blob_id", nullable = false)
    val blob: Blob,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: UserAssetType,

    @Column(length = 16)
    val variant: String? = null,

    @Column(length = 64)
    val alias: String? = null,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
)
