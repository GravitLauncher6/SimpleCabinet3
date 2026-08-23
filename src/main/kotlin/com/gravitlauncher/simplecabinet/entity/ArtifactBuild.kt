package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "artifact_builds", indexes = [
    Index(name = "idx_artifact_build_deprecated", columnList = "deprecated"),
    Index(name = "idx_artifact_build_public_key", columnList = "public_key")
])
data class ArtifactBuild(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 255)
    val variant: String,

    @Column(nullable = false, length = 1024)
    val storageKey: String,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(nullable = false, length = 255)
    val originalFilename: String,

    @Column(nullable = false)
    val sizeBytes: Long,

    @Column(name = "public_key", nullable = false, unique = true, length = 4096)
    val publicKey: String,

    @Column(name = "private_key", nullable = false, length = 4096)
    val privateKey: String,

    @Column(nullable = false)
    val deprecated: Boolean = false,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
)
