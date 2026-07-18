package com.gravitlauncher.simplecabinet.service.auth

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ExternalSessionStateCipher(
    secret: String,
    private val objectMapper: ObjectMapper
) {
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()),
        "AES"
    )
    private val secureRandom = SecureRandom()

    init {
        require(secret.isNotBlank()) { "external-auth.state-encryption-secret is required when external auth is enabled" }
    }

    fun encrypt(state: ExternalSessionState): String {
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(state))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(value: String): ExternalSessionState {
        val valueBytes = Base64.getDecoder().decode(value)
        require(valueBytes.size > 12) { "Invalid external session state" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, valueBytes.copyOfRange(0, 12)))
        return objectMapper.readValue(
            cipher.doFinal(valueBytes.copyOfRange(12, valueBytes.size)),
            ExternalSessionState::class.java
        )
    }
}
