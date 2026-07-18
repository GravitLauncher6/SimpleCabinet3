package com.gravitlauncher.simplecabinet.service.storage

import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class LocalFileStorageService(
    private val storageProperties: StorageProperties
) : StorageService {

    private val rootLocation: Path = Paths.get(storageProperties.local.path)

    init {
        try {
            Files.createDirectories(rootLocation)
        } catch (e: Exception) {
            throw RuntimeException("Could not initialize storage location: ${e.message}", e)
        }
    }

    override fun upload(
        filename: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String?
    ): String {
        try {
            if (filename.contains("..")) {
                throw InvalidInputException(ErrorCode.INVALID_INPUT, "Cannot store file with relative path outside current directory")
            }

            val destinationFile = rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath()
            
            if (!destinationFile.startsWith(rootLocation.toAbsolutePath())) {
                throw InvalidInputException(ErrorCode.INVALID_INPUT, "Cannot store file outside of storage directory")
            }

            // Create parent directories if they don't exist
            destinationFile.parent?.let { Files.createDirectories(it) }

            inputStream.use { input ->
                Files.copy(input, destinationFile, StandardCopyOption.REPLACE_EXISTING)
            }

            return filename
        } catch (e: Exception) {
            if (e is InvalidInputException) throw e
            throw RuntimeException("Failed to store file: ${e.message}", e)
        }
    }

    override fun exists(filename: String): Boolean {
        try {
            val file = rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath()
            return Files.exists(file) && Files.isRegularFile(file)
        } catch (e: Exception) {
            return false
        }
    }

    override fun delete(filename: String) {
        val file = resolveFile(filename)
        Files.deleteIfExists(file)
    }

    override fun getUrl(filename: String): String {
        // For local storage, return a URL that can be served by the application
        // Format: /files/{filename}
        val baseUrl = storageProperties.local.baseUrl
        return "$baseUrl/$filename"
    }

    private fun resolveFile(filename: String): Path {
        if (filename.contains("..")) {
            throw InvalidInputException(ErrorCode.INVALID_INPUT, "Cannot access a relative path outside storage")
        }
        val file = rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath()
        if (!file.startsWith(rootLocation.toAbsolutePath())) {
            throw InvalidInputException(ErrorCode.INVALID_INPUT, "Cannot access a file outside storage")
        }
        return file
    }
}
