package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.LoginRequest
import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import com.gravitlauncher.simplecabinet.entity.Session
import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import com.gravitlauncher.simplecabinet.security.JwtUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.*

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userRoleRepository: UserRoleRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtUtil: JwtUtil
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var customUserDetailsService: CustomUserDetailsService
    private lateinit var sessionService: SessionService
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userRepository = mock()
        userRoleRepository = mock()
        roleRepository = mock()
        passwordEncoder = mock()
        jwtUtil = mock()
        authenticationManager = mock()
        customUserDetailsService = mock()
        sessionService = mock()

        userService = UserService(
            userRepository,
            userRoleRepository,
            roleRepository,
            passwordEncoder,
            jwtUtil,
            authenticationManager,
            customUserDetailsService,
            sessionService
        )
    }

    @Test
    fun `register should create new user with USER role`() {
        // Arrange
        val request = RegisterRequest(
            username = "testuser",
            email = "test@example.com",
            password = "password123"
        )

        val defaultRole = Role(
            id = 1L,
            name = "USER",
            description = "Default user role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedUser = User(
            id = 1L,
            username = request.username,
            email = request.email,
            password = "encodedPassword",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val session = Session(
            id = 1L,
            sessionToken = "session-token",
            user = savedUser,
            expiresAt = java.time.Instant.now().plusMillis(604800000)
        )

        whenever(userRepository.existsByUsername(request.username)).thenReturn(false)
        whenever(userRepository.existsByEmail(request.email)).thenReturn(false)
        whenever(passwordEncoder.encode(request.password)).thenReturn("encodedPassword")
        whenever(userRepository.save(any())).thenReturn(savedUser)
        whenever(roleRepository.findByName("USER")).thenReturn(Optional.of(defaultRole))
        whenever(jwtUtil.generateToken(savedUser.username, "USER")).thenReturn("jwt-token")
        whenever(sessionService.createSession(savedUser)).thenReturn(session)

        // Act
        val response = userService.register(request)

        // Assert
        assertNotNull(response)
        assertEquals("jwt-token", response.token)
        assertEquals("session-token", response.refreshToken)
        assertEquals("testuser", response.username)
        assertEquals("test@example.com", response.email)
        assertEquals("USER", response.role)

        verify(userRepository).save(any())
        verify(roleRepository).findByName("USER")
    }

    @Test
    fun `register should fail when username already exists`() {
        // Arrange
        val request = RegisterRequest(
            username = "existinguser",
            email = "test@example.com",
            password = "password123"
        )

        whenever(userRepository.existsByUsername(request.username)).thenReturn(true)

        // Act & Assert
        val exception = assertThrows<DuplicateResourceException> {
            userService.register(request)
        }

        assertEquals("Username already exists", exception.message)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `register should fail when email already exists`() {
        // Arrange
        val request = RegisterRequest(
            username = "newuser",
            email = "existing@example.com",
            password = "password123"
        )

        whenever(userRepository.existsByUsername(request.username)).thenReturn(false)
        whenever(userRepository.existsByEmail(request.email)).thenReturn(true)

        // Act & Assert
        val exception = assertThrows<DuplicateResourceException> {
            userService.register(request)
        }

        assertEquals("Email already exists", exception.message)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `login should return auth response with tokens`() {
        // Arrange
        val request = LoginRequest(
            username = "testuser",
            password = "password123"
        )

        val user = User(
            id = 1L,
            username = request.username,
            email = "test@example.com",
            password = "encodedPassword",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val role = Role(id = 1L, name = "USER", description = "User role")
        val userRole = com.gravitlauncher.simplecabinet.entity.UserRole(
            id = 1L,
            user = user,
            role = role,
            assignedAt = LocalDateTime.now()
        )

        val session = Session(
            id = 1L,
            sessionToken = "session-token",
            user = user,
            expiresAt = java.time.Instant.now().plusMillis(604800000)
        )

        whenever(customUserDetailsService.loadUserEntityByUsername(request.username)).thenReturn(user)
        whenever(userRoleRepository.findActiveByUser(any(), any())).thenReturn(listOf(userRole))
        whenever(jwtUtil.generateToken(user.username, "USER")).thenReturn("jwt-token")
        whenever(sessionService.createSession(user)).thenReturn(session)

        // Act
        val response = userService.login(request)

        // Assert
        assertNotNull(response)
        assertEquals("jwt-token", response.token)
        assertEquals("session-token", response.refreshToken)
        assertEquals("testuser", response.username)

        verify(authenticationManager).authenticate(any())
    }

    @Test
    fun `login should fail with invalid credentials`() {
        // Arrange
        val request = LoginRequest(
            username = "testuser",
            password = "wrongpassword"
        )

        whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException::class.java)

        // Act & Assert
        assertThrows<BadCredentialsException> {
            userService.login(request)
        }
    }

    @Test
    fun `deleteUser should delete existing user`() {
        // Arrange
        val userId = 1L
        whenever(userRepository.existsById(userId)).thenReturn(true)

        // Act
        userService.deleteUser(userId)

        // Assert
        verify(userRepository).deleteById(userId)
    }

    @Test
    fun `deleteUser should fail when user does not exist`() {
        // Arrange
        val userId = 999L
        whenever(userRepository.existsById(userId)).thenReturn(false)

        // Act & Assert
        val exception = assertThrows<ResourceNotFoundException> {
            userService.deleteUser(userId)
        }

        assertEquals("User not found with id: $userId", exception.message)
        verify(userRepository, never()).deleteById(userId)
    }
}
