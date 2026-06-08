package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class PermissionServiceTest {

    private lateinit var userRoleRepository: UserRoleRepository
    private lateinit var customUserDetailsService: CustomUserDetailsService
    private lateinit var permissionService: PermissionService

    @BeforeEach
    fun setup() {
        userRoleRepository = mock()
        customUserDetailsService = mock()
        permissionService = PermissionService(userRoleRepository, customUserDetailsService)
    }

    @Test
    fun `hasPermission should match exact permission`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "user.profile")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act
        val result = permissionService.hasPermission(user, "user.profile")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `hasPermission should match wildcard prefix pattern`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "user.*")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "user.profile"))
        assertTrue(permissionService.hasPermission(user, "user.settings"))
        assertTrue(permissionService.hasPermission(user, "user.avatar"))
        assertTrue(permissionService.hasPermission(user, "user.delete"))
        assertFalse(permissionService.hasPermission(user, "admin.create"))
    }

    @Test
    fun `hasPermission should match admin wildcard pattern`() {
        // Arrange
        val user = createUser(1L, "admin")
        val role = createRole(1L, "ADMIN")
        val permission = createPermission(1L, role, "admin.*")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "admin.create"))
        assertTrue(permissionService.hasPermission(user, "admin.delete"))
        assertTrue(permissionService.hasPermission(user, "admin.users"))
        assertTrue(permissionService.hasPermission(user, "admin.settings"))
        assertFalse(permissionService.hasPermission(user, "user.profile"))
    }

    @Test
    fun `hasPermission should match full wildcard`() {
        // Arrange
        val user = createUser(1L, "superadmin")
        val role = createRole(1L, "SUPER_ADMIN")
        val permission = createPermission(1L, role, "*")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "admin.create"))
        assertTrue(permissionService.hasPermission(user, "user.profile"))
        assertTrue(permissionService.hasPermission(user, "anything.at.all"))
        assertTrue(permissionService.hasPermission(user, "random.permission"))
    }

    @Test
    fun `hasPermission should match suffix wildcard`() {
        // Arrange
        val user = createUser(1L, "moderator")
        val role = createRole(1L, "MODERATOR")
        val permission = createPermission(1L, role, "*.delete")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "user.delete"))
        assertTrue(permissionService.hasPermission(user, "post.delete"))
        assertTrue(permissionService.hasPermission(user, "comment.delete"))
        assertFalse(permissionService.hasPermission(user, "user.create"))
        assertFalse(permissionService.hasPermission(user, "post.edit"))
    }

    @Test
    fun `hasPermission should match middle wildcard`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "admin.users.*.delete")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "admin.users.123.delete"))
        assertTrue(permissionService.hasPermission(user, "admin.users.456.delete"))
        assertTrue(permissionService.hasPermission(user, "admin.users.abc.delete"))
        assertFalse(permissionService.hasPermission(user, "admin.users.123.edit"))
        assertFalse(permissionService.hasPermission(user, "admin.posts.123.delete"))
    }

    @Test
    fun `hasPermission should work with multiple roles`() {
        // Arrange
        val user = createUser(1L, "testuser")
        
        val userRole1 = createRole(1L, "USER")
        val permission1 = createPermission(1L, userRole1, "user.*")
        userRole1.permissions.add(permission1)
        
        val moderatorRole = createRole(2L, "MODERATOR")
        val permission2 = createPermission(2L, moderatorRole, "content.*")
        moderatorRole.permissions.add(permission2)
        
        val userRole1Assignment = createUserRole(1L, user, userRole1)
        val userRole2Assignment = createUserRole(2L, user, moderatorRole)
        user.userRoles.add(userRole1Assignment)
        user.userRoles.add(userRole2Assignment)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole1Assignment, userRole2Assignment))

        // Act & Assert
        assertTrue(permissionService.hasPermission(user, "user.profile"))
        assertTrue(permissionService.hasPermission(user, "content.moderate"))
        assertTrue(permissionService.hasPermission(user, "content.delete"))
        assertFalse(permissionService.hasPermission(user, "admin.create"))
    }

    @Test
    fun `hasPermission should return false when user has no permissions`() {
        // Arrange
        val user = createUser(1L, "testuser")

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(emptyList())

        // Act
        val result = permissionService.hasPermission(user, "user.profile")

        // Assert
        assertFalse(result)
    }

    @Test
    fun `hasPermission should exclude expired roles`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "user.*")
        role.permissions.add(permission)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(emptyList()) // Expired role filtered out

        // Act
        val result = permissionService.hasPermission(user, "user.profile")

        // Assert
        assertFalse(result)
    }

    @Test
    fun `getPermissionValue should return value for matching permission`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "file.upload.max_size", "10485760")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act
        val result = permissionService.getPermissionValue(user, "file.upload.max_size")

        // Assert
        assertEquals("10485760", result)
    }

    @Test
    fun `getPermissionValue should return value for wildcard permission`() {
        // Arrange
        val user = createUser(1L, "testuser")
        val role = createRole(1L, "USER")
        val permission = createPermission(1L, role, "file.*", "5242880")
        role.permissions.add(permission)
        
        val userRole = createUserRole(1L, user, role)
        user.userRoles.add(userRole)

        whenever(userRoleRepository.findActiveByUser(any(), any()))
            .thenReturn(listOf(userRole))

        // Act
        val result = permissionService.getPermissionValue(user, "file.upload")

        // Assert
        assertEquals("5242880", result)
    }

    // Helper methods
    private fun createUser(id: Long, username: String): User {
        return User(
            id = id,
            username = username,
            email = "$username@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun createRole(id: Long, name: String): Role {
        return Role(
            id = id,
            name = name,
            description = "$name role",
            permissions = mutableListOf(),
            userRoles = mutableListOf(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun createPermission(id: Long, role: Role, key: String, value: String? = null): Permission {
        return Permission(
            id = id,
            role = role,
            permissionKey = key,
            permissionValue = value
        )
    }

    private fun createUserRole(id: Long, user: User, role: Role, expiresAt: LocalDateTime? = null): UserRole {
        return UserRole(
            id = id,
            user = user,
            role = role,
            assignedAt = LocalDateTime.now(),
            expiresAt = expiresAt
        )
    }
}
