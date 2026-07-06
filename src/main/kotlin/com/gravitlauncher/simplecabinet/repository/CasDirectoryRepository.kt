package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.CasDirectory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface CasDirectoryRepository : JpaRepository<CasDirectory, Long> {
    fun findByKey(key: String): Optional<CasDirectory>
    fun existsByKey(key: String): Boolean
}
