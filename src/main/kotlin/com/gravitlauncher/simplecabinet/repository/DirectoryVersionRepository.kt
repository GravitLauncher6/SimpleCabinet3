package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.DirectoryVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DirectoryVersionRepository : JpaRepository<DirectoryVersion, Long> {
    fun findAllByDirectoryIdOrderByCreatedAtDesc(directoryId: Long): List<DirectoryVersion>
    fun findByDirectoryIdAndId(directoryId: Long, id: Long): Optional<DirectoryVersion>
}
