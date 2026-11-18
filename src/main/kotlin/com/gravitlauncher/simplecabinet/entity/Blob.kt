package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "blobs", indexes = [
    Index(name = "idx_blob_created_at", columnList = "createdAt")
])
data class Blob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 128)
    var hash: String,

    @Column(nullable = false, length = 16)
    var algorithm: String,

    @Column(nullable = false)
    var sizeBytes: Long,

    @Column(nullable = false, unique = true, length = 1024)
    var storageKey: String,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
