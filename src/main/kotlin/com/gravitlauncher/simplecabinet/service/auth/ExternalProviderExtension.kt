package com.gravitlauncher.simplecabinet.service.auth

import kotlin.reflect.KClass

data class ExternalCapability<Request : Any, Response : Any>(
    val name: String,
    val requestType: KClass<Request>,
    val responseType: KClass<Response>
)

data class ExternalUserContext(
    val identity: ExternalIdentityReference,
    val sessionState: ExternalSessionState?
)

interface ExternalProviderExtension<Request : Any, Response : Any> {
    val providerId: String
    val capability: ExternalCapability<Request, Response>

    fun execute(context: ExternalUserContext, request: Request): Response
}

class ExternalExtensionRegistry(
    extensions: List<ExternalProviderExtension<*, *>>
) {
    init {
        require(extensions.map { it.providerId to it.capability.name }.distinct().size == extensions.size) {
            "External provider extensions must have unique provider/capability pairs"
        }
    }

    private val extensionsByKey = extensions.associateBy { it.providerId to it.capability.name }

    @Suppress("UNCHECKED_CAST")
    fun <Request : Any, Response : Any> execute(
        providerId: String,
        capability: ExternalCapability<Request, Response>,
        context: ExternalUserContext,
        request: Request
    ): Response {
        require(capability.requestType.isInstance(request)) { "Invalid capability request type" }
        val extension = extensionsByKey[providerId to capability.name]
            as? ExternalProviderExtension<Request, Response>
            ?: throw UnsupportedOperationException(
                "Provider '$providerId' does not support capability '${capability.name}'"
            )
        val response = extension.execute(context, request)
        require(capability.responseType.isInstance(response)) { "Invalid capability response type" }
        return response
    }
}
