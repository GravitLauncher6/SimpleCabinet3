package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {
    fun findByUser(user: User): List<UserRole>
    fun existsByUserAndRole(user: User, role: Role): Boolean
    
    @Query(
        """
        SELECT DISTINCT ur
        FROM UserRole ur
        JOIN FETCH ur.role
        WHERE ur.user = :user
          AND (ur.expiresAt IS NULL OR ur.expiresAt > :currentTime)
        """
    )
    fun findActiveByUser(user: User, currentTime: LocalDateTime = LocalDateTime.now()): List<UserRole>
    
    fun deleteByUser(user: User)
}
