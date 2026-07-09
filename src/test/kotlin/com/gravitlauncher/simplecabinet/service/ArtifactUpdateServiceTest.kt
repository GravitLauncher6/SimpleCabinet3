package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyRequest
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import java.security.Signature
import java.util.Base64

@SpringBootTest
class ArtifactUpdateServiceTest {

    @Autowired
    private lateinit var artifactUpdateService: ArtifactUpdateService

    @Value("\${jwt.private-key}")
    private lateinit var privateKeyString: String

    @Test
    fun `should issue verified token for active variant`() {
        artifactUpdateService.upload(
            variant = "main",
            file = MockMultipartFile("file", "main.zip", "application/zip", "artifact-main".toByteArray()),
            deprecated = false
        )

        val prepared = artifactUpdateService.prepare()
        val signature = sign(prepared.challenge)
        val response = artifactUpdateService.verify(
            ArtifactUpdateVerifyRequest(
                variant = "main",
                signature = signature,
                token = prepared.token
            )
        )

        assertFalse(response.updateRequired)
        assertNotNull(response.verifiedToken)
    }

    @Test
    fun `should return update url for deprecated variant`() {
        val uploaded = artifactUpdateService.upload(
            variant = "legacy",
            file = MockMultipartFile("file", "legacy.zip", "application/zip", "artifact-legacy".toByteArray()),
            deprecated = true
        )

        val prepared = artifactUpdateService.prepare()
        val signature = sign(prepared.challenge)
        val response = artifactUpdateService.verify(
            ArtifactUpdateVerifyRequest(
                variant = "legacy",
                signature = signature,
                token = prepared.token
            )
        )

        assertTrue(response.updateRequired)
        assertTrue(response.url == uploaded.url)
    }

    private fun sign(challenge: String): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString))
        signer.update(challenge.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }
}
