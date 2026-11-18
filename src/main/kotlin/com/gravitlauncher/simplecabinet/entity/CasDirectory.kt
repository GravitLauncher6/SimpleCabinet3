package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "cas_directories")
data class CasDirectory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "directory_key", nullable = false, unique = true, length = 255)
    var key: String,

    @Column(length = 255)
    var name: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var metadata: String = "{}",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
