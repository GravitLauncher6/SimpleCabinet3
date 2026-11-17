package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.dto.MinecraftJoinRequest
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import com.gravitlauncher.simplecabinet.service.ArtifactUpdateService
import com.gravitlauncher.simplecabinet.service.MinecraftService
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.security.PrivateKey
import java.util.Date

@SpringBootTest
@WebAppConfiguration
class MinecraftJoinControllerTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var artifactUpdateService: ArtifactUpdateService

    @MockitoBean
    private lateinit var minecraftService: MinecraftService

    @Value("\${jwt.private-key}")
    private lateinit var privateKeyString: String

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    fun `join requires launcher update token`() {
        mockMvc.perform(joinRequest()).andExpect(status().isUnauthorized)
        verify(minecraftService, never()).joinServer(any())
    }

    @Test
    fun `join rejects invalid launcher update tokens before calling service`() {
        val invalidTokens = listOf(
            " ",
            "malformed",
            artifactUpdateService.prepare().token,
            token(expiresAt = Date(System.currentTimeMillis() - 60_000)),
            token(key = EcdsaKeyUtil.generateKeyPair().private),
            token(subject = "user"),
            token(type = "access"),
            token(expiresAt = null)
        )

        for (token in invalidTokens) {
            mockMvc.perform(joinRequest().header("X-Launcher-Update-Token", token))
                .andExpect(status().isUnauthorized)
        }
        verify(minecraftService, never()).joinServer(any())
    }

    @Test
    fun `join accepts valid launcher update token`() {
        mockMvc.perform(joinRequest().header("X-Launcher-Update-Token", token()))
            .andExpect(status().isNoContent)
        verify(minecraftService).joinServer(MinecraftJoinRequest("access-token", "profile", "server"))
    }

    private fun joinRequest() = post("/minecraft/sessionserver/session/minecraft/join")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"accessToken":"access-token","selectedProfile":"profile","serverId":"server"}""")

    private fun token(
        subject: String = "artifact-update-verified",
        type: String = "artifact-update-verified",
        expiresAt: Date? = Date(System.currentTimeMillis() + 60_000),
        key: PrivateKey = EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString)
    ): String = Jwts.builder()
        .subject(subject)
        .claim("type", type)
        .expiration(expiresAt)
        .signWith(key)
        .compact()
}
