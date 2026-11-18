package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.CasDirectory
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface CasDirectoryRepository : JpaRepository<CasDirectory, Long> {
    fun findByKey(key: String): Optional<CasDirectory>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CasDirectory d where d.key = :key")
    fun findByKeyForUpdate(key: String): Optional<CasDirectory>

    fun existsByKey(key: String): Boolean
}
