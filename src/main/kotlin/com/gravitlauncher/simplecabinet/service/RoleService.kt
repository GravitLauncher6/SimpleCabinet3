package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
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
            .orElseThrow { NoSuchElementException("Role not found with id: $id") }
    }

    fun getRoleByName(name: String): Role {
        return roleRepository.findByName(name)
            .orElseThrow { NoSuchElementException("Role not found: $name") }
    }

    @Transactional
    fun createRole(name: String, description: String?): Role {
        if (roleRepository.existsByName(name)) {
            throw IllegalArgumentException("Role already exists: $name")
        }

        val role = Role(
            name = name,
            description = description,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return roleRepository.save(role)
    }

    @Transactional
    fun addPermissionToRole(roleId: Long, permissionKey: String, permissionValue: String?): Permission {
        val role = getRoleById(roleId)

        val permission = Permission(
            role = role,
            permissionKey = permissionKey,
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
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        val role = getRoleById(roleId)

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
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        return userRoleRepository.findByUser(user)
    }

    fun getActiveUserRoles(userId: Long): List<com.gravitlauncher.simplecabinet.entity.UserRole> {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }
        return userRoleRepository.findActiveByUser(user, LocalDateTime.now())
    }

    fun getRolePermissions(roleId: Long): List<Permission> {
        val role = getRoleById(roleId)
        return permissionRepository.findByRole(role)
    }
}
