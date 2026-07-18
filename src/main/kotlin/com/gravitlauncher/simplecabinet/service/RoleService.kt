package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.DuplicateResourceException
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.repository.PermissionRepository
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val userRepository: UserRepository
) {

    fun getAllRoles(): List<Role> {
        return roleRepository.findAll()
    }

    fun getRoleById(id: Long): Role {
        return roleRepository.findById(id)
            .orElseThrow { ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found with id: $id") }
    }

    fun getRoleByName(name: String): Role {
        return roleRepository.findByName(name)
            .orElseThrow { ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: $name") }
    }

    @Transactional
    fun createRole(name: String, description: String?, parentRoleId: Long? = null): Role {
        if (roleRepository.existsByName(name)) {
            throw DuplicateResourceException(ErrorCode.ROLE_ALREADY_EXISTS, "Role already exists: $name")
        }

        val parent = parentRoleId?.let { getRoleById(it) }
        val role = Role(
            name = name,
            description = description,
            parent = parent,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return roleRepository.save(role)
    }

    @Transactional
    fun addPermissionToRole(roleId: Long, permissionKey: String, permissionValue: String?): Permission {
        val role = getRoleById(roleId)
        val normalizedKey = permissionKey.trim()
        if (normalizedKey.isEmpty()) {
            throw InvalidInputException(ErrorCode.INVALID_PERMISSION, "Permission key is required")
        }
        if (permissionRepository.existsByRoleAndPermissionKey(role, normalizedKey)) {
            throw DuplicateResourceException(
                ErrorCode.DUPLICATE_RESOURCE,
                "Permission already exists for role ${role.name}: $normalizedKey"
            )
        }

        val permission = Permission(
            role = role,
            permissionKey = normalizedKey,
            permissionValue = permissionValue
        )

        return permissionRepository.save(permission)
    }

    @Transactional
    fun removePermissionFromRole(permissionId: Long) {
        permissionRepository.deleteById(permissionId)
    }

    @Transactional
    fun assignRoleToUser(userId: Long, roleId: Long, expiresAt: LocalDateTime?): com.gravitlauncher.simplecabinet.entity.UserRole {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $userId") }
        val role = getRoleById(roleId)
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw InvalidInputException(ErrorCode.INVALID_ROLE_DATA, "Role expiration must be in the future")
        }
        if (userRoleRepository.existsByUserAndRole(user, role)) {
            throw DuplicateResourceException(
                ErrorCode.DUPLICATE_RESOURCE,
                "Role ${role.name} is already assigned to user ${user.username}"
            )
        }

        val userRole = com.gravitlauncher.simplecabinet.entity.UserRole(
            user = user,
            role = role,
            assignedAt = LocalDateTime.now(),
            expiresAt = expiresAt
        )

        return userRoleRepository.save(userRole)
    }

    @Transactional
    fun removeRoleFromUser(userRoleId: Long) {
        userRoleRepository.deleteById(userRoleId)
    }

    fun getUserRoles(userId: Long): List<com.gravitlauncher.simplecabinet.entity.UserRole> {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $userId") }
        return userRoleRepository.findByUser(user)
    }

    fun getActiveUserRoles(userId: Long): List<com.gravitlauncher.simplecabinet.entity.UserRole> {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: $userId") }
        return userRoleRepository.findActiveByUser(user, LocalDateTime.now())
    }

    fun getRolePermissions(roleId: Long): List<Permission> {
        val role = getRoleById(roleId)
        return permissionRepository.findByRole(role)
    }
}
