package com.gravitlauncher.simplecabinet.service.auth

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ExternalAuthSystemTest {
    @Test
    fun `disabled system exposes no provider`() {
        val provider = mock<PasswordExternalAuthProvider>()
        val system = ExternalAuthSystem(
            ExternalAuthProperties(enabled = false),
            listOf(provider),
            null
        )

        assertNull(system.defaultProvider())
        assertNull(system.provider("default"))
    }
}
