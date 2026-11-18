package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.DirectoryBranch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface DirectoryBranchRepository : JpaRepository<DirectoryBranch, Long> {
    @Modifying
    @Query("delete from DirectoryBranch b where b.directory.id = :directoryId")
    fun deleteByDirectoryId(directoryId: Long)

    fun findByDirectoryIdAndName(directoryId: Long, name: String): Optional<DirectoryBranch>
    fun findByDirectoryIdAndHeadVersionId(directoryId: Long, headVersionId: Long): Optional<DirectoryBranch>
    fun findAllByDirectoryId(directoryId: Long): List<DirectoryBranch>
}
