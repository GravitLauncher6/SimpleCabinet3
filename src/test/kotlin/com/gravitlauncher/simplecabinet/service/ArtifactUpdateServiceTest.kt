package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyRequest
import com.gravitlauncher.simplecabinet.dto.BlobRegistrationRequest
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import com.gravitlauncher.simplecabinet.service.storage.StorageService
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

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var casStorageService: CasStorageService

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

    @Test
    fun `should retain previous artifact object after replacement commits`() {
        val firstKeyPair = EcdsaKeyUtil.generateKeyPair()
        val secondKeyPair = EcdsaKeyUtil.generateKeyPair()
        val first = artifactUpdateService.upload(
            variant = "replaceable",
            file = MockMultipartFile("file", "first.zip", "application/zip", "first".toByteArray()),
            publicKeyFile = publicKeyFile(firstKeyPair),
            privateKeyFile = privateKeyFile(firstKeyPair),
            deprecated = false
        )

        val second = artifactUpdateService.upload(
            variant = "replaceable",
            file = MockMultipartFile("file", "second.zip", "application/zip", "second".toByteArray()),
            publicKeyFile = publicKeyFile(secondKeyPair),
            privateKeyFile = privateKeyFile(secondKeyPair),
            deprecated = false
        )

        assertTrue(storageService.exists(first.storageKey))
        assertTrue(storageService.exists(second.storageKey))
    }

    @Test
    fun `should preserve previous artifact object when a blob record references it`() {
        val firstKeyPair = EcdsaKeyUtil.generateKeyPair()
        val secondKeyPair = EcdsaKeyUtil.generateKeyPair()
        val first = artifactUpdateService.upload(
            variant = "shared-artifact",
            file = MockMultipartFile("file", "first.zip", "application/zip", "shared".toByteArray()),
            publicKeyFile = publicKeyFile(firstKeyPair),
            privateKeyFile = privateKeyFile(firstKeyPair),
            deprecated = false
        )
        casStorageService.registerBlob(
            BlobRegistrationRequest(
                hash = "sha256:shared-artifact-test",
                algorithm = "sha256",
                sizeBytes = 6,
                storageKey = first.storageKey
            )
        )

        artifactUpdateService.upload(
            variant = "shared-artifact",
            file = MockMultipartFile("file", "second.zip", "application/zip", "replacement".toByteArray()),
            publicKeyFile = publicKeyFile(secondKeyPair),
            privateKeyFile = privateKeyFile(secondKeyPair),
            deprecated = false
        )

        assertTrue(storageService.exists(first.storageKey))
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
