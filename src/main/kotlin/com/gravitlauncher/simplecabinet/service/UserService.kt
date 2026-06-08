package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import com.gravitlauncher.simplecabinet.repository.RoleRepository
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
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val authenticationManager: AuthenticationManager,
    private val customUserDetailsService: CustomUserDetailsService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw DuplicateResourceException(ErrorCode.USER_ALREADY_EXISTS, "Username already exists")
        }

        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException(ErrorCode.USER_ALREADY_EXISTS, "Email already exists")
        }

        val user = User(
            username = request.username,
            email = request.email,
            password = passwordEncoder.encode(request.password) ?: "",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = userRepository.save(user)
        
        // Assign default USER role
        val defaultRole = roleRepository.findByName("USER")
            .orElseGet {
                roleRepository.save(
                    com.gravitlauncher.simplecabinet.entity.Role(
                        name = "USER",
                        description = "Default user role"
                    )
                )
            }
        
        val userRole = com.gravitlauncher.simplecabinet.entity.UserRole(
            user = savedUser,
            role = defaultRole,
            assignedAt = LocalDateTime.now()
        )
        savedUser.userRoles.add(userRole)
        userRoleRepository.save(userRole)
        
        val token = jwtUtil.generateToken(savedUser.username, "USER")
        val refreshToken = refreshTokenService.createRefreshToken(savedUser)

        return AuthResponse(
            token = token,
            refreshToken = refreshToken.token,
            username = savedUser.username,
            email = savedUser.email,
            role = "USER"
        )
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )

        val user = customUserDetailsService.loadUserEntityByUsername(request.username)
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val primaryRole = activeRoles.firstOrNull()?.role?.name ?: "USER"
        
        val token = jwtUtil.generateToken(user.username, primaryRole)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        return AuthResponse(
            token = token,
            refreshToken = refreshToken.token,
            username = user.username,
            email = user.email,
            role = primaryRole
        )
    }

    @Transactional
    fun refreshToken(refreshTokenRequest: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenService.findByToken(refreshTokenRequest.refreshToken)
            .orElseThrow { ResourceNotFoundException(ErrorCode.TOKEN_NOT_FOUND, "Refresh token not found") }

        refreshTokenService.verifyExpiration(refreshToken)

        if (refreshTokenService.isRevoked(refreshToken)) {
            throw InvalidInputException(ErrorCode.INVALID_TOKEN, "Refresh token has been revoked")
        }

        val user = refreshToken.user
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val primaryRole = activeRoles.firstOrNull()?.role?.name ?: "USER"
        
        val newAccessToken = jwtUtil.generateToken(user.username, primaryRole)

        return AuthResponse(
            token = newAccessToken,
            refreshToken = refreshToken.token,
            username = user.username,
            email = user.email,
            role = primaryRole
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenService.revokeToken(refreshToken)
    }

    fun getAllUsers(): List<UserResponse> {
        return userRepository.findAll().map { user ->
            val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
            val roleNames = activeRoles.map { it.role.name }.joinToString(", ")
            
            UserResponse(
                id = user.id!!,
                username = user.username,
                email = user.email,
                role = roleNames.ifEmpty { "No roles" },
                enabled = user.enabled,
                createdAt = user.createdAt.toString(),
                updatedAt = user.updatedAt.toString()
            )
        }
    }

    fun getUserById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $id") }

        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val roleNames = activeRoles.map { it.role.name }.joinToString(", ")
        
        return UserResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = roleNames.ifEmpty { "No roles" },
            enabled = user.enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }

    fun getUserByUsername(username: String): UserResponse {
        val user = userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $username") }

        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val roleNames = activeRoles.map { it.role.name }.joinToString(", ")
        
        return UserResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = roleNames.ifEmpty { "No roles" },
            enabled = user.enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }

    @Transactional
    fun deleteUser(id: Long) {
        if (!userRepository.existsById(id)) {
            throw ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $id")
        }
        userRepository.deleteById(id)
    }
}
