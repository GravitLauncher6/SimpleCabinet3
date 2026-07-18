package com.gravitlauncher.simplecabinet.service.auth

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OAuthStateService(secret: String) {
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()),
        "HmacSHA256"
    )
    private val random = SecureRandom()

    fun create(providerId: String): String {
        val nonce = ByteArray(16).also(random::nextBytes)
        val payload = ByteBuffer.allocate(8 + nonce.size + providerId.toByteArray().size)
            .putLong(Instant.now().plusSeconds(600).epochSecond)
            .put(nonce)
            .put(providerId.toByteArray())
            .array()
        return encode(payload) + "." + encode(sign(payload))
    }

    fun verify(state: String, providerId: String): Boolean = runCatching {
        val parts = state.split(".", limit = 2)
        if (parts.size != 2) return false
        val payload = decode(parts[0])
        if (!MessageDigest.isEqual(sign(payload), decode(parts[1]))) return false
        val buffer = ByteBuffer.wrap(payload)
        val expiresAt = buffer.long
        val providerBytes = ByteArray(buffer.remaining() - 16)
        buffer.position(buffer.position() + 16)
        buffer.get(providerBytes)
        expiresAt >= Instant.now().epochSecond && String(providerBytes) == providerId
    }.getOrDefault(false)

    private fun sign(payload: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(payload)
        }

    private fun encode(value: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
}
