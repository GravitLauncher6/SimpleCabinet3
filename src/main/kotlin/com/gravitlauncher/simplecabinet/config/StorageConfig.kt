package com.gravitlauncher.simplecabinet.config

import com.gravitlauncher.simplecabinet.service.storage.*
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    @Bean
    fun storageService(storageProperties: StorageProperties): StorageService {
        return when (storageProperties.type) {
            StorageType.LOCAL -> LocalFileStorageService(storageProperties)
            StorageType.S3 -> S3StorageService(storageProperties)
        }
    }
}
