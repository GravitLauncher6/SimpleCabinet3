package com.gravitlauncher.simplecabinet.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

@Component
class JwtUtil {

    @Value("\${jwt.private-key}")
    private lateinit var privateKeyString: String

    @Value("\${jwt.public-key}")
    private lateinit var publicKeyString: String

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000

    private fun getPrivateKey(): PrivateKey {
        return EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString)
    }

    private fun getPublicKey(): PublicKey {
        return EcdsaKeyUtil.loadPublicKeyFromString(publicKeyString)
    }

    fun generateToken(username: String, role: String): String {
        val claims = HashMap<String, Any>()
        claims["role"] = role
        
        return Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(getPrivateKey())
            .compact()
    }

    fun extractUsername(token: String): String {
        return extractAllClaims(token).subject
    }

    fun extractRole(token: String): String {
        return extractAllClaims(token)["role"] as String
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractAllClaims(token).expiration.before(Date())
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getPublicKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
