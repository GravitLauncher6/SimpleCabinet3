package com.gravitlauncher.simplecabinet.service.storage

import com.gravitlauncher.simplecabinet.repository.ArtifactBuildRepository
import com.gravitlauncher.simplecabinet.repository.BlobRepository
import org.springframework.stereotype.Service

@Service
class StorageReferenceService(
    private val blobRepository: BlobRepository,
    private val artifactBuildRepository: ArtifactBuildRepository,
    private val storageService: StorageService
) {
    fun deleteIfUnreferenced(storageKey: String) {
        if (blobRepository.existsByStorageKey(storageKey)) return
        if (artifactBuildRepository.existsByStorageKey(storageKey)) return
        storageService.delete(storageKey)
    }
}
