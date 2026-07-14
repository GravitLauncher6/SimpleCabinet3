package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.annotation.RequirePermission
import com.gravitlauncher.simplecabinet.dto.ArtifactUpdatePrepareResponse
import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyRequest
import com.gravitlauncher.simplecabinet.dto.ArtifactUpdateVerifyResponse
import com.gravitlauncher.simplecabinet.dto.ArtifactUploadResponse
import com.gravitlauncher.simplecabinet.service.ArtifactUpdateService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/artifact-updates")
@Validated
class ArtifactUpdateController(
    private val artifactUpdateService: ArtifactUpdateService
) {

    @PostMapping("/prepare")
    fun prepare(): ResponseEntity<ArtifactUpdatePrepareResponse> {
        return ResponseEntity.ok(artifactUpdateService.prepare())
    }

    @PostMapping("/verify")
    fun verify(@Valid @RequestBody request: ArtifactUpdateVerifyRequest): ResponseEntity<ArtifactUpdateVerifyResponse> {
        return ResponseEntity.ok(artifactUpdateService.verify(request))
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequirePermission("admin.cas.write")
    fun upload(
        @RequestParam @NotBlank variant: String,
        @RequestPart("file") file: MultipartFile,
        @RequestPart("publicKey") publicKey: MultipartFile,
        @RequestPart("privateKey") privateKey: MultipartFile,
        @RequestParam(defaultValue = "false") deprecated: Boolean
    ): ResponseEntity<ArtifactUploadResponse> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(artifactUpdateService.upload(variant, file, publicKey, privateKey, deprecated))
    }
}
