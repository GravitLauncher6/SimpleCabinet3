package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.repository.PermissionRepository
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.*

class RoleServiceTest {

    private lateinit var roleRepository: RoleRepository
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var userRoleRepository: UserRoleRepository
    private lateinit var userRepository: UserRepository
    private lateinit var roleService: RoleService

    @BeforeEach
    fun setup() {
        roleRepository = mock()
        permissionRepository = mock()
        userRoleRepository = mock()
        userRepository = mock()

        roleService = RoleService(
            roleRepository,
            permissionRepository,
            userRoleRepository,
            userRepository
        )
    }

    @Test
    fun `createRole should create new role`() {
        // Arrange
        val roleName = "MODERATOR"
        val description = "Moderator role"

        val savedRole = Role(
            id = 1L,
            name = roleName,
            description = description,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(roleRepository.existsByName(roleName)).thenReturn(false)
        whenever(roleRepository.save(any())).thenReturn(savedRole)

        // Act
        val result = roleService.createRole(roleName, description)

        // Assert
        assertNotNull(result)
        assertEquals(roleName, result.name)
        assertEquals(description, result.description)
        verify(roleRepository).save(any())
    }

    @Test
    fun `createRole should fail when role already exists`() {
        // Arrange
        val roleName = "ADMIN"
        whenever(roleRepository.existsByName(roleName)).thenReturn(true)

        // Act & Assert
        val exception = assertThrows<DuplicateResourceException> {
            roleService.createRole(roleName, "Admin role")
        }

        assertEquals("Role already exists: $roleName", exception.message)
        verify(roleRepository, never()).save(any())
    }

    @Test
    fun `addPermissionToRole should add permission to role`() {
        // Arrange
        val roleId = 1L
        val role = Role(
            id = roleId,
            name = "USER",
            description = "User role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val permission = Permission(
            id = 1L,
            role = role,
            permissionKey = "user.profile",
            permissionValue = null
        )

        whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(role))
        whenever(permissionRepository.save(any())).thenReturn(permission)

        // Act
        val result = roleService.addPermissionToRole(roleId, "user.profile", null)

        // Assert
        assertNotNull(result)
        assertEquals("user.profile", result.permissionKey)
        assertNull(result.permissionValue)
        verify(permissionRepository).save(any())
    }

    @Test
    fun `addPermissionToRole should add permission with value`() {
        // Arrange
        val roleId = 1L
        val role = Role(
            id = roleId,
            name = "USER",
            description = "User role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val permission = Permission(
            id = 1L,
            role = role,
            permissionKey = "file.upload.max_size",
            permissionValue = "10485760"
        )

        whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(role))
        whenever(permissionRepository.save(any())).thenReturn(permission)

        // Act
        val result = roleService.addPermissionToRole(roleId, "file.upload.max_size", "10485760")

