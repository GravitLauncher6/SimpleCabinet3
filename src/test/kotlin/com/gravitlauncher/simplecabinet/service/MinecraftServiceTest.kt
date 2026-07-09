package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.LoginRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftJoinRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftPlayerAttributesUpdateRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftProfanityFilterPreferencesResponse
import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

@SpringBootTest
class MinecraftServiceTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var minecraftService: MinecraftService

    @Test
    fun `should return minecraft profile and attributes for session token`() {
        userService.register(
            RegisterRequest(
                username = "steve",
                email = "steve@example.com",
                password = "password123"
            )
        )
        val auth = userService.login(LoginRequest(username = "steve", password = "password123"))
        authenticate("steve")

        val profile = minecraftService.getProfile()
        val attributes = minecraftService.getAttributes()

        assertEquals("steve", profile.name)
        assertEquals(32, profile.id.length)
        assertTrue(attributes.privileges.multiplayerServer.enabled)
        assertFalse(attributes.profanityFilterPreferences.profanityFilterOn)
    }

    @Test
    fun `join server should bind server id and allow hasJoined lookup`() {
        val auth = userService.register(
            RegisterRequest(
                username = "alex",
                email = "alex@example.com",
                password = "password123"
            )
        )
        authenticate("alex")

        val profile = minecraftService.getProfile()
        minecraftService.joinServer(
            MinecraftJoinRequest(
                accessToken = auth.refreshToken,
                selectedProfile = profile.id,
                serverId = "test-server-id"
            )
        )

        val joined = minecraftService.hasJoined("alex", "test-server-id", null)
        assertEquals(profile.id, joined.id)
        assertEquals("alex", joined.name)
        assertNotNull(joined.properties.first().signature)
    }

    @Test
    fun `updating attributes should validate supported payload and return current attributes`() {
        val auth = userService.register(
            RegisterRequest(
                username = "builder",
                email = "builder@example.com",
                password = "password123"
            )
        )
        authenticate("builder")

        val response = minecraftService.updateAttributes(
            MinecraftPlayerAttributesUpdateRequest(
                profanityFilterPreferences = MinecraftProfanityFilterPreferencesResponse(profanityFilterOn = true)
            )
        )

        assertFalse(response.profanityFilterPreferences.profanityFilterOn)
    }

    private fun authenticate(username: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            emptyList()
        )
    }
}
