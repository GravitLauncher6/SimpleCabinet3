package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {
    fun findByUser(user: User): List<UserRole>
    
    @Query("SELECT ur FROM UserRole ur WHERE ur.user = :user AND (ur.expiresAt IS NULL OR ur.expiresAt > :currentTime)")
    fun findActiveByUser(user: User, currentTime: LocalDateTime = LocalDateTime.now()): List<UserRole>
    
    fun deleteByUser(user: User)
}
