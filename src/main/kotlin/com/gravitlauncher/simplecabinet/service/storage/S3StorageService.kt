package com.gravitlauncher.simplecabinet.service.storage

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import java.io.InputStream
import java.net.URI

class S3StorageService(
    private val storageProperties: StorageProperties
) : StorageService {

    private val s3Client: S3Client

    init {
        val s3Config = storageProperties.s3
        val credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(s3Config.accessKey, s3Config.secretKey)
        )

        val clientBuilder = S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(s3Config.region))

        // Support custom endpoint for S3-compatible services (MinIO, etc.)
        if (s3Config.endpoint.isNotEmpty()) {
            clientBuilder.endpointOverride(URI.create(s3Config.endpoint))
        }

        s3Client = clientBuilder.build()
    }

    override fun upload(
        filename: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String?
    ): String {
        try {
            val bucket = storageProperties.s3.bucket
            val key = if (storageProperties.s3.prefix.isNotEmpty()) {
                "${storageProperties.s3.prefix}/$filename"
            } else {
                filename
            }

            val requestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)

            if (contentType != null) {
                requestBuilder.contentType(contentType)
            }

            val putObjectRequest = requestBuilder.build()

            inputStream.use { input ->
                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(input, contentLength))
            }

            return filename
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload file to S3: ${e.message}", e)
        }
    }

    override fun delete(filename: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(storageProperties.s3.bucket)
                .key(resolveKey(filename))
                .build()
        )
    }

    override fun exists(filename: String): Boolean {
        try {
            val bucket = storageProperties.s3.bucket
            val key = resolveKey(filename)

            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()

            s3Client.headObject(headObjectRequest)
            return true
        } catch (e: NoSuchKeyException) {
            return false
        } catch (e: Exception) {
            return false
        }
    }

    override fun getUrl(filename: String): String {
        val bucket = storageProperties.s3.bucket
        val key = resolveKey(filename)

        return if (storageProperties.s3.endpoint.isNotEmpty()) {
            // Custom endpoint (e.g., MinIO)
            val endpoint = storageProperties.s3.endpoint.removeSuffix("/")
            "$endpoint/$bucket/$key"
        } else {
            // Standard AWS S3 URL
            "https://$bucket.s3.${storageProperties.s3.region}.amazonaws.com/$key"
        }
    }

    private fun resolveKey(filename: String): String {
        val prefix = storageProperties.s3.prefix.trim('/')
        return if (prefix.isNotEmpty() && !filename.startsWith("$prefix/")) "$prefix/$filename" else filename
    }
}
