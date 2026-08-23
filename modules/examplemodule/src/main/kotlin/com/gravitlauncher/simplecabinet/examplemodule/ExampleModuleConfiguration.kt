package com.gravitlauncher.simplecabinet.examplemodule

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@Import(ExampleController::class)
class ExampleModuleConfiguration {
    @Bean
    @Order(0)
    fun exampleSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .securityMatcher("/example-module")
        .csrf { it.disable() }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()
}
