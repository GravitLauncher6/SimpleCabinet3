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
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 128)
    val hash: String,

    @Column(nullable = false, length = 16)
    val algorithm: String,

    @Column(nullable = false)
    val sizeBytes: Long,

    @Column(nullable = false, unique = true, length = 1024)
    val storageKey: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
