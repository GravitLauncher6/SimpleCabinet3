package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.security.JwtUtil
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val authenticationManager: AuthenticationManager,
    private val customUserDetailsService: CustomUserDetailsService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username already exists")
        }

        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user = User(
            username = request.username,
            email = request.email,
            password = passwordEncoder.encode(request.password) ?: "",
            role = UserRole.USER,
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = userRepository.save(user)
        val token = jwtUtil.generateToken(savedUser.username, savedUser.role.name)
        val refreshToken = refreshTokenService.createRefreshToken(savedUser)

        return AuthResponse(
            token = token,
            refreshToken = refreshToken.token,
            username = savedUser.username,
            email = savedUser.email,
            role = savedUser.role.name
        )
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )

        val user = customUserDetailsService.loadUserEntityByUsername(request.username)
        val token = jwtUtil.generateToken(user.username, user.role.name)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        return AuthResponse(
            token = token,
            refreshToken = refreshToken.token,
            username = user.username,
            email = user.email,
            role = user.role.name
        )
    }

    @Transactional
    fun refreshToken(refreshTokenRequest: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenService.findByToken(refreshTokenRequest.refreshToken)
            .orElseThrow { IllegalArgumentException("Refresh token not found") }

        refreshTokenService.verifyExpiration(refreshToken)

        if (refreshTokenService.isRevoked(refreshToken)) {
            throw IllegalArgumentException("Refresh token has been revoked")
        }

        val user = refreshToken.user
        val newAccessToken = jwtUtil.generateToken(user.username, user.role.name)

        return AuthResponse(
            token = newAccessToken,
            refreshToken = refreshToken.token,
            username = user.username,
            email = user.email,
            role = user.role.name
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenService.revokeToken(refreshToken)
    }

    fun getAllUsers(): List<UserResponse> {
        return userRepository.findAll().map { user ->
            UserResponse(
                id = user.id!!,
                username = user.username,
                email = user.email,
                role = user.role.name,
                enabled = user.enabled,
                createdAt = user.createdAt.toString(),
                updatedAt = user.updatedAt.toString()
            )
        }
    }

    fun getUserById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { NoSuchElementException("User not found with id: $id") }

        return UserResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = user.role.name,
            enabled = user.enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }

    fun getUserByUsername(username: String): UserResponse {
        val user = userRepository.findByUsername(username)
            .orElseThrow { NoSuchElementException("User not found: $username") }

        return UserResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = user.role.name,
            enabled = user.enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }

    @Transactional
    fun deleteUser(id: Long) {
        if (!userRepository.existsById(id)) {
            throw NoSuchElementException("User not found with id: $id")
        }
        userRepository.deleteById(id)
    }
}
