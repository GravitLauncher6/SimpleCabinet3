package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.entity.Session
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.exception.ErrorCode
import com.gravitlauncher.simplecabinet.exception.InvalidInputException
import com.gravitlauncher.simplecabinet.exception.ResourceNotFoundException
import com.gravitlauncher.simplecabinet.exception.UnauthorizedException
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.security.EcdsaKeyUtil
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

@Service
class MinecraftService(
    private val sessionService: SessionService,
    private val userRepository: UserRepository,
    private val permissionService: PermissionService
) {

    @Value("\${jwt.private-key}")
    private lateinit var privateKeyString: String

    @Value("\${jwt.public-key}")
    private lateinit var publicKeyString: String

    private lateinit var signingPrivateKey: PrivateKey
    private lateinit var signingPublicKey: PublicKey

    @PostConstruct
    fun initKeys() {
        signingPrivateKey = EcdsaKeyUtil.loadPrivateKeyFromString(privateKeyString)
        signingPublicKey = EcdsaKeyUtil.loadPublicKeyFromString(publicKeyString)
    }

    @Transactional(readOnly = true)
    fun getProfile(): MinecraftProfileResponse {
        return toMinecraftProfile(resolveCurrentUser())
    }

    @Transactional(readOnly = true)
    fun getAttributes(): MinecraftPlayerAttributesResponse {
        return buildAttributes(resolveCurrentUser())
    }

    @Transactional(readOnly = true)
    fun updateAttributes(
        request: MinecraftPlayerAttributesUpdateRequest
    ): MinecraftPlayerAttributesResponse {
        validateAttributeUpdate(request)
        return buildAttributes(resolveCurrentUser())
    }

    @Transactional(readOnly = true)
    fun getBlocklist(): MinecraftBlockedProfilesResponse {
        resolveCurrentUser()
        return MinecraftBlockedProfilesResponse()
    }

    @Transactional(readOnly = true)
    fun getCertificates(): MinecraftCertificateResponse {
        resolveCurrentUser()

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val expiresAt = Instant.now().plusSeconds(48 * 60 * 60)
        val refreshedAfter = Instant.now().plusSeconds(24 * 60 * 60)
        val publicKeyDer = keyPair.public.encoded
        val publicKeySignature = sign(publicKeyDer)

        return MinecraftCertificateResponse(
            keyPair = MinecraftKeyPairResponse(
                privateKey = toPkcs1Pem(keyPair.private as RSAPrivateCrtKey),
                publicKey = toPkcs1PublicPem(keyPair.public as RSAPublicKey)
            ),
            publicKeySignature = publicKeySignature,
            publicKeySignatureV2 = publicKeySignature,
            expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt),
            refreshedAfter = DateTimeFormatter.ISO_INSTANT.format(refreshedAfter)
        )
    }

    @Transactional(readOnly = true)
    fun getNameChangeInfo(): MinecraftNameChangeInfoResponse {
        val user = resolveCurrentUser()
        return MinecraftNameChangeInfoResponse(
            changedAt = user.updatedAt.toString(),
            createdAt = user.createdAt.toString(),
            nameChangeAllowed = false
        )
    }

    @Transactional(readOnly = true)
    fun checkNameAvailability(name: String): MinecraftNameAvailabilityResponse {
        resolveCurrentUser()
        val normalizedName = name.trim()
        if (!normalizedName.matches(Regex("^[A-Za-z0-9_]{3,16}$"))) {
            return MinecraftNameAvailabilityResponse(status = "NOT_ALLOWED")
        }
        return MinecraftNameAvailabilityResponse(
            status = if (userRepository.existsByUsername(normalizedName)) "DUPLICATE" else "AVAILABLE"
        )
    }

    @Transactional
    fun joinServer(request: MinecraftJoinRequest) {
        val session = resolveSessionFromAccessToken(request.accessToken)
        val selectedProfile = normalizeProfileId(request.selectedProfile)
        if (selectedProfile != profileId(session.user)) {
            throw InvalidInputException(message = "Selected profile does not match access token")
        }

        val serverId = request.serverId.trim()
        if (serverId.isEmpty()) {
            throw InvalidInputException(message = "serverId is required")
        }

        sessionService.bindServer(session.sessionToken, serverId)
    }

    @Transactional(readOnly = true)
    fun hasJoined(username: String, serverId: String, ip: String?): MinecraftSessionProfileResponse {
        val user = userRepository.findByUsername(username)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NO_CONTENT)
            }

        val session = sessionService.findByUserAndServerId(user.id ?: error("user id missing"), serverId.trim())
            .orElseThrow {
                ResponseStatusException(HttpStatus.NO_CONTENT)
            }

        sessionService.verifySession(session)
        if (ip != null && session.ipAddress != null && ip != session.ipAddress) {
            throw ResponseStatusException(HttpStatus.NO_CONTENT)
        }

        return toSessionProfile(user, includeSignature = true)
    }

    @Transactional(readOnly = true)
    fun getSessionProfile(profileId: String, unsigned: Boolean): MinecraftSessionProfileResponse {
        val user = findUserByProfileId(profileId)
            ?: throw ResponseStatusException(HttpStatus.NO_CONTENT)
        return toSessionProfile(user, includeSignature = !unsigned)
    }

    @Transactional(readOnly = true)
    fun lookupByName(name: String): MinecraftLookupProfileResponse {
        val user = userRepository.findByUsername(name)
            .orElseThrow {
                ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found: $name")
            }
        return toLookupProfile(user)
    }

    @Transactional(readOnly = true)
    fun lookupById(profileId: String): MinecraftLookupProfileResponse {
        val user = findUserByProfileId(profileId)
            ?: throw ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found for profile id: $profileId")
        return toLookupProfile(user)
    }

    @Transactional(readOnly = true)
    fun lookupBulkByName(names: List<String>): List<MinecraftLookupProfileResponse> {
        if (names.isEmpty() || names.size > 10) {
            throw InvalidInputException(message = "size must be between 1 and 10")
        }
        if (names.any { it.isBlank() }) {
            throw InvalidInputException(message = "Invalid profile name")
        }

        val requested = names.toSet()
        return userRepository.findAll()
            .filter { it.username in requested }
            .map { toLookupProfile(it) }
    }

    fun getPublicKeys(): MinecraftPublicKeysResponse {
        val key = Base64.getEncoder().encodeToString(signingPublicKey.encoded)
        return MinecraftPublicKeysResponse(
            profilePropertyKeys = listOf(key),
            playerCertificateKeys = listOf(key),
            authenticationKeys = listOf(key)
        )
    }

    fun getBlockedServers(): String {
        return ""
    }

    @Transactional(readOnly = true)
    fun getFriends(): MinecraftFriendsResponse {
        resolveCurrentUser()
        return MinecraftFriendsResponse()
    }

    @Transactional(readOnly = true)
    fun updateFriends(request: MinecraftFriendUpdateRequest): MinecraftFriendsResponse {
        resolveCurrentUser()
        val updateType = request.updateType.trim()
        if (updateType != "ADD" && updateType != "REMOVE") {
            throw InvalidInputException(message = "updateType must be ADD or REMOVE")
        }
        return MinecraftFriendsResponse()
    }

    @Transactional(readOnly = true)
    fun checkGiftCode() {
        resolveCurrentUser()
    }

    private fun resolveCurrentUser(): User {
        return permissionService.getCurrentUser()
            ?: throw UnauthorizedException(ErrorCode.UNAUTHORIZED, "The request requires user authentication")
    }

    private fun resolveUserFromAccessToken(accessToken: String): User {
        val session = resolveSessionFromAccessToken(accessToken)
        val userId = session.user.id ?: error("user id missing")
        return userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found for session") }
    }

    private fun resolveSessionFromAccessToken(accessToken: String): Session {
        val token = accessToken.removePrefix("Bearer ").trim()
        if (token.isEmpty()) {
            throw UnauthorizedException(ErrorCode.UNAUTHORIZED, "The request requires user authentication")
        }
        val session = sessionService.findByToken(token)
            .orElseThrow { UnauthorizedException(ErrorCode.INVALID_TOKEN, "Invalid access token") }
        return sessionService.verifySession(session)
    }

    private fun buildAttributes(user: User): MinecraftPlayerAttributesResponse {
        return MinecraftPlayerAttributesResponse(
            privileges = MinecraftPrivilegesResponse(
                onlineChat = MinecraftBooleanPrivilegeResponse(permissionFlag(user, "minecraft.privileges.onlineChat", true)),
                multiplayerServer = MinecraftBooleanPrivilegeResponse(permissionFlag(user, "minecraft.privileges.multiplayerServer", true)),
                multiplayerRealms = MinecraftBooleanPrivilegeResponse(permissionFlag(user, "minecraft.privileges.multiplayerRealms", true)),
                telemetry = MinecraftBooleanPrivilegeResponse(permissionFlag(user, "minecraft.privileges.telemetry", true)),
                optionalTelemetry = MinecraftBooleanPrivilegeResponse(permissionFlag(user, "minecraft.privileges.optionalTelemetry", true))
            ),
            profanityFilterPreferences = MinecraftProfanityFilterPreferencesResponse(
                profanityFilterOn = permissionFlag(user, "minecraft.preferences.profanityFilterOn", false)
            ),
            friendsPreferences = MinecraftFriendsPreferencesResponse(
                friends = permissionEnum(user, "minecraft.preferences.friends", setOf("ENABLED", "DISABLED"), "ENABLED"),
                acceptInvites = permissionEnum(user, "minecraft.preferences.acceptInvites", setOf("ENABLED", "DISABLED"), "ENABLED")
            ),
            chatPreferences = MinecraftChatPreferencesResponse(
                textCommunication = permissionEnum(
                    user,
                    "minecraft.preferences.textCommunication",
                    setOf("ENABLED", "DISABLED", "FRIENDS_ONLY"),
                    if (permissionFlag(user, "minecraft.privileges.onlineChat", true)) "ENABLED" else "DISABLED"
                )
            ),
            banStatus = MinecraftBanStatusResponse()
        )
    }

    private fun validateAttributeUpdate(request: MinecraftPlayerAttributesUpdateRequest) {
        request.friendsPreferences?.let {
            if (it.friends !in setOf("ENABLED", "DISABLED")) {
                throw InvalidInputException(message = "friends must be ENABLED or DISABLED")
            }
            if (it.acceptInvites !in setOf("ENABLED", "DISABLED")) {
                throw InvalidInputException(message = "acceptInvites must be ENABLED or DISABLED")
            }
        }
    }

    private fun permissionFlag(user: User, key: String, defaultValue: Boolean): Boolean {
        return permissionService.getPermissionValue(user, key)?.trim()?.lowercase()?.let {
            when (it) {
                "true", "enabled", "1", "yes" -> true
                "false", "disabled", "0", "no" -> false
                else -> defaultValue
            }
        } ?: defaultValue
    }

    private fun permissionEnum(user: User, key: String, allowedValues: Set<String>, defaultValue: String): String {
        val value = permissionService.getPermissionValue(user, key)?.trim()?.uppercase()
        return if (value != null && value in allowedValues) value else defaultValue
    }

    private fun toMinecraftProfile(user: User): MinecraftProfileResponse {
        return MinecraftProfileResponse(
            id = profileId(user),
            name = user.username
        )
    }

    private fun toLookupProfile(user: User): MinecraftLookupProfileResponse {
        return MinecraftLookupProfileResponse(
            id = profileId(user),
            name = user.username
        )
    }

    private fun toSessionProfile(user: User, includeSignature: Boolean): MinecraftSessionProfileResponse {
        val texturesJson = """
            {"timestamp":${System.currentTimeMillis()},"profileId":"${profileId(user)}","profileName":"${user.username}","textures":{}}
        """.trimIndent()
        val value = Base64.getEncoder().encodeToString(texturesJson.toByteArray(StandardCharsets.UTF_8))
        val property = MinecraftSessionPropertyResponse(
            name = "textures",
            value = value,
            signature = if (includeSignature) sign(value.toByteArray(StandardCharsets.UTF_8)) else null
        )
        return MinecraftSessionProfileResponse(
            id = profileId(user),
            name = user.username,
            properties = listOf(property)
        )
    }

    private fun profileId(user: User): String {
        return UUID.nameUUIDFromBytes("simplecabinet:minecraft:${user.id}:${user.username}".toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
    }

    private fun findUserByProfileId(profileId: String): User? {
        val normalized = normalizeProfileId(profileId)
        return userRepository.findAll().firstOrNull { profileId(it) == normalized }
    }

    private fun normalizeProfileId(profileId: String): String {
        val normalized = profileId.replace("-", "").trim().lowercase()
        if (!normalized.matches(Regex("^[0-9a-f]{32}$"))) {
            throw InvalidInputException(message = "Not a valid UUID: $profileId")
        }
        return normalized
    }

    private fun sign(bytes: ByteArray): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(signingPrivateKey)
        signature.update(bytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun toPkcs1Pem(privateKey: RSAPrivateCrtKey): String {
        val der = encodeSequence(
            encodeInteger(java.math.BigInteger.ZERO),
            encodeInteger(privateKey.modulus),
            encodeInteger(privateKey.publicExponent),
            encodeInteger(privateKey.privateExponent),
            encodeInteger(privateKey.primeP),
            encodeInteger(privateKey.primeQ),
            encodeInteger(privateKey.primeExponentP),
            encodeInteger(privateKey.primeExponentQ),
            encodeInteger(privateKey.crtCoefficient)
        )
        return pem("RSA PRIVATE KEY", der)
    }

    private fun toPkcs1PublicPem(publicKey: RSAPublicKey): String {
        val der = encodeSequence(
            encodeInteger(publicKey.modulus),
            encodeInteger(publicKey.publicExponent)
        )
        return pem("RSA PUBLIC KEY", der)
    }

    private fun encodeSequence(vararg parts: ByteArray): ByteArray {
        val content = parts.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        return byteArrayOf(0x30) + encodeLength(content.size) + content
    }

    private fun encodeInteger(value: java.math.BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return byteArrayOf(0x02) + encodeLength(bytes.size) + bytes
    }

    private fun encodeLength(length: Int): ByteArray {
        if (length < 128) {
            return byteArrayOf(length.toByte())
        }
        val bytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun pem(type: String, der: ByteArray): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $type-----\n$encoded\n-----END $type-----"
    }
}
