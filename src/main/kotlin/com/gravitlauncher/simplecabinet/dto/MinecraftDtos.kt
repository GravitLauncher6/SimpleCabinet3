package com.gravitlauncher.simplecabinet.dto

data class MinecraftProfileResponse(
    val id: String,
    val name: String,
    val skins: List<MinecraftSkinResponse> = emptyList(),
    val capes: List<MinecraftCapeResponse> = emptyList()
)

data class MinecraftSkinResponse(
    val id: String,
    val state: String,
    val url: String,
    val variant: String
)

data class MinecraftCapeResponse(
    val id: String,
    val state: String,
    val url: String,
    val alias: String
)

data class MinecraftLookupProfileResponse(
    val id: String,
    val name: String
)

data class MinecraftPlayerAttributesResponse(
    val privileges: MinecraftPrivilegesResponse,
    val profanityFilterPreferences: MinecraftProfanityFilterPreferencesResponse,
    val friendsPreferences: MinecraftFriendsPreferencesResponse,
    val chatPreferences: MinecraftChatPreferencesResponse,
    val banStatus: MinecraftBanStatusResponse
)

data class MinecraftPrivilegesResponse(
    val onlineChat: MinecraftBooleanPrivilegeResponse,
    val multiplayerServer: MinecraftBooleanPrivilegeResponse,
    val multiplayerRealms: MinecraftBooleanPrivilegeResponse,
    val telemetry: MinecraftBooleanPrivilegeResponse,
    val optionalTelemetry: MinecraftBooleanPrivilegeResponse
)

data class MinecraftBooleanPrivilegeResponse(
    val enabled: Boolean
)

data class MinecraftProfanityFilterPreferencesResponse(
    val profanityFilterOn: Boolean
)

data class MinecraftFriendsPreferencesResponse(
    val friends: String,
    val acceptInvites: String
)

data class MinecraftChatPreferencesResponse(
    val textCommunication: String
)

data class MinecraftBanStatusResponse(
    val bannedScopes: Map<String, MinecraftBanScopeResponse> = emptyMap()
)

data class MinecraftBanScopeResponse(
    val banId: String,
    val expires: String? = null,
    val reason: String,
    val reasonMessage: String
)

data class MinecraftPlayerAttributesUpdateRequest(
    val profanityFilterPreferences: MinecraftProfanityFilterPreferencesResponse? = null,
    val friendsPreferences: MinecraftFriendsPreferencesResponse? = null
)

data class MinecraftBlockedProfilesResponse(
    val blockedProfiles: List<String> = emptyList()
)

data class MinecraftCertificateResponse(
    val keyPair: MinecraftKeyPairResponse,
    val publicKeySignature: String,
    val publicKeySignatureV2: String,
    val expiresAt: String,
    val refreshedAfter: String
)

data class MinecraftKeyPairResponse(
    val privateKey: String,
    val publicKey: String
)

data class MinecraftNameChangeInfoResponse(
    val changedAt: String,
    val createdAt: String,
    val nameChangeAllowed: Boolean
)

data class MinecraftNameAvailabilityResponse(
    val status: String
)

data class MinecraftJoinRequest(
    val accessToken: String,
    val selectedProfile: String,
    val serverId: String
)

data class MinecraftPublicKeysResponse(
    val profilePropertyKeys: List<String>,
    val playerCertificateKeys: List<String>,
    val authenticationKeys: List<String>
)

data class MinecraftSessionProfileResponse(
    val id: String,
    val name: String,
    val properties: List<MinecraftSessionPropertyResponse>
)

data class MinecraftSessionPropertyResponse(
    val name: String,
    val value: String,
    val signature: String? = null
)

data class MinecraftFriendsResponse(
    val friends: List<MinecraftFriendEntryResponse> = emptyList(),
    val incomingRequests: List<MinecraftFriendEntryResponse> = emptyList(),
    val outgoingRequests: List<MinecraftFriendEntryResponse> = emptyList(),
    val empty: Boolean = true
)

data class MinecraftFriendEntryResponse(
    val profileId: String,
    val name: String
)

data class MinecraftFriendUpdateRequest(
    val name: String? = null,
    val profileId: String? = null,
    val updateType: String
)
