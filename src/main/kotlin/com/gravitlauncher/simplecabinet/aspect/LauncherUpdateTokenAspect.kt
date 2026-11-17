package com.gravitlauncher.simplecabinet.aspect

import com.gravitlauncher.simplecabinet.service.ArtifactUpdateService
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class LauncherUpdateTokenAspect(
    private val artifactUpdateService: ArtifactUpdateService
) {

    @Before("@annotation(com.gravitlauncher.simplecabinet.annotation.RequireLauncherUpdateToken)")
    fun checkLauncherUpdateToken() {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        artifactUpdateService.validateVerifiedToken(request?.getHeader("X-Launcher-Update-Token"))
    }
}
