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
    var id: Long? = null,

    @Column(nullable = false, length = 255)
    var variant: String,

    @Column(nullable = false, length = 1024)
    var storageKey: String,

    @Column(nullable = false, length = 2048)
    var url: String,

    @Column(nullable = false, length = 255)
    var originalFilename: String,

    @Column(nullable = false)
    var sizeBytes: Long,

    @Column(name = "public_key", nullable = false, unique = true, length = 4096)
    var publicKey: String,

    @Column(name = "private_key", nullable = false, length = 4096)
    var privateKey: String,

    @Column(nullable = false)
    var deprecated: Boolean = false,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
