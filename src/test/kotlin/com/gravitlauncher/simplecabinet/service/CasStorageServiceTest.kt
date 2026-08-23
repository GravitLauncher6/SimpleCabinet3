package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.BlobRegistrationRequest
import com.gravitlauncher.simplecabinet.dto.CreateDirectoryRequest
import com.gravitlauncher.simplecabinet.dto.ForkDirectoryRequest
import com.gravitlauncher.simplecabinet.dto.PublishDirectoryVersionRequest
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.service.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream

@SpringBootTest
class CasStorageServiceTest {

    @Autowired
    private lateinit var casStorageService: CasStorageService

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun `should publish and read latest branch version`() {
        registerBlob(
            BlobRegistrationRequest(
                hash = "sha256:file-a",
                algorithm = "sha256",
                sizeBytes = 128,
                storageKey = "blobs/sha256/fi/le/file-a"
            )
        )
        registerBlob(
            BlobRegistrationRequest(
                hash = "sha256:file-b",
                algorithm = "sha256",
                sizeBytes = 256,
                storageKey = "blobs/sha256/fi/le/file-b"
            )
        )

        val directory = casStorageService.createDirectory(CreateDirectoryRequest(key = "project/a", name = "Project A"))
        val published = casStorageService.publishVersion(
            PublishDirectoryVersionRequest(
                directoryKey = directory.key,
                branchName = "main",
                manifest = """
                    {"files":[
                      {"path":"b.txt","hash":"sha256:file-b","size":256,"storageKey":"blobs/sha256/fi/le/file-b"},
                      {"path":"a.txt","hash":"sha256:file-a","size":128,"storageKey":"blobs/sha256/fi/le/file-a"}
                    ]}
                """.trimIndent(),
                metadata = """
                    {"tool":"publisher","build":1}
                """.trimIndent(),
                createdBy = 42L,
                message = "Initial publish"
            )
        )

        val latest = casStorageService.getLatestVersion(directory.key, "main")
        assertEquals(published.id, latest.id)
        assertEquals("main", latest.branchName)
        assertEquals("{" + "\"files\":[{" + "\"path\":\"a.txt\",\"hash\":\"sha256:file-a\",\"size\":128,\"storageKey\":\"blobs/sha256/fi/le/file-a\",\"url\":\"http://localhost:8080/files/blobs/sha256/fi/le/file-a\"},{\"path\":\"b.txt\",\"hash\":\"sha256:file-b\",\"size\":256,\"storageKey\":\"blobs/sha256/fi/le/file-b\",\"url\":\"http://localhost:8080/files/blobs/sha256/fi/le/file-b\"}]}", latest.manifest)
        assertEquals("{\"tool\":\"publisher\",\"build\":1}", latest.metadata)
        assertEquals(42L, latest.createdBy)
    }

    @Test
    fun `should reject manifest with duplicate paths`() {
        registerBlob(
            BlobRegistrationRequest(
                hash = "sha256:dup",
                algorithm = "sha256",
                sizeBytes = 100,
                storageKey = "blobs/sha256/du/pl/dup"
            )
        )

        val directory = casStorageService.createDirectory(CreateDirectoryRequest(key = "project/dup"))

        assertThrows(InvalidInputException::class.java) {
            casStorageService.publishVersion(
                PublishDirectoryVersionRequest(
                    directoryKey = directory.key,
                    branchName = "main",
                    manifest = """
                        {"files":[
                          {"path":"same.txt","hash":"sha256:dup","size":100,"storageKey":"blobs/sha256/du/pl/dup"},
                          {"path":"same.txt","hash":"sha256:dup","size":100,"storageKey":"blobs/sha256/du/pl/dup"}
                        ]}
                    """.trimIndent(),
                    metadata = "{}"
                )
            )
        }
    }

    @Test
    fun `should fork a directory into a new branch root`() {
        registerBlob(
            BlobRegistrationRequest(
                hash = "sha256:fork-a",
                algorithm = "sha256",
                sizeBytes = 333,
                storageKey = "blobs/sha256/fo/rk/fork-a"
            )
        )

        val sourceDirectory = casStorageService.createDirectory(CreateDirectoryRequest(key = "project/source"))
        val sourceVersion = casStorageService.publishVersion(
            PublishDirectoryVersionRequest(
                directoryKey = sourceDirectory.key,
                branchName = "main",
                manifest = """
                    {"files":[
                      {"path":"release.tar","hash":"sha256:fork-a","size":333,"storageKey":"blobs/sha256/fo/rk/fork-a"}
                    ]}
                """.trimIndent(),
                metadata = "{" + "\"source\":\"original\"}"
            )
        )

        val forkedVersion = casStorageService.forkDirectory(
            ForkDirectoryRequest(
                sourceDirectoryKey = sourceDirectory.key,
                sourceBranchName = "main",
                targetDirectoryKey = "project/fork",
                targetName = "Forked Project",
                targetBranchName = "main",
                metadata = "{" + "\"source\":\"forked\"}",
                createdBy = 7L,
                message = "Forked from source"
            )
        )

        val latestFork = casStorageService.getLatestVersion("project/fork", "main")
        assertEquals(forkedVersion.id, latestFork.id)
        assertEquals(sourceVersion.id, latestFork.parentVersionId)
        assertEquals(sourceVersion.manifest, latestFork.manifest)
        assertEquals("{\"source\":\"forked\"}", latestFork.metadata)
        assertEquals(7L, latestFork.createdBy)

        val branches = casStorageService.listBranches("project/fork")
        assertEquals(1, branches.size)
        assertEquals("main", branches.first().name)
        assertNotNull(branches.first().headVersionId)
    }

    @Test
    fun `should reject registration when storage object is missing`() {
        assertThrows(InvalidInputException::class.java) {
            casStorageService.registerBlob(
                BlobRegistrationRequest(
                    hash = "sha256:missing",
                    algorithm = "sha256",
                    sizeBytes = 1,
                    storageKey = "blobs/missing"
                )
            )
        }
    }

    private fun registerBlob(request: BlobRegistrationRequest) {
        val content = ByteArray(request.sizeBytes.toInt())
        storageService.upload(
            request.storageKey,
            ByteArrayInputStream(content),
            content.size.toLong(),
            "application/octet-stream"
        )
        casStorageService.registerBlob(request)
    }
}
