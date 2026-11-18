package com.gravitlauncher.simplecabinet.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtUtil(private val keys: JwtSigningKeys) {

    @Value("\${jwt.expiration}")
    private var expiration: Long = 86400000

    fun generateToken(username: String, role: String, sessionToken: String): String {
        val claims = HashMap<String, Any>()
        claims["role"] = role
        claims["sessionId"] = sessionToken
        
        return Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(keys.privateKey)
            .compact()
    }

    fun generateNonExpiringToken(username: String, role: String, sessionToken: String): String {
        check(keys.persistentKeys) { "Configure a persistent JWT key pair before creating an admin token" }
        val claims = hashMapOf<String, Any>(
            "role" to role,
            "sessionId" to sessionToken
        )

        return Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuedAt(Date())
            .signWith(keys.privateKey)
            .compact()
    }

    fun extractUsername(token: String): String {
        return extractAllClaims(token).subject
    }

    fun extractRole(token: String): String {
        return extractAllClaims(token)["role"] as String
    }

    fun extractSessionToken(token: String): String {
        return extractAllClaims(token)["sessionId"] as? String
            ?: throw IllegalArgumentException("Token does not contain a session id")
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        // JWT expiration is optional. Treat a missing exp claim as non-expiring.
        return extractAllClaims(token).expiration?.before(Date()) ?: false
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(keys.publicKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
