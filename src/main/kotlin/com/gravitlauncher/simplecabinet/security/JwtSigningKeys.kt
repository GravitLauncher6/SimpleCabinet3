package com.gravitlauncher.simplecabinet.security

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

@Component
class JwtSigningKeys(
    @param:Value("\${jwt.private-key:}") private val privateKeyString: String,
    @param:Value("\${jwt.public-key:}") private val publicKeyString: String,
    @param:Value("\${jwt.private-key-file:}") private val privateKeyFile: String,
    @param:Value("\${jwt.public-key-file:}") private val publicKeyFile: String
) {

    final var persistentKeys = false
        private set
    final lateinit var privateKey: PrivateKey
        private set
    final lateinit var publicKey: PublicKey
        private set

    @PostConstruct
    fun initKeys() {
        val configuredPrivateKey = readKey(privateKeyString, privateKeyFile, "private-key")
        val configuredPublicKey = readKey(publicKeyString, publicKeyFile, "public-key")
        require((configuredPrivateKey == null) == (configuredPublicKey == null)) {
            "Configure both JWT keys or leave both unset for an ephemeral key pair"
        }

        if (configuredPrivateKey == null && configuredPublicKey == null) {
            val pair = EcdsaKeyUtil.generateKeyPair()
            privateKey = pair.private
            publicKey = pair.public
            return
        }

        privateKey = EcdsaKeyUtil.loadPrivateKeyFromString(checkNotNull(configuredPrivateKey))
        publicKey = EcdsaKeyUtil.loadPublicKeyFromString(checkNotNull(configuredPublicKey))

        val signature = Signature.getInstance("SHA256withECDSA")
        val challenge = "SimpleCabinet JWT key check".toByteArray()
        signature.initSign(privateKey)
        signature.update(challenge)
        val signed = signature.sign()
        signature.initVerify(publicKey)
        signature.update(challenge)
        require(signature.verify(signed)) { "The configured JWT keys do not form a key pair" }
        persistentKeys = true
    }

    private fun readKey(value: String, file: String, property: String): String? {
        require(value.isBlank() || file.isBlank()) {
            "Configure only one of jwt.$property and jwt.$property-file"
        }
        return if (file.isNotBlank()) {
            Files.readString(Path.of(file)).trim().also {
                require(it.isNotEmpty()) { "jwt.$property-file is empty" }
            }
        } else {
            value.trim().takeIf { it.isNotEmpty() }
        }
    }
}
