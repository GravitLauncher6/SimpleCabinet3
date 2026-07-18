package com.gravitlauncher.simplecabinet.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gravitlauncher.simplecabinet.dto.LoginRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftJoinRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftPlayerAttributesUpdateRequest
import com.gravitlauncher.simplecabinet.dto.MinecraftProfanityFilterPreferencesResponse
import com.gravitlauncher.simplecabinet.dto.RegisterRequest
import com.gravitlauncher.simplecabinet.exception.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

@SpringBootTest
class MinecraftServiceTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var minecraftService: MinecraftService

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

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
                accessToken = auth.token,
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
    fun `join server should reject access token after logout`() {
        val auth = userService.register(
            RegisterRequest(
                username = "loggedout",
                email = "loggedout@example.com",
                password = "password123"
            )
        )
        authenticate("loggedout")
        val profile = minecraftService.getProfile()

        userService.logout(auth.refreshToken)

        assertThrows<UnauthorizedException> {
            minecraftService.joinServer(
                MinecraftJoinRequest(
                    accessToken = auth.token,
                    selectedProfile = profile.id,
                    serverId = "test-server-id"
                )
            )
        }
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

    @Test
    fun `should upload skin and cape and include them in session textures`() {
        userService.register(
            RegisterRequest(
                username = "textures",
                email = "textures@example.com",
                password = "password123"
            )
        )
        authenticate("textures")

        val skinProfile = minecraftService.uploadSkin("slim", pngFile("skin.png", 64, 64))
        val capeProfile = minecraftService.uploadCape("Founder", pngFile("cape.png", 64, 32))

        assertEquals(1, skinProfile.skins.size)
        assertEquals("SLIM", skinProfile.skins.single().variant)
        assertEquals("ACTIVE", skinProfile.skins.single().state)
        assertEquals(1, capeProfile.capes.size)
        assertEquals("Founder", capeProfile.capes.single().alias)
        assertEquals("ACTIVE", capeProfile.capes.single().state)

        val sessionProfile = minecraftService.getSessionProfile(capeProfile.id, unsigned = false)
        val decoded = Base64.getDecoder().decode(sessionProfile.properties.single().value)
        val textures = objectMapper.readTree(decoded).path("textures")
        assertEquals("slim", textures.path("SKIN").path("metadata").path("model").asText())
        assertEquals(skinProfile.skins.single().url, textures.path("SKIN").path("url").asText())
        assertEquals(capeProfile.capes.single().url, textures.path("CAPE").path("url").asText())
        assertNotNull(sessionProfile.properties.single().signature)
    }

    @Test
    fun `should retain cape history when hiding and reactivating cape`() {
        userService.register(
            RegisterRequest(
                username = "capes",
                email = "capes@example.com",
                password = "password123"
            )
        )
        authenticate("capes")

        val uploaded = minecraftService.uploadCape("Test Cape", pngFile("cape.png", 64, 32))
        val capeId = uploaded.capes.single().id
        val hidden = minecraftService.hideCape()
        val activated = minecraftService.activateCape(capeId)

        assertEquals("INACTIVE", hidden.capes.single().state)
        assertEquals("ACTIVE", activated.capes.single().state)
    }

    private fun authenticate(username: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            emptyList()
        )
    }

    private fun pngFile(filename: String, width: Int, height: Int): MockMultipartFile {
        val output = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output)
        return MockMultipartFile("file", filename, "image/png", output.toByteArray())
    }
}
