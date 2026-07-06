package com.gravitlauncher.simplecabinet.dto

import java.time.Instant

data class BlobRegistrationRequest(
    val hash: String,
    val algorithm: String,
    val sizeBytes: Long,
    val storageKey: String
)

data class BlobResponse(
    val id: Long,
    val hash: String,
    val algorithm: String,
    val sizeBytes: Long,
    val storageKey: String,
    val createdAt: Instant
)

data class CreateDirectoryRequest(
    val key: String,
    val name: String? = null
)

data class DirectoryResponse(
    val id: Long,
    val key: String,
    val name: String?,
    val createdAt: Instant
)

data class PublishDirectoryVersionRequest(
    val directoryKey: String,
    val branchName: String,
    val manifest: String,
    val metadata: String,
    val expectedParentVersionId: Long? = null,
    val createdBy: Long? = null,
    val message: String? = null
)

data class ForkDirectoryRequest(
    val sourceDirectoryKey: String,
    val sourceBranchName: String,
    val targetDirectoryKey: String,
    val targetName: String? = null,
    val targetBranchName: String,
    val metadata: String,
    val createdBy: Long? = null,
    val message: String? = null
)

data class DirectoryVersionResponse(
    val id: Long,
    val directoryId: Long,
    val directoryKey: String,
    val branchName: String?,
    val parentVersionId: Long?,
    val manifestHash: String,
    val manifest: String,
    val metadata: String,
    val createdAt: Instant,
    val createdBy: Long?,
    val message: String?
)

data class DirectoryBranchResponse(
    val id: Long,
    val directoryId: Long,
    val directoryKey: String,
    val name: String,
    val headVersionId: Long?,
    val updatedAt: Instant
)
