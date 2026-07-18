package com.gravitlauncher.simplecabinet.service.auth

class ExternalAuthSystem(
    private val properties: ExternalAuthProperties,
    providers: List<ExternalAuthProvider>,
    private val stateCipher: ExternalSessionStateCipher?
) {
    init {
        require(providers.map(ExternalAuthProvider::id).distinct().size == providers.size) {
            "External provider IDs must be unique"
        }
    }

    private val providersById = providers.associateBy(ExternalAuthProvider::id)

    val enabled: Boolean
        get() = properties.enabled

    val defaultProviderId: String
        get() = properties.providerId

    fun provider(providerId: String): ExternalAuthProvider? =
        if (enabled) providersById[providerId] else null

    fun defaultProvider(): PasswordExternalAuthProvider? =
        provider(defaultProviderId) as? PasswordExternalAuthProvider

    fun encrypt(state: ExternalSessionState): String =
        requireNotNull(stateCipher) { "External authentication is disabled" }.encrypt(state)

    fun decrypt(payload: String): ExternalSessionState =
        requireNotNull(stateCipher) { "External authentication is disabled" }.decrypt(payload)
}