        // Assert
        assertNotNull(result)
        assertEquals("file.upload.max_size", result.permissionKey)
        assertEquals("10485760", result.permissionValue)
    }

    @Test
    fun `addPermissionToRole should fail when role not found`() {
        // Arrange
        val roleId = 999L
        whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<ResourceNotFoundException> {
            roleService.addPermissionToRole(roleId, "user.profile", null)
        }

        verify(permissionRepository, never()).save(any())
    }

    @Test
    fun `addPermissionToRole should reject duplicate permission key`() {
        val role = Role(id = 1L, name = "USER")
        whenever(roleRepository.findById(1L)).thenReturn(Optional.of(role))
        whenever(permissionRepository.existsByRoleAndPermissionKey(role, "user.profile")).thenReturn(true)

        assertThrows<DuplicateResourceException> {
            roleService.addPermissionToRole(1L, " user.profile ", null)
        }

        verify(permissionRepository, never()).save(any())
    }

    @Test
    fun `assignRoleToUser should assign role to user`() {
        // Arrange
        val userId = 1L
        val roleId = 1L

        val user = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val role = Role(
            id = roleId,
            name = "MODERATOR",
            description = "Moderator role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val userRole = UserRole(
            id = 1L,
            user = user,
            role = role,
            assignedAt = LocalDateTime.now(),
            expiresAt = null
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(role))
        whenever(userRoleRepository.save(any())).thenReturn(userRole)

        // Act
        val result = roleService.assignRoleToUser(userId, roleId, null)

        // Assert
        assertNotNull(result)
        assertEquals(userId, result.user.id)
        assertEquals(roleId, result.role.id)
        assertNull(result.expiresAt)
        verify(userRoleRepository).save(any())
    }

    @Test
    fun `assignRoleToUser should assign role with expiration`() {
        // Arrange
        val userId = 1L
        val roleId = 1L
        val expiresAt = LocalDateTime.now().plusDays(30)

        val user = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val role = Role(
            id = roleId,
            name = "TRIAL",
            description = "Trial role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val userRole = UserRole(
            id = 1L,
            user = user,
            role = role,
            assignedAt = LocalDateTime.now(),
            expiresAt = expiresAt
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(roleRepository.findById(roleId)).thenReturn(Optional.of(role))
        whenever(userRoleRepository.save(any())).thenReturn(userRole)

        // Act
        val result = roleService.assignRoleToUser(userId, roleId, expiresAt)

        // Assert
        assertNotNull(result)
        assertEquals(expiresAt, result.expiresAt)
    }

    @Test
    fun `assignRoleToUser should fail when user not found`() {
        // Arrange
        val userId = 999L
        val roleId = 1L

        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<ResourceNotFoundException> {
            roleService.assignRoleToUser(userId, roleId, null)
        }

        verify(userRoleRepository, never()).save(any())
    }

    @Test
    fun `assignRoleToUser should fail when role not found`() {
        // Arrange
        val userId = 1L
        val roleId = 999L

        val user = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(roleRepository.findById(roleId)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<ResourceNotFoundException> {
            roleService.assignRoleToUser(userId, roleId, null)
        }

        verify(userRoleRepository, never()).save(any())
    }

    @Test
    fun `assignRoleToUser should reject duplicate role assignment`() {
        val user = User(id = 1L, username = "testuser", email = "test@example.com", password = "password")
        val role = Role(id = 1L, name = "USER")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(roleRepository.findById(1L)).thenReturn(Optional.of(role))
        whenever(userRoleRepository.existsByUserAndRole(user, role)).thenReturn(true)

        assertThrows<DuplicateResourceException> {
            roleService.assignRoleToUser(1L, 1L, null)
        }

        verify(userRoleRepository, never()).save(any())
    }

    @Test
    fun `assignRoleToUser should reject expiration in the past`() {
        val user = User(id = 1L, username = "testuser", email = "test@example.com", password = "password")
        val role = Role(id = 1L, name = "TRIAL")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(roleRepository.findById(1L)).thenReturn(Optional.of(role))

        assertThrows<com.gravitlauncher.simplecabinet.exception.InvalidInputException> {
            roleService.assignRoleToUser(1L, 1L, LocalDateTime.now().minusMinutes(1))
        }

        verify(userRoleRepository, never()).save(any())
    }

    @Test
    fun `removeRoleFromUser should remove user role assignment`() {
        // Arrange
        val userRoleId = 1L

        // Act
        roleService.removeRoleFromUser(userRoleId)

        // Assert
        verify(userRoleRepository).deleteById(userRoleId)
    }

    @Test
    fun `getRoleByName should return role`() {
        // Arrange
        val roleName = "ADMIN"
        val role = Role(
            id = 1L,
            name = roleName,
            description = "Admin role",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(roleRepository.findByName(roleName)).thenReturn(Optional.of(role))

        // Act
        val result = roleService.getRoleByName(roleName)

        // Assert
        assertEquals(roleName, result.name)
    }

    @Test
    fun `getRoleByName should fail when role not found`() {
        // Arrange
        val roleName = "NONEXISTENT"
        whenever(roleRepository.findByName(roleName)).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<ResourceNotFoundException> {
            roleService.getRoleByName(roleName)
        }
    }

    @Test
    fun `getUserRoles should return all user roles`() {
        // Arrange
        val userId = 1L
        val user = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val role1 = Role(id = 1L, name = "USER", description = "User role")
        val role2 = Role(id = 2L, name = "MODERATOR", description = "Moderator role")

        val userRole1 = UserRole(id = 1L, user = user, role = role1, assignedAt = LocalDateTime.now())
        val userRole2 = UserRole(id = 2L, user = user, role = role2, assignedAt = LocalDateTime.now())

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(userRoleRepository.findByUser(user)).thenReturn(listOf(userRole1, userRole2))

        // Act
        val result = roleService.getUserRoles(userId)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.any { it.role.name == "USER" })
        assertTrue(result.any { it.role.name == "MODERATOR" })
    }

    @Test
    fun `getActiveUserRoles should return only active roles`() {
        // Arrange
        val userId = 1L
        val user = User(
            id = userId,
            username = "testuser",
            email = "test@example.com",
            password = "password",
            enabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val role = Role(id = 1L, name = "USER", description = "User role")
        val activeUserRole = UserRole(
            id = 1L,
            user = user,
            role = role,
            assignedAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(1)
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(userRoleRepository.findActiveByUser(eq(user), any()))
            .thenReturn(listOf(activeUserRole))

        // Act
        val result = roleService.getActiveUserRoles(userId)

        // Assert
        assertEquals(1, result.size)
        assertEquals("USER", result[0].role.name)
    }
}
