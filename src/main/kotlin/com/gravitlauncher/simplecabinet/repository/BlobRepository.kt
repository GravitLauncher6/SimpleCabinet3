package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Blob
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface BlobRepository : JpaRepository<Blob, Long> {
    fun findByHash(hash: String): Optional<Blob>
    fun findByStorageKey(storageKey: String): Optional<Blob>
    fun existsByHash(hash: String): Boolean
    fun existsByStorageKey(storageKey: String): Boolean
}
