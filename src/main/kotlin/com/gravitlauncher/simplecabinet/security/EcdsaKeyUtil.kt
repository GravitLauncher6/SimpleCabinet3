package com.gravitlauncher.simplecabinet.security

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utility class for generating and loading ECDSA key pairs for JWT signing
 */
object EcdsaKeyUtil {
    
    private const val ALGORITHM = "EC"
    private const val CURVE = "secp256r1" // P-256 curve
    
    /**
     * Generate a new ECDSA key pair
     */
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
        val ecSpec = ECGenParameterSpec(CURVE)
        keyPairGenerator.initialize(ecSpec)
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Save a key pair to files
     * @param keyPair The key pair to save
     * @param privateKeyPath Path to save the private key
     * @param publicKeyPath Path to save the public key
     */
    fun saveKeyPair(keyPair: KeyPair, privateKeyPath: String, publicKeyPath: String) {
        // Save private key
        val privateKeyBytes = keyPair.private.encoded
        val privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes)
        Files.write(Paths.get(privateKeyPath), privateKeyBase64.toByteArray())
        
        // Save public key
        val publicKeyBytes = keyPair.public.encoded
        val publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes)
        Files.write(Paths.get(publicKeyPath), publicKeyBase64.toByteArray())
    }
    
    /**
     * Load private key from file
     * @param path Path to the private key file
     * @return PrivateKey instance
     */
    fun loadPrivateKey(path: String): PrivateKey {
        val keyBytes = Files.readAllBytes(Paths.get(path))
        val keyString = String(keyBytes)
        val decoded = Base64.getDecoder().decode(keyString)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePrivate(keySpec)
    }
    
    /**
     * Load public key from file
     * @param path Path to the public key file
     * @return PublicKey instance
     */
    fun loadPublicKey(path: String): PublicKey {
        val keyBytes = Files.readAllBytes(Paths.get(path))
        val keyString = String(keyBytes)
        val decoded = Base64.getDecoder().decode(keyString)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }
    
    /**
     * Load private key from Base64 string
     */
    fun loadPrivateKeyFromString(privateKeyString: String): PrivateKey {
        val decoded = Base64.getDecoder().decode(privateKeyString)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePrivate(keySpec)
    }
    
    /**
     * Load public key from Base64 string
     */
    fun loadPublicKeyFromString(publicKeyString: String): PublicKey {
        val decoded = Base64.getDecoder().decode(publicKeyString)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }
}
