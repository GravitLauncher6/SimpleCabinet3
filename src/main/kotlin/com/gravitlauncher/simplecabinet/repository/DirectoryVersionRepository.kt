package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.DirectoryVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface DirectoryVersionRepository : JpaRepository<DirectoryVersion, Long> {
    @Query("select v.id from DirectoryVersion v where v.directory.id = :directoryId")
    fun findIdsByDirectoryId(directoryId: Long): List<Long>

    @Modifying
    @Query("update DirectoryVersion v set v.parentVersion = null where v.parentVersion.id in :parentIds")
    fun clearParentVersions(parentIds: List<Long>)

    @Modifying
    @Query("delete from DirectoryVersion v where v.directory.id = :directoryId")
    fun deleteByDirectoryId(directoryId: Long)

    fun findAllByDirectoryIdOrderByCreatedAtDesc(directoryId: Long): List<DirectoryVersion>
    fun findByDirectoryIdAndId(directoryId: Long, id: Long): Optional<DirectoryVersion>
}
