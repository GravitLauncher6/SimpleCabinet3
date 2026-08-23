package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.ArtifactBuild
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ArtifactBuildRepository : JpaRepository<ArtifactBuild, Long> {
    fun findByVariant(variant: String): Optional<ArtifactBuild>
    fun findAllByVariant(variant: String): List<ArtifactBuild>
    fun findByPublicKey(publicKey: String): Optional<ArtifactBuild>
    fun findFirstByVariantAndDeprecatedFalseOrderByUpdatedAtDesc(variant: String): Optional<ArtifactBuild>
    fun existsByStorageKey(storageKey: String): Boolean
}
