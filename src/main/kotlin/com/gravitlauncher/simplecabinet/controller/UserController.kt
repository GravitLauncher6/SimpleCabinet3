package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.dto.MessageResponse
import com.gravitlauncher.simplecabinet.dto.UserResponse
import com.gravitlauncher.simplecabinet.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userService.getAllUsers()
        return ResponseEntity.ok(users)
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    fun getUserById(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            val user = userService.getUserById(id)
            ResponseEntity.ok(user)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "User not found"))
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    fun getCurrentUser(authentication: Authentication): ResponseEntity<Any> {
        return try {
            val username = authentication.name
            val user = userService.getUserByUsername(username)
            ResponseEntity.ok(user)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "User not found"))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<MessageResponse> {
        return try {
            userService.deleteUser(id)
            ResponseEntity.ok(MessageResponse("User deleted successfully"))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MessageResponse(e.message ?: "User not found"))
        }
    }
}
