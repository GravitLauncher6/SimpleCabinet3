package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.Session
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.repository.SessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class SessionService(
    private val sessionRepository: SessionRepository
) {

    @Value("\${jwt.session-expiration:2592000000}") // Default 30 days in milliseconds
    private var sessionDurationMs: Long = 2592000000

    @Value("\${session.max-per-user:10}")
    private var maxSessionsPerUser: Int = 10

    /**
     * Create a new session for a user
     */
    @Transactional
    fun createSession(
        user: User,
        ipAddress: String? = null,
        userAgent: String? = null,
        deviceType: String? = null,
        externalProviderId: String? = null,
        externalState: String? = null
    ): Session {
        // Clean up old sessions if max limit reached
        val activeSessions = sessionRepository.findActiveSessionsByUser(user, Instant.now())
            .filter { it.deviceType != Session.ADMIN_CLI_DEVICE_TYPE }
        if (activeSessions.size >= maxSessionsPerUser) {
            // Revoke the oldest session
            val oldestSession = activeSessions.minByOrNull { it.createdAt }
            oldestSession?.let {
                revokeSession(it.sessionToken, "Maximum sessions limit reached")
            }
        }

        val session = Session(
            sessionToken = generateSessionToken(),
            user = user,
            expiresAt = Instant.now().plusMillis(sessionDurationMs),
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceType = deviceType,
            externalProviderId = externalProviderId,
            externalState = externalState
        )

        return sessionRepository.save(session)
    }

    /**
     * Find session by token
     */
    fun findByToken(token: String): Optional<Session> {
        return sessionRepository.findBySessionToken(token)
    }

    /**
     * Verify session is valid (not expired, not revoked)
     */
    fun verifySession(session: Session): Session {
        if (session.revoked) {
            throw InvalidInputException(
                ErrorCode.INVALID_TOKEN,
                "Session has been revoked: ${session.revokedReason ?: "Unknown reason"}"
            )
        }

        if (session.expiresAt.isBefore(Instant.now())) {
            sessionRepository.delete(session)
            throw InvalidInputException(ErrorCode.TOKEN_EXPIRED, "Session has expired")
        }

        return session
    }

    /**
     * Update last accessed time
     */
    @Transactional
    fun updateLastAccessed(sessionToken: String): Session {
        val session = findByToken(sessionToken)
            .orElseThrow { ResourceNotFoundException(ErrorCode.TOKEN_NOT_FOUND, "Session not found") }
        
        verifySession(session)
        
        // Create updated session (since it's a data class)
        val updatedSession = session.copy(lastAccessedAt = Instant.now())
        return sessionRepository.save(updatedSession)
    }

    @Transactional
    fun updateExternalState(session: Session, externalState: String): Session {
        val updatedSession = session.copy(
            externalState = externalState,
            lastAccessedAt = Instant.now()
        )
        return sessionRepository.save(updatedSession)
    }

    @Transactional
    fun bindServer(sessionToken: String, serverId: String): Session {
        val session = findByToken(sessionToken)
            .orElseThrow { ResourceNotFoundException(ErrorCode.TOKEN_NOT_FOUND, "Session not found") }

        verifySession(session)

        val updatedSession = session.copy(
            serverId = serverId,
            lastAccessedAt = Instant.now()
        )
        return sessionRepository.save(updatedSession)
    }

    fun findByUserAndServerId(userId: Long, serverId: String): Optional<Session> {
        return sessionRepository.findByUserIdAndServerId(userId, serverId)
    }

    /**
     * Revoke a specific session
     */
    @Transactional
    fun revokeSession(sessionToken: String, reason: String = "User logged out") {
        val session = sessionRepository.findBySessionToken(sessionToken)
        if (session.isPresent) {
            val s = session.get()
            val revokedSession = s.copy(
                revoked = true,
                revokedAt = Instant.now(),
                revokedReason = reason
            )
            sessionRepository.save(revokedSession)
        }
    }

    /**
     * Revoke all sessions for a user
     */
    @Transactional
    fun revokeAllUserSessions(userId: Long, reason: String = "All sessions revoked") {
        sessionRepository.revokeAllUserSessions(userId, Instant.now(), reason)
    }

    /**
     * Get all active sessions for a user
     */
    fun getActiveUserSessions(userId: Long): List<Session> {
        return sessionRepository.findActiveSessionsByUserId(userId, Instant.now())
    }

    /**
     * Get all sessions for a user (including expired/revoked)
     */
    fun getAllUserSessions(user: User): List<Session> {
        return sessionRepository.findByUser(user)
    }

    /**
     * Delete all sessions for a user
     */
    @Transactional
    fun deleteAllUserSessions(userId: Long) {
        sessionRepository.deleteByUserId(userId)
    }

    /**
     * Clean up expired sessions (for scheduled task)
     */
    @Transactional
    fun deleteExpiredSessions() {
        sessionRepository.deleteExpiredSessions(Instant.now())
    }

    /**
     * Clean up old revoked sessions (for scheduled task)
     */
    @Transactional
    fun deleteOldRevokedSessions(daysOld: Long = 7) {
        val cutoffDate = Instant.now().minusMillis(daysOld * 24 * 60 * 60 * 1000)
        sessionRepository.deleteRevokedSessionsBefore(cutoffDate)
    }

    /**
     * Check if session is revoked
     */
    fun isRevoked(session: Session): Boolean {
        return session.revoked
    }

    /**
     * Check if session is expired
     */
    fun isExpired(session: Session): Boolean {
        return session.expiresAt.isBefore(Instant.now())
    }

    /**
     * Generate a unique session token
     */
    private fun generateSessionToken(): String {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString()
    }
}
