package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.entity.UserType
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.exception.UnauthorizedException
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import com.gravitlauncher.simplecabinet.security.JwtUtil
import com.gravitlauncher.simplecabinet.service.auth.*
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
    private val externalAuthSystem: ExternalAuthSystem
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw DuplicateResourceException(ErrorCode.USER_ALREADY_EXISTS, "Username already exists")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException(ErrorCode.USER_ALREADY_EXISTS, "Email already exists")
        }
        val user = userRepository.save(
            User(
                username = request.username,
                email = request.email,
                password = passwordEncoder.encode(request.password).orEmpty(),
                enabled = true
            )
        )
        assignRoles(user, setOf("USER"), "Default user role")
        return responseFor(user, "USER", sessionService.createSession(user))
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username).orElse(null)
        return when {
            user == null -> authenticateWithDefaultExternalProvider(request)
            user.userType == UserType.LOCAL -> loginLocalUser(request)
            else -> authenticateExistingExternalUser(user, request)
        }
    }

    @Transactional
    fun loginWithOAuthCallback(providerId: String, code: String, redirectUri: String): AuthResponse {
        val provider = externalAuthSystem.provider(providerId) as? OAuthExternalAuthProvider
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "OAuth provider is unavailable")
        return completeExternalAuthentication(provider.authenticateCallback(code, redirectUri))
    }

    fun oauthAuthorizationUrl(providerId: String, state: String, redirectUri: String): String {
        val provider = externalAuthSystem.provider(providerId) as? OAuthExternalAuthProvider
            ?: throw ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "OAuth provider not found")
        return provider.authorizationUrl(state, redirectUri)
    }

    private fun loginLocalUser(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )
        val user = customUserDetailsService.loadUserEntityByUsername(request.username)
        return responseFor(user, primaryRole(user), sessionService.createSession(user))
    }

    private fun authenticateExistingExternalUser(user: User, request: LoginRequest): AuthResponse {
        val providerId = user.externalProviderId
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "External user has no provider")
        val provider = externalAuthSystem.provider(providerId) as? PasswordExternalAuthProvider
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "External authentication is unavailable")
        val authentication = provider.authenticate(ExternalPasswordAuthentication(user.username, request.password))
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials")

        if (authentication.identity.subject != user.externalSubject || authentication.identity.providerId != providerId) {
            throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "External identity does not match this user")
        }
        return completeExternalAuthentication(authentication, user)
    }

    private fun authenticateWithDefaultExternalProvider(request: LoginRequest): AuthResponse {
        val provider = externalAuthSystem.defaultProvider()
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials")
        val authentication = provider.authenticate(ExternalPasswordAuthentication(request.username, request.password))
            ?: throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials")
        return completeExternalAuthentication(authentication)
    }

    private fun completeExternalAuthentication(
        authentication: ExternalAuthentication,
        existingUser: User? = null
    ): AuthResponse {
        val identity = authentication.identity
        val user = existingUser ?: userRepository
            .findByExternalProviderIdAndExternalSubject(identity.providerId, identity.subject)
            .orElseGet {
                if (userRepository.existsByUsername(identity.username)) {
                    throw DuplicateResourceException(
                        ErrorCode.USER_ALREADY_EXISTS,
                        "Username is already owned by another user"
                    )
                }
                userRepository.save(
                    User(
                        username = identity.username,
                        email = identity.email ?: "${identity.subject}@external.local",
                        // External users never authenticate through this value.
                        password = passwordEncoder.encode(java.util.UUID.randomUUID().toString()).orEmpty(),
                        userType = UserType.EXTERNAL,
                        externalProviderId = identity.providerId,
                        externalSubject = identity.subject,
                        enabled = true
                    )
                )
            }

        if (user.userType != UserType.EXTERNAL ||
            user.externalProviderId != identity.providerId ||
            user.externalSubject != identity.subject
        ) {
            throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "External identity is not allowed")
        }

        if (user.userRoles.isEmpty()) {
            assignRoles(user, identity.roles.ifEmpty { setOf("USER") }, "Role from external system")
        }
        val session = sessionService.createSession(
            user,
            externalProviderId = identity.providerId,
            externalState = externalAuthSystem.encrypt(authentication.sessionState)
        )
        return responseFor(user, primaryRole(user), session)
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        var session = sessionService.findByToken(request.refreshToken)
            .orElseThrow { ResourceNotFoundException(ErrorCode.TOKEN_NOT_FOUND, "Session not found") }
        sessionService.verifySession(session)

        val user = session.user
        if (user.userType == UserType.EXTERNAL) {
            if (!externalAuthSystem.enabled ||
                session.externalProviderId != user.externalProviderId ||
                session.externalState == null
            ) {
                return revokeAndRequireLogin(session.sessionToken)
            }
            val provider = externalAuthSystem.provider(user.externalProviderId!!)
                ?: return revokeAndRequireLogin(session.sessionToken)
            val state = runCatching { externalAuthSystem.decrypt(session.externalState!!) }
                .getOrElse { return revokeAndRequireLogin(session.sessionToken) }
            val providerId = requireNotNull(user.externalProviderId)
            val externalSubject = requireNotNull(user.externalSubject)

            when (val validation = provider.validateSession(
                ExternalIdentityReference(providerId, externalSubject),
                state
            )) {
                ExternalSessionValidation.ReauthenticationRequired -> return revokeAndRequireLogin(session.sessionToken)
                is ExternalSessionValidation.Valid -> {
                    if (validation.updatedState != null) {
                        session = sessionService.updateExternalState(
                            session,
                            externalAuthSystem.encrypt(validation.updatedState)
                        )
                    }
                }
            }
        }

        sessionService.updateLastAccessed(session.sessionToken)
        return responseFor(user, primaryRole(user), session)
    }

    @Transactional
    fun logout(refreshToken: String) {
        sessionService.revokeSession(refreshToken)
    }

    fun getAllUsers(): List<UserResponse> = userRepository.findAll().map(::toResponse)

    fun getUserById(id: Long): UserResponse = toResponse(
        userRepository.findById(id)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $id") }
    )

    fun getUserByUsername(username: String): UserResponse = toResponse(
        userRepository.findByUsername(username)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $username") }
    )

    @Transactional
    fun deleteUser(id: Long) {
        if (!userRepository.existsById(id)) {
            throw ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $id")
        }
        userRepository.deleteById(id)
    }

    private fun revokeAndRequireLogin(sessionToken: String): Nothing {
        sessionService.revokeSession(sessionToken, "External authentication requires re-login")
        throw UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "External authentication requires re-login")
    }

    private fun assignRoles(user: User, roles: Set<String>, description: String) {
        roles.forEach { roleName ->
            val role = roleRepository.findByName(roleName).orElseGet {
                roleRepository.save(com.gravitlauncher.simplecabinet.entity.Role(name = roleName, description = description))
            }
            val userRole = UserRole(user = user, role = role, assignedAt = LocalDateTime.now())
            user.userRoles.add(userRole)
            userRoleRepository.save(userRole)
        }
    }

    private fun primaryRole(user: User): String =
        userRoleRepository.findActiveByUser(user, LocalDateTime.now()).firstOrNull()?.role?.name ?: "USER"

    private fun responseFor(user: User, role: String, session: com.gravitlauncher.simplecabinet.entity.Session) =
        AuthResponse(
            token = jwtUtil.generateToken(user.username, role),
            refreshToken = session.sessionToken,
            username = user.username,
            email = user.email,
            role = role
        )

    private fun toResponse(user: User): UserResponse {
        val roles = userRoleRepository.findActiveByUser(user, LocalDateTime.now()).map { it.role.name }
        return UserResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            role = roles.joinToString(", ").ifEmpty { "No roles" },
            enabled = user.enabled,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }
}
