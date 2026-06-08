package com.gravitlauncher.simplecabinet.annotation

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequirePermission(
    val value: String,
    val message: String = "Permission denied"
)
