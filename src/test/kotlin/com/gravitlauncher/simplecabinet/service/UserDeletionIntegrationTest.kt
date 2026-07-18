package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import com.gravitlauncher.simplecabinet.repository.SessionRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserDeletionIntegrationTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sessionRepository: SessionRepository

    @Test
    fun `delete user removes sessions before user row`() {
        userService.register(
            RegisterRequest(
                username = "delete-with-session",
                email = "delete-with-session@example.com",
                password = "password123"
            )
        )
        val user = userRepository.findByUsername("delete-with-session").orElseThrow()
        val userId = requireNotNull(user.id)
        assertTrue(sessionRepository.findByUser(user).isNotEmpty())

        userService.deleteUser(userId)

        assertFalse(userRepository.existsById(userId))
        assertTrue(sessionRepository.findAll().none { it.user.id == userId })
    }
}
