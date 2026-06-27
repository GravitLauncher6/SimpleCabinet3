package com.gravitlauncher.simplecabinet.service.storage

import java.io.InputStream

interface StorageService {
    /**
     * Upload a file to storage
     * @param filename The name/path of the file
     * @param inputStream The file content as InputStream
     * @param contentType The MIME type of the file (optional)
     * @return The storage path/key of the uploaded file
     */
    fun upload(filename: String, inputStream: InputStream, contentType: String? = null): String

    /**
     * Check if a file exists in storage
     * @param filename The name/path of the file
     * @return true if the file exists, false otherwise
     */
    fun exists(filename: String): Boolean

    /**
     * Get the URL of a file without downloading it
     * @param filename The name/path of the file
     * @return The URL to access the file
     */
    fun getUrl(filename: String): String
}
