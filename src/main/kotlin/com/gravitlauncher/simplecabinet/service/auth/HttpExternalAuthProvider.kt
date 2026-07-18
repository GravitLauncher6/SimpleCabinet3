package com.gravitlauncher.simplecabinet.service.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HttpExternalAuthProvider(
    override val id: String,
    private val properties: HttpExternalAuthProperties,
    private val objectMapper: ObjectMapper
) : PasswordExternalAuthProvider {
    private val client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()

    init {
        require(properties.baseUrl.isNotBlank()) { "external-auth.http.base-url is required" }
        require(properties.authenticateEndpoint.isNotBlank()) {
            "external-auth.http.authenticate-endpoint is required"
        }
        require(properties.refreshEndpoint.isNotBlank()) {
            "external-auth.http.refresh-endpoint is required"
        }
    }

    override fun authenticate(request: ExternalPasswordAuthentication): ExternalAuthentication? {
        val fields = properties.fields
        val response = post(
            properties.authenticateEndpoint,
            mapOf(fields.usernameRequest to request.username, fields.passwordRequest to request.password)
        )
        if (response.statusCode() !in 200..299) return null
        val body = readBody(response)
        if (!body.path(fields.successResponse).asBoolean(false)) return null
        val subject = body.text(fields.subjectResponse) ?: return null
        val refreshToken = body.text(fields.refreshTokenResponse) ?: return null
        return ExternalAuthentication(
            ExternalIdentity(
                providerId = id,
                subject = subject,
                username = body.text(fields.usernameResponse) ?: request.username,
                email = body.text(fields.emailResponse),
                roles = body.roles(fields.rolesResponse)
            ),
            tokenState(refreshToken, body.text(fields.accessTokenResponse))
        )
    }

    override fun validateSession(
        identity: ExternalIdentityReference,
        state: ExternalSessionState
    ): ExternalSessionValidation {
        if (state.type != "remote-token") return ExternalSessionValidation.ReauthenticationRequired
        val tokenState = objectMapper.readValue(state.payload, RemoteTokenState::class.java)
        val fields = properties.fields
        val response = post(
            properties.refreshEndpoint,
            mapOf(fields.refreshTokenRequest to tokenState.refreshToken)
        )
        if (response.statusCode() !in 200..299) return ExternalSessionValidation.ReauthenticationRequired
        val body = readBody(response)
        if (!body.path(fields.successResponse).asBoolean(false)) {
            return ExternalSessionValidation.ReauthenticationRequired
        }
        val rotatedRefreshToken = body.text(fields.refreshTokenResponse) ?: tokenState.refreshToken
        return ExternalSessionValidation.Valid(
            tokenState(rotatedRefreshToken, body.text(fields.accessTokenResponse) ?: tokenState.accessToken)
        )
    }

    private fun tokenState(refreshToken: String, accessToken: String?) = ExternalSessionState(
        type = "remote-token",
        payload = objectMapper.writeValueAsString(RemoteTokenState(refreshToken, accessToken))
    )

    private fun post(endpoint: String, body: Map<String, String>): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(resolve(endpoint))
            .timeout(properties.readTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        properties.headers.forEach(builder::header)
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun resolve(endpoint: String) =
        URI.create(properties.baseUrl.trimEnd('/') + "/" + endpoint.trimStart('/'))

    private fun readBody(response: HttpResponse<String>): JsonNode =
        response.body().takeIf(String::isNotBlank)?.let(objectMapper::readTree)
            ?: objectMapper.createObjectNode()

    private fun JsonNode.text(field: String): String? =
        path(field).takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private fun JsonNode.roles(field: String): Set<String> = path(field).let {
        when {
            it.isArray -> it.mapNotNull { role -> role.takeIf(JsonNode::isTextual)?.asText() }.toSet()
            it.isTextual -> it.asText().split(",").map(String::trim).filter(String::isNotEmpty).toSet()
            else -> emptySet()
        }
    }

    data class RemoteTokenState(
        val refreshToken: String = "",
        val accessToken: String? = null
    )
}
