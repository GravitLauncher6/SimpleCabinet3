package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.RefreshToken
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.repository.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository
) {

    @Value("\${jwt.refresh-expiration}")
    private var refreshTokenDurationMs: Long = 604800000

    fun createRefreshToken(user: User): RefreshToken {
        val refreshToken = RefreshToken(
            token = UUID.randomUUID().toString(),
            user = user,
            expiryDate = Instant.now().plusMillis(refreshTokenDurationMs)
        )

        return refreshTokenRepository.save(refreshToken)
    }

    fun findByToken(token: String): Optional<RefreshToken> {
        return refreshTokenRepository.findByToken(token)
    }

    fun verifyExpiration(token: RefreshToken): RefreshToken {
        if (token.expiryDate.isBefore(Instant.now())) {
            refreshTokenRepository.delete(token)
            throw InvalidInputException(ErrorCode.TOKEN_EXPIRED, "Refresh token was expired. Please make a new signin request")
        }
        return token
    }

    fun isRevoked(token: RefreshToken): Boolean {
        return token.revoked
    }

    @Transactional
    fun revokeToken(token: String) {
        val refreshToken = refreshTokenRepository.findByToken(token)
        if (refreshToken.isPresent) {
            val rt = refreshToken.get()
            rt.revoked = true
            refreshTokenRepository.save(rt)
        }
    }

    @Transactional
    fun revokeAllUserTokens(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
    }

    @Transactional
    fun deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens()
    }
}
