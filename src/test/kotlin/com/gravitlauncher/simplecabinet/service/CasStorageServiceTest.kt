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
import org.springframework.mock.web.MockMultipartFile
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import java.security.MessageDigest
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

    @Test
    fun `bulk upload preserves order and deduplicates content`() {
        val firstContent = "cas-bulk-first".toByteArray()
        val secondContent = "cas-bulk-second".toByteArray()
        val responses = casStorageService.bulkUpload(
            listOf(
                MockMultipartFile("files", "first.txt", "text/plain", firstContent),
                MockMultipartFile("files", "second.txt", "text/plain", secondContent),
                MockMultipartFile("files", "duplicate.txt", "text/plain", firstContent)
            )
        )

        assertEquals(listOf(hash(firstContent), hash(secondContent), hash(firstContent)), responses.map { it.hash })
        assertEquals(firstContent.size.toLong(), responses[0].sizeBytes)
        assertEquals(secondContent.size.toLong(), responses[1].sizeBytes)
        assertEquals(responses[0].id, responses[2].id)
        responses.forEach { response ->
            val storedResponse = casStorageService.getBlobByHash(response.hash)
            assertEquals(response.id, storedResponse.id)
            assertEquals(response.hash, storedResponse.hash)
            assertEquals(response.algorithm, storedResponse.algorithm)
            assertEquals(response.sizeBytes, storedResponse.sizeBytes)
            assertEquals(response.storageKey, storedResponse.storageKey)
            assertEquals(response.url, storedResponse.url)
            assertEquals(true, storageService.exists(response.storageKey))
        }
    }

    @Test
    fun `bulk upload rejects an empty batch`() {
        assertThrows(InvalidInputException::class.java) {
            casStorageService.bulkUpload(emptyList())
        }
    }

    @Test
    fun `bulk upload validates every file before storing any content`() {
        val content = "cas-bulk-invalid-batch".toByteArray()
        assertThrows(InvalidInputException::class.java) {
            casStorageService.bulkUpload(
                listOf(
                    MockMultipartFile("files", "valid.txt", "text/plain", content),
                    MockMultipartFile("files", "empty.txt", "text/plain", byteArrayOf())
                )
            )
        }
        val digest = hash(content).removePrefix("sha256:")
        assertEquals(false, storageService.exists("blobs/sha256/${digest.take(2)}/${digest.substring(2, 4)}/$digest"))
        assertThrows(ResourceNotFoundException::class.java) {
            casStorageService.getBlobByHash(hash(content))
        }
    }

    private fun hash(content: ByteArray): String =
        "sha256:" + MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

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
