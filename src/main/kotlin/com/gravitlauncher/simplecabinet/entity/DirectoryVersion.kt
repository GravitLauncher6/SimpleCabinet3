package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "directory_versions", indexes = [
    Index(name = "idx_directory_version_directory_created_at", columnList = "directory_id, createdAt"),
    Index(name = "idx_directory_version_parent", columnList = "parent_version_id"),
    Index(name = "idx_directory_version_directory_manifest_hash", columnList = "directory_id, manifestHash")
])
data class DirectoryVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id", nullable = false)
    var directory: CasDirectory,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    var parentVersion: DirectoryVersion? = null,

    @Column(nullable = false, length = 128)
    var manifestHash: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    var manifest: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    var metadata: String,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column
    var createdBy: Long? = null,

    @Column(length = 1000)
    var message: String? = null
)
