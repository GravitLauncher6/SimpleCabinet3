package com.gravitlauncher.simplecabinet.controller

import com.gravitlauncher.simplecabinet.annotation.RequireAuth
import com.gravitlauncher.simplecabinet.annotation.RequirePermission
import com.gravitlauncher.simplecabinet.dto.*
import com.gravitlauncher.simplecabinet.service.CasStorageService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/cas")
@Validated
class CasController(
    private val casStorageService: CasStorageService
) {

    @GetMapping("/blobs/{hash}")
    @RequireAuth
    fun getBlobByHash(@PathVariable hash: String): ResponseEntity<BlobResponse> {
        return ResponseEntity.ok(casStorageService.getBlobByHash(hash))
    }

    @PostMapping("/blobs")
    @RequirePermission("admin.cas.write")
    fun registerBlob(@Valid @RequestBody request: BlobRegistrationRequest): ResponseEntity<BlobResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.registerBlob(request))
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequirePermission("admin.cas.write")
    fun upload(@RequestPart("file") file: MultipartFile): ResponseEntity<BlobResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.upload(file))
    }

    @PostMapping("/upload/bulk", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequirePermission("admin.cas.write")
    fun bulkUpload(@RequestPart("files") files: List<MultipartFile>): ResponseEntity<List<BlobResponse>> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.bulkUpload(files))
    }

    @PostMapping("/directories")
    @RequirePermission("admin.cas.write")
    fun createDirectory(@Valid @RequestBody request: CreateDirectoryRequest): ResponseEntity<DirectoryResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.createDirectory(request))
    }

    @GetMapping("/directories")
    @RequireAuth
    fun getDirectory(@RequestParam @NotBlank directoryKey: String): ResponseEntity<DirectoryResponse> {
        return ResponseEntity.ok(casStorageService.getDirectory(directoryKey))
    }

    @GetMapping("/directories/list ")
    @RequireAuth
    fun getDirectories(): ResponseEntity<List<DirectoryResponse>> {
        return ResponseEntity.ok(casStorageService.getDirectories())
    }

    @GetMapping("/branches")
    @RequireAuth
    fun listBranches(@RequestParam @NotBlank directoryKey: String): ResponseEntity<List<DirectoryBranchResponse>> {
        return ResponseEntity.ok(casStorageService.listBranches(directoryKey))
    }

    @PostMapping("/versions")
    @RequirePermission("admin.cas.write")
    fun publishVersion(@Valid @RequestBody request: PublishDirectoryVersionRequest): ResponseEntity<DirectoryVersionResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.publishVersion(request))
    }

    @GetMapping("/versions/latest")
    @RequireAuth
    fun getLatestVersion(
        @RequestParam @NotBlank directoryKey: String,
        @RequestParam @NotBlank branchName: String
    ): ResponseEntity<DirectoryVersionResponse> {
        return ResponseEntity.ok(casStorageService.getLatestVersion(directoryKey, branchName))
    }

    @GetMapping("/versions/{versionId}")
    @RequireAuth
    fun getVersion(
        @PathVariable versionId: Long,
        @RequestParam @NotBlank directoryKey: String
    ): ResponseEntity<DirectoryVersionResponse> {
        return ResponseEntity.ok(casStorageService.getVersion(directoryKey, versionId))
    }

    @PostMapping("/fork")
    @RequirePermission("admin.cas.write")
    fun forkDirectory(@Valid @RequestBody request: ForkDirectoryRequest): ResponseEntity<DirectoryVersionResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(casStorageService.forkDirectory(request))
    }
}
