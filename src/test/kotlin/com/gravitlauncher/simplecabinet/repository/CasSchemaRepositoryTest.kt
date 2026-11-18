package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.dto.CreateDirectoryRequest
import com.gravitlauncher.simplecabinet.dto.ForkDirectoryRequest
import com.gravitlauncher.simplecabinet.dto.PublishDirectoryVersionRequest
import com.gravitlauncher.simplecabinet.entity.Blob
import com.gravitlauncher.simplecabinet.entity.CasDirectory
import com.gravitlauncher.simplecabinet.entity.DirectoryBranch
import com.gravitlauncher.simplecabinet.entity.DirectoryVersion
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.service.CasStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CasSchemaRepositoryTest {

    @Autowired
    private lateinit var blobRepository: BlobRepository

    @Autowired
    private lateinit var casDirectoryRepository: CasDirectoryRepository

    @Autowired
    private lateinit var directoryVersionRepository: DirectoryVersionRepository

    @Autowired
    private lateinit var directoryBranchRepository: DirectoryBranchRepository

    @Autowired
    private lateinit var casStorageService: CasStorageService

    @Test
    fun `deleting a directory removes its history and preserves forks and blobs`() {
        val blob = blobRepository.save(
            Blob(hash = "sha256:delete-test", algorithm = "SHA-256", sizeBytes = 3, storageKey = "blobs/delete-test")
        )
        val directory = casStorageService.createDirectory(CreateDirectoryRequest(key = "delete/source"))
        val manifest = """
            {"files":[{"path":"app.jar","hash":"${blob.hash}","size":${blob.sizeBytes},"storageKey":"${blob.storageKey}"}]}
        """.trimIndent()
        val publish = PublishDirectoryVersionRequest(directoryKey = directory.key, branchName = "main", manifest = manifest, metadata = "{}")
        casStorageService.publishVersion(publish)
        val head = casStorageService.publishVersion(publish)
        val fork = casStorageService.forkDirectory(
            ForkDirectoryRequest(sourceDirectoryKey = directory.key, sourceBranchName = "main", targetDirectoryKey = "delete/fork", targetBranchName = "main", metadata = "{}")
        )
        assertEquals(head.id, fork.parentVersionId)

        casStorageService.deleteDirectory(" ${directory.key} ")

        assertThrows(ResourceNotFoundException::class.java) { casStorageService.getDirectory(directory.key) }
        assertThrows(ResourceNotFoundException::class.java) { casStorageService.deleteDirectory(directory.key) }
        assertTrue(directoryVersionRepository.findAllByDirectoryIdOrderByCreatedAtDesc(directory.id).isEmpty())
        assertTrue(directoryBranchRepository.findAllByDirectoryId(directory.id).isEmpty())
        val survivingFork = casStorageService.getLatestVersion(fork.directoryKey, "main")
        assertEquals(fork.id, survivingFork.id)
        assertEquals(fork.manifest, survivingFork.manifest)
        assertNull(survivingFork.parentVersionId)
        assertEquals(blob.id, casStorageService.getBlobByHash(blob.hash).id)
    }

    @Test
    fun `should persist directory versions and branch heads with json manifest and metadata`() {
        val blob = blobRepository.save(
            Blob(
                hash = "sha256:abc123",
                algorithm = "sha256",
                sizeBytes = 123L,
                storageKey = "blobs/sha256/ab/cd/abc123"
            )
        )

        val directory = casDirectoryRepository.save(
            CasDirectory(
                key = "project/main",
                name = "Main Project"
            )
        )

        val manifest = """
            {"files":[{"path":"bin/app.jar","hash":"${blob.hash}","size":${blob.sizeBytes},"storageKey":"${blob.storageKey}"}]}
        """.trimIndent()
        val metadata = """
            {"tool":"publisher","build":"1.0.0"}
        """.trimIndent()

        val version = directoryVersionRepository.save(
            DirectoryVersion(
                directory = directory,
                manifestHash = "manifest:123",
                manifest = manifest,
                metadata = metadata,
                message = "Initial publish"
            )
        )

        val branch = directoryBranchRepository.save(
            DirectoryBranch(
                directory = directory,
                name = "main",
                headVersion = version
            )
        )

        val loadedBranch = directoryBranchRepository.findByDirectoryIdAndName(directory.id!!, "main").orElseThrow()
        assertNotNull(loadedBranch.id)
        assertEquals(version.id, loadedBranch.headVersion?.id)

        val directoryId = directory.id ?: error("directory id was not generated")
        val versions = directoryVersionRepository.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)
        assertEquals(1, versions.size)
        assertEquals(manifest, versions.first().manifest)
        assertEquals(metadata, versions.first().metadata)
        assertEquals(branch.id, loadedBranch.id)
    }
}
