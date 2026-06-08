package com.gravitlauncher.simplecabinet.aspect

import com.gravitlauncher.simplecabinet.annotation.RequireAuth
import com.gravitlauncher.simplecabinet.annotation.RequirePermission
import com.gravitlauncher.simplecabinet.service.PermissionService
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Aspect
@Component
class PermissionAspect(
    private val permissionService: PermissionService
) {

    @Before("@annotation(requireAuth)")
    fun checkAuthentication(requireAuth: RequireAuth) {
        if (!permissionService.isAuthenticated()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, requireAuth.message)
        }
    }

    @Before("@annotation(requirePermission)")
    fun checkPermission(requirePermission: RequirePermission) {
        if (!permissionService.isAuthenticated()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        }

        if (!permissionService.hasPermission(requirePermission.value)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, requirePermission.message)
        }
    }
}
