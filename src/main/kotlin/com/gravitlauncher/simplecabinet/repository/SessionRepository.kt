package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Session
import com.gravitlauncher.simplecabinet.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface SessionRepository : JpaRepository<Session, Long> {
    fun findBySessionToken(sessionToken: String): Optional<Session>
    fun findByUserIdAndServerId(userId: Long, serverId: String): Optional<Session>
    
    fun findByUser(user: User): List<Session>
    
    @Query("SELECT s FROM Session s WHERE s.user = :user AND s.revoked = false AND s.expiresAt > :now")
    fun findActiveSessionsByUser(user: User, now: Instant): List<Session>
    
    @Query("SELECT s FROM Session s WHERE s.user.id = :userId AND s.revoked = false AND s.expiresAt > :now")
    fun findActiveSessionsByUserId(userId: Long, now: Instant): List<Session>
    
    @Modifying
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason WHERE s.user.id = :userId AND s.revoked = false")
    fun revokeAllUserSessions(userId: Long, revokedAt: Instant, reason: String)
    
    @Modifying
    @Query("DELETE FROM Session s WHERE s.user.id = :userId")
    fun deleteByUserId(userId: Long)
    
    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    fun deleteExpiredSessions(now: Instant)
    
    @Modifying
    @Query("DELETE FROM Session s WHERE s.revoked = true AND s.revokedAt < :before")
    fun deleteRevokedSessionsBefore(before: Instant)
}
