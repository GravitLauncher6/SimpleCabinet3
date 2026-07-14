package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.UserAsset
import com.gravitlauncher.simplecabinet.entity.UserAssetType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserAssetRepository : JpaRepository<UserAsset, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<UserAsset>
    fun findAllByUserIdAndTypeAndActiveTrue(userId: Long, type: UserAssetType): List<UserAsset>
    fun findByAssetIdAndUserIdAndType(assetId: String, userId: Long, type: UserAssetType): Optional<UserAsset>
}
