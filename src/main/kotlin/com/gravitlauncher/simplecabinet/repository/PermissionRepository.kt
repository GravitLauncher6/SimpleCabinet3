package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByRole(role: Role): List<Permission>
    fun findByRoleAndPermissionKey(role: Role, permissionKey: String): List<Permission>
}
