package com.gravitlauncher.simplecabinet.dto

data class ArtifactUpdatePrepareResponse(
    val challenge: String,
    val token: String
)

data class ArtifactUpdateVerifyRequest(
    val variant: String,
    val signature: String,
    val token: String
)

data class ArtifactUpdateVerifyResponse(
    val updateRequired: Boolean,
    val verifiedToken: String? = null,
    val url: String? = null
)

data class ArtifactUploadResponse(
    val variant: String,
    val storageKey: String,
    val url: String,
    val deprecated: Boolean
)
