package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyRequest
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import java.security.KeyPair
import java.security.Signature
import java.util.Base64
import kotlin.test.assertFailsWith

@SpringBootTest
class ArtifactUpdateServiceTest {

    @Autowired
    private lateinit var artifactUpdateService: ArtifactUpdateService

    @Test
    fun `should issue verified token for active variant`() {
        val keyPair = EcdsaKeyUtil.generateKeyPair()
        artifactUpdateService.upload(
            variant = "main",
            file = MockMultipartFile("file", "main.zip", "application/zip", "artifact-main".toByteArray()),
            publicKeyFile = publicKeyFile(keyPair),
            privateKeyFile = privateKeyFile(keyPair),
            deprecated = false
        )

        val prepared = artifactUpdateService.prepare()
        val signature = sign(prepared.challenge, keyPair)
        val response = artifactUpdateService.verify(
            ArtifactUpdateVerifyRequest(
                publicKey = encode(keyPair.public.encoded),
                signature = signature,
                token = prepared.token
            )
        )

        assertFalse(response.updateRequired)
        assertNotNull(response.verifiedToken)
    }

    @Test
    fun `should return update url for deprecated variant`() {
        val keyPair = EcdsaKeyUtil.generateKeyPair()
        val uploaded = artifactUpdateService.upload(
            variant = "legacy",
            file = MockMultipartFile("file", "legacy.zip", "application/zip", "artifact-legacy".toByteArray()),
            publicKeyFile = publicKeyFile(keyPair),
            privateKeyFile = privateKeyFile(keyPair),
            deprecated = true
        )

        val prepared = artifactUpdateService.prepare()
        val signature = sign(prepared.challenge, keyPair)
        val response = artifactUpdateService.verify(
            ArtifactUpdateVerifyRequest(
                publicKey = encode(keyPair.public.encoded),
                signature = signature,
                token = prepared.token
            )
        )

        assertTrue(response.updateRequired)
        assertTrue(response.url == uploaded.url)
    }

    @Test
    fun `should reject signature made with another update key`() {
        val updateKeyPair = EcdsaKeyUtil.generateKeyPair()
        val otherKeyPair = EcdsaKeyUtil.generateKeyPair()
        artifactUpdateService.upload(
            variant = "isolated",
            file = MockMultipartFile("file", "isolated.zip", "application/zip", "artifact-isolated".toByteArray()),
            publicKeyFile = publicKeyFile(updateKeyPair),
            privateKeyFile = privateKeyFile(updateKeyPair),
            deprecated = false
        )

        val prepared = artifactUpdateService.prepare()
        assertFailsWith<InvalidInputException> {
            artifactUpdateService.verify(
                ArtifactUpdateVerifyRequest(
                    publicKey = encode(updateKeyPair.public.encoded),
                    signature = sign(prepared.challenge, otherKeyPair),
                    token = prepared.token
                )
            )
        }
    }

    @Test
    fun `should reject mismatched uploaded key pair`() {
        val publicKeyPair = EcdsaKeyUtil.generateKeyPair()
        val privateKeyPair = EcdsaKeyUtil.generateKeyPair()

        assertFailsWith<InvalidInputException> {
            artifactUpdateService.upload(
                variant = "mismatched",
                file = MockMultipartFile("file", "mismatched.zip", "application/zip", "artifact".toByteArray()),
                publicKeyFile = publicKeyFile(publicKeyPair),
                privateKeyFile = privateKeyFile(privateKeyPair),
                deprecated = false
            )
        }
    }

    private fun sign(challenge: String, keyPair: KeyPair): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(challenge.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun publicKeyFile(keyPair: KeyPair) = MockMultipartFile(
        "publicKey",
        "public.key",
        "text/plain",
        encode(keyPair.public.encoded).toByteArray()
    )

    private fun privateKeyFile(keyPair: KeyPair) = MockMultipartFile(
        "privateKey",
        "private.key",
        "text/plain",
        encode(keyPair.private.encoded).toByteArray()
    )

    private fun encode(value: ByteArray): String = Base64.getEncoder().encodeToString(value)
}
