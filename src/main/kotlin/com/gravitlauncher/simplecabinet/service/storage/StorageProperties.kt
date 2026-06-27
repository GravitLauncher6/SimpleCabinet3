package com.gravitlauncher.simplecabinet.service.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
data class StorageProperties(
    val type: StorageType = StorageType.LOCAL,
    val local: LocalStorageProperties = LocalStorageProperties(),
    val s3: S3StorageProperties = S3StorageProperties()
)

enum class StorageType {
    LOCAL,
    S3
}

data class LocalStorageProperties(
    val path: String = "uploads",
    val baseUrl: String = "http://localhost:8080/files"
)

data class S3StorageProperties(
    val accessKey: String = "",
    val secretKey: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val prefix: String = "",
    val endpoint: String = ""
)
