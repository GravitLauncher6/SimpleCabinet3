package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Blob
import com.gravitlauncher.simplecabinet.entity.CasDirectory
import com.gravitlauncher.simplecabinet.entity.DirectoryBranch
import com.gravitlauncher.simplecabinet.entity.DirectoryVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
