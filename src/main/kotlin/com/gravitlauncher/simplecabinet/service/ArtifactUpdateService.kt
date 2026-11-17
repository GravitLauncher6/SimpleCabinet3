package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.ArtifactUpdatePrepareResponse
import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyRequest
import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyResponse
import com.gravitlauncher.simplecabinet.dto.ArtifactUploadResponse
import com.gravitlauncher.simplecabinet.entity.ArtifactBuild
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.exception.UnauthorizedException
import com.gravitlauncher.simplecabinet.repository.ArtifactBuildRepository
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import com.gravitlauncher.simplecabinet.service.storage.StorageReferenceService
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@Service
class ArtifactUpdateService(
    private val artifactBuildRepository: ArtifactBuildRepository,
    private val storageService: com.gravitlauncher.simplecabinet.service.storage.StorageService,
    private val storageReferenceService: StorageReferenceService
) {

    @Value("\${jwt.private-key}")
    private lateinit var privateKeyString: String

    @Value("\${jwt.public-key}")
    private lateinit var publicKeyString: String

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000

    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey

    @PostConstruct
    fun initKeys() {
        privateKey = EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString)
        publicKey = EcdsaKeyUtil.loadPublicKeyFromString(publicKeyString)
    }

    fun prepare(): ArtifactUpdatePrepareResponse {
        val challengeBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(challengeBytes)
        val challenge = Base64.getEncoder().encodeToString(challengeBytes)
        val token = Jwts.builder()
            .subject("artifact-update-prepare")
            .claim("type", "artifact-update-prepare")
            .claim("challenge", challenge)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(privateKey)
            .compact()

        return ArtifactUpdatePrepareResponse(challenge = challenge, token = token)
    }

    @Transactional(readOnly = true)
    fun verify(request: ArtifactUpdateVerifyRequest): ArtifactUpdateVerifyResponse {
        val publicKeyString = normalizeKey(request.publicKey, "Public key")
        val updatePublicKey = loadPublicKey(publicKeyString)

        val claims = parseAndValidatePrepareToken(request.token)
        val challenge = Base64.getDecoder().decode(claims["challenge"] as? String)
            ?: throw InvalidInputException(message = "Prepare token missing challenge")

        if (!verifySignature(challenge, request.signature, updatePublicKey)) {
            throw InvalidInputException(message = "Invalid signature")
        }

        val build = artifactBuildRepository.findByPublicKey(publicKeyString)
            .orElseThrow {
                ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Artifact build not found for public key")
        }

        return if (build.deprecated) {
            val currentBuild = artifactBuildRepository
                .findFirstByVariantAndDeprecatedFalseOrderByUpdatedAtDesc(build.variant)
                .orElse(build)
            ArtifactUpdateVerifyResponse(updateRequired = true, url = currentBuild.url)
        } else {
            ArtifactUpdateVerifyResponse(
                updateRequired = false,
                verifiedToken = Jwts.builder()
                    .subject("artifact-update-verified")
                    .claim("type", "artifact-update-verified")
                    .claim("variant", build.variant)
                    .claim("challenge", challenge)
                    .issuedAt(Date())
                    .expiration(Date(System.currentTimeMillis() + expiration))
                    .signWith(privateKey)
                    .compact()
            )
        }
    }

    fun validateVerifiedToken(token: String?) {
        if (token.isNullOrBlank()) {
            throw UnauthorizedException(message = "Launcher update token required")
        }

        val claims = try {
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            throw UnauthorizedException(message = "Invalid launcher update token")
        } catch (_: IllegalArgumentException) {
            throw UnauthorizedException(message = "Invalid launcher update token")
        }

        if (claims.subject != "artifact-update-verified" || claims["type"] != "artifact-update-verified" ||
            claims.expiration == null
        ) {
            throw UnauthorizedException(message = "Invalid launcher update token")
        }
    }

    @Transactional
    fun upload(
        variant: String,
        file: MultipartFile,
        publicKeyFile: MultipartFile,
        privateKeyFile: MultipartFile,
        deprecated: Boolean
    ): ArtifactUploadResponse {
        val normalizedVariant = variant.trim()
        if (normalizedVariant.isEmpty()) {
            throw InvalidInputException(message = "Variant is required")
        }
        if (file.isEmpty) {
            throw InvalidInputException(message = "File is required")
        }

        val publicKeyString = readKey(publicKeyFile, "Public key")
        val privateKeyString = readKey(privateKeyFile, "Private key")
        validateKeyPair(publicKeyString, privateKeyString)

        val originalFilename = file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "artifact.bin"
        val storageKey = buildStorageKey(normalizedVariant, originalFilename)

        val uploadedStorageKey = file.inputStream.use { inputStream ->
            storageService.upload(storageKey, inputStream, file.size, file.contentType)
        }
        registerRollbackCleanup(uploadedStorageKey)
        val url = storageService.getUrl(uploadedStorageKey)
        val now = Instant.now()

        artifactBuildRepository.saveAll(
            artifactBuildRepository.findAllByVariant(normalizedVariant)
                .map { previous -> previous.copy(deprecated = true, updatedAt = now) }
        )

        val saved = ArtifactBuild(
            variant = normalizedVariant,
            storageKey = uploadedStorageKey,
            url = url,
            originalFilename = originalFilename,
            sizeBytes = file.size,
            publicKey = publicKeyString,
            privateKey = privateKeyString,
            deprecated = deprecated,
            createdAt = now,
            updatedAt = now
        )

        val persisted = artifactBuildRepository.save(saved)
        return ArtifactUploadResponse(
            variant = persisted.variant,
            storageKey = persisted.storageKey,
            url = persisted.url,
            deprecated = persisted.deprecated
        )
    }

    private fun buildStorageKey(variant: String, originalFilename: String): String {
        val safeVariant = variant.replace(Regex("[^a-zA-Z0-9._/-]"), "_")
        val safeFilename = originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "artifacts/$safeVariant/${UUID.randomUUID()}-$safeFilename"
    }

    private fun parseAndValidatePrepareToken(token: String): Claims {
        val claims = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .payload

        if (claims.subject != "artifact-update-prepare" || claims["type"] != "artifact-update-prepare") {
            throw InvalidInputException(message = "Invalid prepare token")
        }

        return claims
    }

    private fun verifySignature(challenge: ByteArray, signature: String, updatePublicKey: PublicKey): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(updatePublicKey)
            verifier.update(challenge)
            verifier.verify(Base64.getDecoder().decode(signature))
        } catch (_: Exception) {
            false
        }
    }

    private fun readKey(file: MultipartFile, name: String): String {
        if (file.isEmpty) {
            throw InvalidInputException(message = "$name is required")
        }
        if (file.size > MAX_KEY_FILE_SIZE) {
            throw InvalidInputException(message = "$name is too large")
        }
        return normalizeKey(String(file.bytes, Charsets.UTF_8), name)
    }

    private fun normalizeKey(value: String, name: String): String {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            throw InvalidInputException(message = "$name is required")
        }
        return normalized
    }

    private fun loadPublicKey(value: String): PublicKey {
        return try {
            EcdsaKeyUtil.loadPublicKeyFromString(value)
        } catch (_: Exception) {
            throw InvalidInputException(message = "Invalid public key")
        }
    }

    private fun validateKeyPair(publicKeyString: String, privateKeyString: String) {
        val updatePublicKey = loadPublicKey(publicKeyString)
        val updatePrivateKey = try {
            EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString)
        } catch (_: Exception) {
            throw InvalidInputException(message = "Invalid private key")
        }

        try {
            val proof = "artifact-update-key-pair".toByteArray(Charsets.UTF_8)
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(updatePrivateKey)
            signer.update(proof)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(updatePublicKey)
            verifier.update(proof)
            if (!verifier.verify(signer.sign())) {
                throw InvalidInputException(message = "Public and private keys do not match")
            }
        } catch (exception: InvalidInputException) {
            throw exception
        } catch (_: Exception) {
            throw InvalidInputException(message = "Invalid update key pair")
        }
    }

    private fun registerRollbackCleanup(uploadedStorageKey: String) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    runCatching { storageReferenceService.deleteIfUnreferenced(uploadedStorageKey) }
                }
            }
        })
    }

    companion object {
        private const val MAX_KEY_FILE_SIZE = 16 * 1024L
    }
}
