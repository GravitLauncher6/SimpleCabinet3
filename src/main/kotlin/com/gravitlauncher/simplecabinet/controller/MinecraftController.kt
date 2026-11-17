package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.annotation.RequireAuth
import com.gravitlauncher.simplecabinet.annotation.RequireLauncherUpdateToken
import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.service.MinecraftService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/minecraft")
class MinecraftController(
    private val minecraftService: MinecraftService
) {

    @GetMapping("/profile")
    @RequireAuth
    fun getProfile(): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.getProfile())
    }

    @PostMapping("/profile/skins", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequireAuth
    fun uploadSkin(
        @RequestParam variant: String,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.uploadSkin(variant, file))
    }

    @DeleteMapping("/profile/skins/active")
    @RequireAuth
    fun resetSkin(): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.resetSkin())
    }

    @PostMapping("/profile/capes", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequireAuth
    fun uploadCape(
        @RequestParam(required = false) alias: String?,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.uploadCape(alias, file))
    }

    @DeleteMapping("/profile/capes/active")
    @RequireAuth
    fun hideCape(): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.hideCape())
    }

    @PutMapping("/profile/capes/active")
    @RequireAuth
    fun activateCape(
        @RequestBody request: MinecraftCapeActivationRequest
    ): ResponseEntity<MinecraftProfileResponse> {
        return ResponseEntity.ok(minecraftService.activateCape(request.capeId))
    }

    @GetMapping("/profile/lookup/name/{name}")
    fun lookupByName(@PathVariable name: String): ResponseEntity<MinecraftLookupProfileResponse> {
        return ResponseEntity.ok(minecraftService.lookupByName(name))
    }

    @GetMapping("/profile/lookup/{uuid}")
    fun lookupByUuid(@PathVariable uuid: String): ResponseEntity<MinecraftLookupProfileResponse> {
        return ResponseEntity.ok(minecraftService.lookupById(uuid))
    }

    @PostMapping("/profile/lookup/bulk/byname")
    fun lookupBulkByName(@RequestBody names: List<String>): ResponseEntity<List<MinecraftLookupProfileResponse>> {
        return ResponseEntity.ok(minecraftService.lookupBulkByName(names))
    }

    @GetMapping("/player/attributes")
    @RequireAuth
    fun getAttributes(): ResponseEntity<MinecraftPlayerAttributesResponse> {
        return ResponseEntity.ok(minecraftService.getAttributes())
    }

    @PostMapping("/player/attributes")
    @RequireAuth
    fun updateAttributes(
        @Valid @RequestBody request: MinecraftPlayerAttributesUpdateRequest
    ): ResponseEntity<MinecraftPlayerAttributesResponse> {
        return ResponseEntity.ok(minecraftService.updateAttributes(request))
    }

    @GetMapping("/privacy/blocklist")
    @RequireAuth
    fun getBlocklist(): ResponseEntity<MinecraftBlockedProfilesResponse> {
        return ResponseEntity.ok(minecraftService.getBlocklist())
    }

    @PostMapping("/player/certificates")
    @RequireAuth
    fun getCertificates(): ResponseEntity<MinecraftCertificateResponse> {
        return ResponseEntity.ok(minecraftService.getCertificates())
    }

    @GetMapping("/profile/namechange")
    @RequireAuth
    fun getNameChangeInfo(): ResponseEntity<MinecraftNameChangeInfoResponse> {
        return ResponseEntity.ok(minecraftService.getNameChangeInfo())
    }

    @GetMapping("/profile/name/{name}/available")
    @RequireAuth
    fun checkNameAvailability(
        @PathVariable name: String
    ): ResponseEntity<MinecraftNameAvailabilityResponse> {
        return ResponseEntity.ok(minecraftService.checkNameAvailability(name))
    }

    @GetMapping("/productvoucher/giftcode")
    @RequireAuth
    fun checkGiftCode(): ResponseEntity<Void> {
        minecraftService.checkGiftCode()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/friends")
    @RequireAuth
    fun getFriends(): ResponseEntity<MinecraftFriendsResponse> {
        return ResponseEntity.ok(minecraftService.getFriends())
    }

    @PutMapping("/friends")
    @RequireAuth
    fun updateFriends(
        @RequestBody request: MinecraftFriendUpdateRequest
    ): ResponseEntity<MinecraftFriendsResponse> {
        return ResponseEntity.ok(minecraftService.updateFriends(request))
    }

    @GetMapping("/publickeys")
    fun getPublicKeys(): ResponseEntity<MinecraftPublicKeysResponse> {
        return ResponseEntity.ok(minecraftService.getPublicKeys())
    }

    @GetMapping("/sessionserver/session/minecraft/profile/{uuid}")
    fun getSessionProfile(
        @PathVariable uuid: String,
        @RequestParam(name = "unsigned", defaultValue = "true") unsigned: Boolean
    ): ResponseEntity<MinecraftSessionProfileResponse> {
        return ResponseEntity.ok(minecraftService.getSessionProfile(uuid, unsigned))
    }

    @PostMapping("/sessionserver/session/minecraft/join")
    @RequireLauncherUpdateToken
    fun joinServer(@Valid @RequestBody request: MinecraftJoinRequest): ResponseEntity<Void> {
        minecraftService.joinServer(request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/sessionserver/session/minecraft/hasJoined")
    fun hasJoined(
        @RequestParam username: String,
        @RequestParam serverId: String,
        @RequestParam(required = false) ip: String?
    ): ResponseEntity<MinecraftSessionProfileResponse> {
        return ResponseEntity.ok(minecraftService.hasJoined(username, serverId, ip))
    }

    @GetMapping("/sessionserver/blockedservers", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getBlockedServers(): ResponseEntity<String> {
        return ResponseEntity.ok(minecraftService.getBlockedServers())
    }
}
