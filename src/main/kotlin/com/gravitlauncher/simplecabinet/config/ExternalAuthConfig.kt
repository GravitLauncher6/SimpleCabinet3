package com.gravitlauncher.simplecabinet.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.gravitlauncher.simplecabinet.service.auth.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableConfigurationProperties(ExternalAuthProperties::class)
class ExternalAuthConfig {
    @Bean
    @ConditionalOnProperty(prefix = "external-auth", name = ["enabled"], havingValue = "true")
    fun externalSessionStateCipher(
        properties: ExternalAuthProperties,
        objectMapper: ObjectMapper
    ) = ExternalSessionStateCipher(properties.stateEncryptionSecret, objectMapper)

    @Bean
    fun oauthStateService(properties: ExternalAuthProperties) =
        OAuthStateService(
            properties.stateEncryptionSecret.ifBlank { "external-auth-disabled-oauth-state" }
        )

    @Bean
    @ConditionalOnProperty(prefix = "external-auth", name = ["enabled"], havingValue = "true")
    @ConditionalOnProperty(prefix = "external-auth", name = ["type"], havingValue = "SQL", matchIfMissing = true)
    fun sqlExternalAuthProvider(
        properties: ExternalAuthProperties,
        passwordEncoder: PasswordEncoder
    ): ExternalAuthProvider = SqlExternalAuthProvider(
        properties.providerId,
        properties.sql,
        passwordEncoder,
        properties.credentialFingerprintSecret
    )

    @Bean
    @ConditionalOnProperty(prefix = "external-auth", name = ["enabled"], havingValue = "true")
    @ConditionalOnProperty(prefix = "external-auth", name = ["type"], havingValue = "HTTP")
    fun httpExternalAuthProvider(
        properties: ExternalAuthProperties,
        objectMapper: ObjectMapper
    ): ExternalAuthProvider = HttpExternalAuthProvider(properties.providerId, properties.http, objectMapper)

    @Bean
    fun externalAuthSystem(
        properties: ExternalAuthProperties,
        providers: ObjectProvider<ExternalAuthProvider>,
        stateCipher: ObjectProvider<ExternalSessionStateCipher>
    ) = ExternalAuthSystem(
        properties,
        providers.orderedStream().toList(),
        stateCipher.ifAvailable
    )

    @Bean
    fun externalExtensionRegistry(
        extensions: ObjectProvider<ExternalProviderExtension<*, *>>
    ) = ExternalExtensionRegistry(extensions.orderedStream().toList())
}
