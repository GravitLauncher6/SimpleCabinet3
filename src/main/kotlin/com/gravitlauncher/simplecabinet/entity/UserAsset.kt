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
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 36)
    var assetId: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blob_id", nullable = false)
    var blob: Blob,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: UserAssetType,

    @Column(length = 16)
    var variant: String? = null,

    @Column(length = 64)
    var alias: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
