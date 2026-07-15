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
                parentRoleId = role.parent?.id,
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
    fun getRoleById(@PathVariable id: Long): ResponseEntity<RoleResponse> {
        val role = roleService.getRoleById(id)
        val response = RoleResponse(
            id = role.id!!,
            name = role.name,
            description = role.description,
            parentRoleId = role.parent?.id,
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
        return ResponseEntity.ok(response)
    }

    @PostMapping
    @RequirePermission("role.create")
    fun createRole(@Valid @RequestBody request: CreateRoleRequest): ResponseEntity<RoleResponse> {
        val role = roleService.createRole(request.name, request.description, request.parentRoleId)
        val response = RoleResponse(
            id = role.id!!,
            name = role.name,
            description = role.description,
            parentRoleId = role.parent?.id,
            permissions = emptyList(),
            createdAt = role.createdAt.toString(),
            updatedAt = role.updatedAt.toString()
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{roleId}/permissions")
    @RequirePermission("role.manage")
    fun addPermission(
        @PathVariable roleId: Long,
        @Valid @RequestBody request: AddPermissionRequest
    ): ResponseEntity<PermissionResponse> {
        val permission = roleService.addPermissionToRole(roleId, request.permissionKey, request.permissionValue)
        val response = PermissionResponse(
            id = permission.id!!,
            permissionKey = permission.permissionKey,
            permissionValue = permission.permissionValue
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/permissions/{permissionId}")
    @RequirePermission("role.manage")
    fun removePermission(@PathVariable permissionId: Long): ResponseEntity<MessageResponse> {
        roleService.removePermissionFromRole(permissionId)
        return ResponseEntity.ok(MessageResponse("Permission removed successfully"))
    }

    @PostMapping("/assign")
    @RequirePermission("role.assign")
    fun assignRoleToUser(@Valid @RequestBody request: AssignRoleRequest): ResponseEntity<UserRoleResponse> {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/assign/{userRoleId}")
    @RequirePermission("role.assign")
    fun removeRoleFromUser(@PathVariable userRoleId: Long): ResponseEntity<MessageResponse> {
        roleService.removeRoleFromUser(userRoleId)
        return ResponseEntity.ok(MessageResponse("Role removed from user successfully"))
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
