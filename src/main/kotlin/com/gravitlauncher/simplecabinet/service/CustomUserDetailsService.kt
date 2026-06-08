package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }

        return org.springframework.security.core.userdetails.User(
            user.username,
            user.password,
            user.enabled,
            true,
            true,
            true,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )
    }

    fun loadUserEntityByUsername(username: String): User {
        return userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }
    }
}
