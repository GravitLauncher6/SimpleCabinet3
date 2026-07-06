package com.gravitlauncher.simplecabinet.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "directory_branches", uniqueConstraints = [
    UniqueConstraint(name = "uk_directory_branch_directory_name", columnNames = ["directory_id", "name"])
], indexes = [
    Index(name = "idx_directory_branch_head_version", columnList = "head_version_id")
])
data class DirectoryBranch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id", nullable = false)
    val directory: CasDirectory,

    @Column(nullable = false, length = 128)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_version_id")
    val headVersion: DirectoryVersion? = null,

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
)
