package com.gravitlauncher.simplecabinet.dto

import jakarta.validation.constraints.NotBlank

data class CreateRoleRequest(
    @field:NotBlank(message = "Role name is required")
    val name: String,
    
    val description: String? = null,

    val parentRoleId: Long? = null
)

data class AddPermissionRequest(
    @field:NotBlank(message = "Permission key is required")
    val permissionKey: String,
    
    val permissionValue: String? = null
)

data class AssignRoleRequest(
    val userId: Long,
    val roleId: Long,
    val expiresAt: String? = null
)

data class RoleResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val parentRoleId: Long?,
    val permissions: List<PermissionResponse>,
    val createdAt: String,
    val updatedAt: String
)

data class PermissionResponse(
    val id: Long,
    val permissionKey: String,
    val permissionValue: String?
)

data class UserRoleResponse(
    val id: Long,
    val userId: Long,
    val roleId: Long,
    val roleName: String,
    val assignedAt: String,
    val expiresAt: String?
)
