package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.annotation.RequireAuth
import com.gravitlauncher.simplecabinet.annotation.RequirePermission
import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.service.RoleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/roles")
class RoleController(
    private val roleService: RoleService
) {

    @GetMapping
    @RequireAuth
    fun getAllRoles(): ResponseEntity<List<RoleResponse>> {
        val roles = roleService.getAllRoles().map { role ->
            RoleResponse(
                id = role.id!!,
                name = role.name,
                description = role.description,
                permissions = role.permissions.map { permission ->
                    PermissionResponse(
                        id = permission.id!!,
                        permissionKey = permission.permissionKey,
                        permissionValue = permission.permissionValue
                    )
                },
                createdAt = role.createdAt.toString(),
                updatedAt = role.updatedAt.toString()
            )
        }
        return ResponseEntity.ok(roles)
    }

    @GetMapping("/{id}")
    @RequireAuth
    fun getRoleById(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            val role = roleService.getRoleById(id)
            val response = RoleResponse(
                id = role.id!!,
                name = role.name,
                description = role.description,
                permissions = role.permissions.map { permission ->
                    PermissionResponse(
                        id = permission.id!!,
                        permissionKey = permission.permissionKey,
                        permissionValue = permission.permissionValue
                    )
                },
                createdAt = role.createdAt.toString(),
                updatedAt = role.updatedAt.toString()
            )
            ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "Role not found"))
        }
    }

    @PostMapping
    @RequirePermission("role.create")
    fun createRole(@Valid @RequestBody request: CreateRoleRequest): ResponseEntity<Any> {
        return try {
            val role = roleService.createRole(request.name, request.description)
            val response = RoleResponse(
                id = role.id!!,
                name = role.name,
                description = role.description,
                permissions = emptyList(),
                createdAt = role.createdAt.toString(),
                updatedAt = role.updatedAt.toString()
            )
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MessageResponse(e.message ?: "Failed to create role"))
        }
    }

    @PostMapping("/{roleId}/permissions")
    @RequirePermission("role.manage")
    fun addPermission(
        @PathVariable roleId: Long,
        @Valid @RequestBody request: AddPermissionRequest
    ): ResponseEntity<Any> {
        return try {
            val permission = roleService.addPermissionToRole(roleId, request.permissionKey, request.permissionValue)
            val response = PermissionResponse(
                id = permission.id!!,
                permissionKey = permission.permissionKey,
                permissionValue = permission.permissionValue
            )
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "Role not found"))
        }
    }

    @DeleteMapping("/permissions/{permissionId}")
    @RequirePermission("role.manage")
    fun removePermission(@PathVariable permissionId: Long): ResponseEntity<MessageResponse> {
        return try {
            roleService.removePermissionFromRole(permissionId)
            ResponseEntity.ok(MessageResponse("Permission removed successfully"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Failed to remove permission"))
        }
    }

    @PostMapping("/assign")
    @RequirePermission("role.assign")
    fun assignRoleToUser(@Valid @RequestBody request: AssignRoleRequest): ResponseEntity<Any> {
        return try {
            val expiresAt = request.expiresAt?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            val userRole = roleService.assignRoleToUser(request.userId, request.roleId, expiresAt)
            val response = UserRoleResponse(
                id = userRole.id!!,
                userId = userRole.user.id!!,
                roleId = userRole.role.id!!,
                roleName = userRole.role.name,
                assignedAt = userRole.assignedAt.toString(),
                expiresAt = userRole.expiresAt?.toString()
            )
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "User or role not found"))
        }
    }

    @DeleteMapping("/assign/{userRoleId}")
    @RequirePermission("role.assign")
    fun removeRoleFromUser(@PathVariable userRoleId: Long): ResponseEntity<MessageResponse> {
        return try {
            roleService.removeRoleFromUser(userRoleId)
            ResponseEntity.ok(MessageResponse("Role removed from user successfully"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponse("Failed to remove role from user"))
        }
    }

    @GetMapping("/user/{userId}")
    @RequireAuth
    fun getUserRoles(@PathVariable userId: Long): ResponseEntity<List<UserRoleResponse>> {
        val userRoles = roleService.getUserRoles(userId).map { userRole ->
            UserRoleResponse(
                id = userRole.id!!,
                userId = userRole.user.id!!,
                roleId = userRole.role.id!!,
                roleName = userRole.role.name,
                assignedAt = userRole.assignedAt.toString(),
                expiresAt = userRole.expiresAt?.toString()
            )
        }
        return ResponseEntity.ok(userRoles)
    }
}
