package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PermissionService(
    private val userRoleRepository: UserRoleRepository,
    private val customUserDetailsService: CustomUserDetailsService
) {

    fun getCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.principal == "anonymousUser") {
            return null
        }
        val username = authentication.name
        return try {
            customUserDetailsService.loadUserEntityByUsername(username)
        } catch (e: Exception) {
            null
        }
    }

    fun hasPermission(user: User, permissionKey: String): Boolean {
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        
        return activeRoles.any { userRole ->
            userRole.role.permissions.any { permission ->
                permission.permissionKey == permissionKey
            }
        }
    }

    fun hasPermission(permissionKey: String): Boolean {
        val user = getCurrentUser() ?: return false
        return hasPermission(user, permissionKey)
    }

    fun getPermissionValue(user: User, permissionKey: String): String? {
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        
        for (userRole in activeRoles) {
            val permission = userRole.role.permissions.find { it.permissionKey == permissionKey }
            if (permission != null) {
                return permission.permissionValue
            }
        }
        return null
    }

    fun getPermissionValue(permissionKey: String): String? {
        val user = getCurrentUser() ?: return null
        return getPermissionValue(user, permissionKey)
    }

    fun getAllPermissions(user: User): Map<String, String?> {
        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val permissionsMap = mutableMapOf<String, String?>()
        
        for (userRole in activeRoles) {
            for (permission in userRole.role.permissions) {
                // Later roles override earlier ones
                permissionsMap[permission.permissionKey] = permission.permissionValue
            }
        }
        
        return permissionsMap
    }

    fun isAuthenticated(): Boolean {
        return getCurrentUser() != null
    }
}
