package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserType
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.exception.UnauthorizedException
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.security.JwtUtil
import com.gravitlauncher.simplecabinet.service.auth.ExternalAuthService
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
    private val sessionService: SessionService,
    private val externalAuthService: ExternalAuthService
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
        val session = sessionService.createSession(savedUser)

        return AuthResponse(
            token = token,
            refreshToken = session.sessionToken,
            username = savedUser.username,
            email = savedUser.email,
            role = "USER"
        )
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        // Check if user exists in local database
        val userOptional = userRepository.findByUsername(request.username)
        
        if (userOptional.isPresent) {
            val user = userOptional.get()
            
            // Handle external users
            if (user.userType == UserType.EXTERNAL) {
                return loginExternalUser(user, request.password)
            }
            
            // Handle local users
            return loginLocalUser(request)
        } else {
            // User not found locally - try external authentication
            return tryExternalUserLogin(request)
        }
    }
    
    private fun loginLocalUser(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )

        val user = customUserDetailsService.loadUserEntityByUsername(request.username)
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val primaryRole = activeRoles.firstOrNull()?.role?.name ?: "USER"
        
        val token = jwtUtil.generateToken(user.username, primaryRole)
        val session = sessionService.createSession(user)

        return AuthResponse(
            token = token,
            refreshToken = session.sessionToken,
            username = user.username,
            email = user.email,
            role = primaryRole
        )
    }
    
    private fun loginExternalUser(user: User, password: String): AuthResponse {
        // Authenticate with external system
        val externalResult = externalAuthService.authenticate(user.username, password)
        
        if (!externalResult.success) {
            throw UnauthorizedException(
                ErrorCode.INVALID_CREDENTIALS,
                externalResult.errorMessage ?: "External authentication failed"
            )
        }
        
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val primaryRole = activeRoles.firstOrNull()?.role?.name ?: "USER"
        
        val token = jwtUtil.generateToken(user.username, primaryRole)
        val session = sessionService.createSession(user)
        
        // Notify external system about the refresh token
        externalAuthService.notifyRefreshToken(user.username, session.sessionToken)

        return AuthResponse(
            token = token,
            refreshToken = session.sessionToken,
            username = user.username,
            email = user.email,
            role = primaryRole
        )
    }
    
    @Transactional
    private fun tryExternalUserLogin(request: LoginRequest): AuthResponse {
        // Try to authenticate with external system
        val externalResult = externalAuthService.authenticate(request.username, request.password)
        
        if (!externalResult.success) {
            throw UnauthorizedException(
                ErrorCode.INVALID_CREDENTIALS,
                externalResult.errorMessage ?: "Invalid credentials"
            )
        }
        
        // Create external user in local database
        val user = User(
            username = externalResult.username ?: request.username,
            email = externalResult.email ?: "${request.username}@external.local",
            password = passwordEncoder.encode("external-user-no-password") ?: "",
            userType = UserType.EXTERNAL,
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val savedUser = userRepository.save(user)
        
        // Assign roles from external system or default role
        val rolesToAssign = if (externalResult.roles.isNotEmpty()) {
            externalResult.roles
        } else {
            listOf("USER")
        }
        
        rolesToAssign.forEach { roleName ->
            val role = roleRepository.findByName(roleName)
                .orElseGet {
                    roleRepository.save(
                        com.gravitlauncher.simplecabinet.entity.Role(
                            name = roleName,
                            description = "Role from external system"
                        )
                    )
                }
            
            val userRole = com.gravitlauncher.simplecabinet.entity.UserRole(
                user = savedUser,
                role = role,
                assignedAt = LocalDateTime.now()
            )
            savedUser.userRoles.add(userRole)
            userRoleRepository.save(userRole)
        }
        
        val primaryRole = rolesToAssign.firstOrNull() ?: "USER"
        val token = jwtUtil.generateToken(savedUser.username, primaryRole)
        val session = sessionService.createSession(savedUser)
        
        // Notify external system about the refresh token
        externalAuthService.notifyRefreshToken(savedUser.username, session.sessionToken)

        return AuthResponse(
            token = token,
            refreshToken = session.sessionToken,
            username = savedUser.username,
            email = savedUser.email,
            role = primaryRole
        )
    }

    @Transactional
    fun refreshToken(refreshTokenRequest: RefreshTokenRequest): AuthResponse {
        val session = sessionService.findByToken(refreshTokenRequest.refreshToken)
            .orElseThrow { ResourceNotFoundException(ErrorCode.TOKEN_NOT_FOUND, "Session not found") }

        sessionService.verifySession(session)
        
        val user = session.user
        
        // For external users, verify refresh token with external system
        if (user.userType == UserType.EXTERNAL) {
            val isValidInExternalSystem = externalAuthService.verifyRefreshToken(
                user.username,
                refreshTokenRequest.refreshToken
            )
            
            if (!isValidInExternalSystem) {
                sessionService.revokeSession(refreshTokenRequest.refreshToken)
                throw UnauthorizedException(
                    ErrorCode.TOKEN_EXPIRED,
                    "Refresh token is invalid in external system"
                )
            }
        }
        
        sessionService.updateLastAccessed(session.sessionToken)

        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val primaryRole = activeRoles.firstOrNull()?.role?.name ?: "USER"
        
        val newAccessToken = jwtUtil.generateToken(user.username, primaryRole)

        return AuthResponse(
            token = newAccessToken,
            refreshToken = session.sessionToken,
            username = user.username,
            email = user.email,
            role = primaryRole
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        sessionService.revokeSession(refreshToken)
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
