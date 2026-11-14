package com.gravitlauncher.simplecabinet.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.Blob
import com.gravitlauncher.simplecabinet.entity.CasDirectory
import com.gravitlauncher.simplecabinet.entity.DirectoryBranch
import com.gravitlauncher.simplecabinet.entity.DirectoryVersion
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.repository.BlobRepository
import com.gravitlauncher.simplecabinet.repository.CasDirectoryRepository
import com.gravitlauncher.simplecabinet.repository.DirectoryBranchRepository
import com.gravitlauncher.simplecabinet.repository.DirectoryVersionRepository
import com.gravitlauncher.simplecabinet.service.storage.StorageService
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant

@Service
class CasStorageService(
    private val blobRepository: BlobRepository,
    private val casDirectoryRepository: CasDirectoryRepository,
    private val directoryVersionRepository: DirectoryVersionRepository,
    private val directoryBranchRepository: DirectoryBranchRepository,
    private val storageService: StorageService
) {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Transactional
    fun registerBlob(request: BlobRegistrationRequest): BlobResponse {
        val hash = request.hash.trim()
        val algorithm = request.algorithm.trim()
        val storageKey = request.storageKey.trim()

        if (hash.isEmpty() || algorithm.isEmpty() || storageKey.isEmpty()) {
            throw InvalidInputException(message = "Blob hash, algorithm and storage key are required")
        }
        if (request.sizeBytes < 0) {
            throw InvalidInputException(message = "Blob size must be non-negative")
        }

        val existingBlob = blobRepository.findByHash(hash).orElse(null)
        if (existingBlob != null) {
            if (existingBlob.storageKey != storageKey || existingBlob.sizeBytes != request.sizeBytes || existingBlob.algorithm != algorithm) {
                throw DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Blob hash already exists with different metadata: $hash"
                )
            }
            return toBlobResponse(existingBlob)
        }

        blobRepository.findByStorageKey(storageKey).ifPresent {
            throw DuplicateResourceException(
                ErrorCode.DUPLICATE_RESOURCE,
                "Storage key already exists: $storageKey"
            )
        }
        if (!storageService.exists(storageKey)) {
            throw InvalidInputException(message = "Storage object does not exist: $storageKey")
        }

        return toBlobResponse(
            blobRepository.save(
                Blob(
                    hash = hash,
                    algorithm = algorithm,
                    sizeBytes = request.sizeBytes,
                    storageKey = storageKey
                )
            )
        )
    }

    @Transactional(readOnly = true)
    fun getBlobByHash(hash: String): BlobResponse {
        return toBlobResponse(
            blobRepository.findByHash(hash.trim())
                .orElseThrow { ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Blob not found: $hash") }
        )
    }

    @Transactional
    fun upload(file: MultipartFile): BlobResponse {
        if (file.isEmpty) {
            throw InvalidInputException(message = "File is required")
        }

        val temporaryFile = Files.createTempFile("simplecabinet-cas-", ".upload")
        try {
            val messageDigest = MessageDigest.getInstance(SHA_256)
            var sizeBytes = 0L
            file.inputStream.use { rawInput ->
                BufferedInputStream(rawInput).use { input ->
                    Files.newOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            messageDigest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            sizeBytes += read
                        }
                    }
                }
            }

            val digest = messageDigest.digest().toHex()
            val hash = "sha256:$digest"
            val existingBlob = blobRepository.findByHash(hash).orElse(null)
            if (existingBlob != null) {
                return toBlobResponse(existingBlob)
            }

            val requestedKey = "blobs/sha256/${digest.take(2)}/${digest.substring(2, 4)}/$digest"
            val storageKey = Files.newInputStream(temporaryFile).use { input ->
                storageService.upload(
                    requestedKey,
                    input,
                    sizeBytes,
                    file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
                )
            }
            return toBlobResponse(
                blobRepository.save(
                    Blob(
                        hash = hash,
                        algorithm = SHA_256,
                        sizeBytes = sizeBytes,
                        storageKey = storageKey
                    )
                )
            )
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    @Transactional
    fun bulkUpload(files: List<MultipartFile>): List<BlobResponse> {
        if (files.isEmpty()) {
            throw InvalidInputException(message = "At least one file is required")
        }
        if (files.any { it.isEmpty }) {
            throw InvalidInputException(message = "Files must not be empty")
        }
        return files.map(::upload)
    }

    @Transactional
    fun createDirectory(request: CreateDirectoryRequest): DirectoryResponse {
        val key = request.key.trim()
        val name = request.name?.trim()?.takeIf { it.isNotEmpty() }

        if (key.isEmpty()) {
            throw InvalidInputException(message = "Directory key is required")
        }
        if (casDirectoryRepository.existsByKey(key)) {
            throw DuplicateResourceException(ErrorCode.DUPLICATE_RESOURCE, "Directory already exists: $key")
        }

        val metadata = canonicalizeMetadata(request.metadata)
        return toDirectoryResponse(
            casDirectoryRepository.save(CasDirectory(key = key, name = name, metadata = metadata))
        )
    }

    @Transactional(readOnly = true)
    fun getDirectory(key: String): DirectoryResponse {
        return toDirectoryResponse(getDirectoryEntity(key))
    }

    @Transactional(readOnly = true)
    fun getDirectories(): List<DirectoryResponse> {
        return casDirectoryRepository.findAll().map { toDirectoryResponse(it) };
    }

    @Transactional
    fun updateDirectoryMetadata(key: String, request: UpdateDirectoryMetadataRequest): DirectoryResponse {
        val directory = getDirectoryEntity(key)
        val metadata = canonicalizeMetadata(request.metadata)
        return toDirectoryResponse(casDirectoryRepository.save(directory.copy(metadata = metadata)))
    }

    @Transactional(readOnly = true)
    fun listBranches(directoryKey: String): List<DirectoryBranchResponse> {
        val directory = getDirectoryEntity(directoryKey)
        return directoryBranchRepository.findAllByDirectoryId(directory.id!!)
            .map { toBranchResponse(it) }
    }

    @Transactional
    fun publishVersion(request: PublishDirectoryVersionRequest): DirectoryVersionResponse {
        val directory = getDirectoryEntity(request.directoryKey)
        val branchName = request.branchName.trim()
        if (branchName.isEmpty()) {
            throw InvalidInputException(message = "Branch name is required")
        }

        val metadata = canonicalizeMetadata(request.metadata)
        val manifest = canonicalizeAndValidateManifest(request.manifest)
        val manifestHash = buildManifestHash(manifest)

        val branch = directoryBranchRepository.findByDirectoryIdAndName(directory.id!!, branchName).orElse(null)
        val currentHead = branch?.headVersion

        if (request.expectedParentVersionId != null && currentHead?.id != request.expectedParentVersionId) {
            throw InvalidInputException(
                message = "Branch head changed. Expected parent ${request.expectedParentVersionId}, actual ${currentHead?.id}"
            )
        }

        val version = directoryVersionRepository.save(
            DirectoryVersion(
                directory = directory,
                parentVersion = currentHead,
                manifestHash = manifestHash,
                manifest = manifest,
                metadata = metadata,
                createdAt = Instant.now(),
                createdBy = request.createdBy,
                message = request.message?.trim()?.takeIf { it.isNotEmpty() }
            )
        )

        val savedBranch = directoryBranchRepository.save(
            if (branch == null) {
                DirectoryBranch(
                    directory = directory,
                    name = branchName,
                    headVersion = version,
                    updatedAt = Instant.now()
                )
            } else {
                branch.copy(headVersion = version, updatedAt = Instant.now())
            }
        )

        return toVersionResponse(version, savedBranch.name)
    }

    @Transactional(readOnly = true)
    fun getLatestVersion(directoryKey: String, branchName: String): DirectoryVersionResponse {
        val directory = getDirectoryEntity(directoryKey)
        val branch = directoryBranchRepository.findByDirectoryIdAndName(directory.id!!, branchName.trim())
            .orElseThrow {
                ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Branch not found: ${directory.key}/${branchName.trim()}"
                )
            }

        val headVersion = branch.headVersion
            ?: throw ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Branch has no published version")

        return toVersionResponse(headVersion, branch.name)
    }

    @Transactional(readOnly = true)
    fun getVersion(directoryKey: String, versionId: Long): DirectoryVersionResponse {
        val directory = getDirectoryEntity(directoryKey)
        val version = directoryVersionRepository.findByDirectoryIdAndId(directory.id!!, versionId)
            .orElseThrow {
                ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Version not found: ${directory.key}/$versionId"
                )
            }

        val branchName = directoryBranchRepository.findByDirectoryIdAndHeadVersionId(directory.id!!, version.id!!)
            .map { it.name }
            .orElse(null)

        return toVersionResponse(version, branchName)
    }

    @Transactional
    fun forkDirectory(request: ForkDirectoryRequest): DirectoryVersionResponse {
        val sourceVersion = getLatestVersion(request.sourceDirectoryKey, request.sourceBranchName)
        val targetDirectory = createDirectory(
            CreateDirectoryRequest(
                key = request.targetDirectoryKey,
                name = request.targetName
            )
        )

        val sourceVersionEntity = directoryVersionRepository.findById(sourceVersion.id)
            .orElseThrow {
                ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Source version not found: ${sourceVersion.id}")
            }

        val targetDirectoryEntity = casDirectoryRepository.findById(targetDirectory.id)
            .orElseThrow {
                ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Target directory not found: ${targetDirectory.id}")
            }

        val normalizedMetadata = canonicalizeMetadata(request.metadata)
        val forkVersion = directoryVersionRepository.save(
            DirectoryVersion(
                directory = targetDirectoryEntity,
                parentVersion = sourceVersionEntity,
                manifestHash = sourceVersion.manifestHash,
                manifest = sourceVersion.manifest,
                metadata = normalizedMetadata,
                createdAt = Instant.now(),
                createdBy = request.createdBy,
                message = request.message?.trim()?.takeIf { it.isNotEmpty() }
            )
        )

        directoryBranchRepository.save(
            DirectoryBranch(
                directory = targetDirectoryEntity,
                name = request.targetBranchName.trim(),
                headVersion = forkVersion,
                updatedAt = Instant.now()
            )
        )

        return toVersionResponse(forkVersion, request.targetBranchName.trim())
    }

    private fun getDirectoryEntity(key: String): CasDirectory {
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty()) {
            throw InvalidInputException(message = "Directory key is required")
        }
        return casDirectoryRepository.findByKey(normalizedKey)
            .orElseThrow {
                ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Directory not found: $normalizedKey")
            }
    }

    private fun canonicalizeAndValidateManifest(rawManifest: String): String {
        val manifestNode = parseJson(rawManifest, "Manifest must be valid JSON")
        val filesNode = when {
            manifestNode.isArray -> manifestNode
            manifestNode.isObject && manifestNode.has("files") && manifestNode.get("files").isArray -> manifestNode.get("files")
            else -> throw InvalidInputException(message = "Manifest must be a JSON array or an object with a files array")
        }

        val files = filesNode.mapIndexed { index, node ->
            if (!node.isObject) {
                throw InvalidInputException(message = "Manifest file entry at index $index must be an object")
            }

            val path = node.path("path").asText().trim()
            val hash = node.path("hash").asText().trim()
            val storageKey = node.path("storageKey").asText().trim()
            val sizeNode = node.get("size")

            if (path.isEmpty() || hash.isEmpty() || storageKey.isEmpty() || sizeNode == null || !sizeNode.isIntegralNumber) {
                throw InvalidInputException(
                    message = "Manifest entries must include non-empty path, hash, storageKey and integral size"
                )
            }

            val size = sizeNode.asLong()
            if (size < 0) {
                throw InvalidInputException(message = "Manifest file size must be non-negative")
            }

            val blob = blobRepository.findByHash(hash).orElseThrow {
                ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Blob not found for hash: $hash")
            }

            if (blob.storageKey != storageKey || blob.sizeBytes != size) {
                throw InvalidInputException(
                    message = "Manifest entry for $path does not match registered blob metadata"
                )
            }

            CanonicalManifestFile(
                path = path,
                hash = hash,
                size = size,
                storageKey = storageKey,
                url = storageService.getUrl(storageKey)
            )
        }

        val duplicatePaths = files.groupingBy { it.path }.eachCount().filterValues { it > 1 }.keys
        if (duplicatePaths.isNotEmpty()) {
            throw InvalidInputException(message = "Manifest contains duplicate paths: ${duplicatePaths.sorted().joinToString(", ")}")
        }

        val root = objectMapper.createObjectNode()
        val normalizedFiles = objectMapper.createArrayNode()
        files.sortedBy { it.path }.forEach { file ->
            normalizedFiles.add(
                objectMapper.createObjectNode()
                    .put("path", file.path)
                    .put("hash", file.hash)
                    .put("size", file.size)
                    .put("storageKey", file.storageKey)
                    .put("url", file.url)
            )
        }
        root.set<ArrayNode>("files", normalizedFiles)
        return objectMapper.writeValueAsString(root)
    }

    private fun canonicalizeMetadata(rawMetadata: String): String {
        val node = parseJson(rawMetadata, "Metadata must be valid JSON")
        if (!node.isObject) {
            throw InvalidInputException(message = "Metadata must be a JSON object")
        }
        return objectMapper.writeValueAsString(node)
    }

    private fun parseJson(rawJson: String, errorMessage: String): JsonNode {
        try {
            return objectMapper.readTree(rawJson)
        } catch (_: Exception) {
            throw InvalidInputException(message = errorMessage)
        }
    }

    private fun buildManifestHash(manifest: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(manifest.toByteArray(StandardCharsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun toBlobResponse(blob: Blob): BlobResponse {
        return BlobResponse(
            id = blob.id ?: error("blob id missing"),
            hash = blob.hash,
            algorithm = blob.algorithm,
            sizeBytes = blob.sizeBytes,
            storageKey = blob.storageKey,
            url = storageService.getUrl(blob.storageKey),
            createdAt = blob.createdAt
        )
    }

    private fun toDirectoryResponse(directory: CasDirectory): DirectoryResponse {
        return DirectoryResponse(
            id = directory.id ?: error("directory id missing"),
            key = directory.key,
            name = directory.name,
            metadata = directory.metadata,
            createdAt = directory.createdAt
        )
    }

    private fun toVersionResponse(version: DirectoryVersion, branchName: String?): DirectoryVersionResponse {
        val directory = version.directory
        return DirectoryVersionResponse(
            id = version.id ?: error("version id missing"),
            directoryId = directory.id ?: error("directory id missing"),
            directoryKey = directory.key,
            branchName = branchName,
            parentVersionId = version.parentVersion?.id,
            manifestHash = version.manifestHash,
            manifest = version.manifest,
            metadata = version.metadata,
            createdAt = version.createdAt,
            createdBy = version.createdBy,
            message = version.message
        )
    }

    private fun toBranchResponse(branch: DirectoryBranch): DirectoryBranchResponse {
        val directory = branch.directory
        return DirectoryBranchResponse(
            id = branch.id ?: error("branch id missing"),
            directoryId = directory.id ?: error("directory id missing"),
            directoryKey = directory.key,
            name = branch.name,
            headVersionId = branch.headVersion?.id,
            updatedAt = branch.updatedAt
        )
    }

    private data class CanonicalManifestFile(
        val path: String,
        val hash: String,
        val size: Long,
        val storageKey: String,
        val url: String = ""
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val SHA_256 = "SHA-256"
        const val STREAM_BUFFER_SIZE = 64 * 1024
    }
}
