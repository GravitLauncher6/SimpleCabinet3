package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "cas_directories")
data class CasDirectory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "directory_key", nullable = false, unique = true, length = 255)
    val key: String,

    @Column(length = 255)
    val name: String? = null,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
)
