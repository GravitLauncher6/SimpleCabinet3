package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.DirectoryBranch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DirectoryBranchRepository : JpaRepository<DirectoryBranch, Long> {
    fun findByDirectoryIdAndName(directoryId: Long, name: String): Optional<DirectoryBranch>
    fun findByDirectoryIdAndHeadVersionId(directoryId: Long, headVersionId: Long): Optional<DirectoryBranch>
    fun findAllByDirectoryId(directoryId: Long): List<DirectoryBranch>
}
