package com.gravitlauncher.simplecabinet.service

import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }

        val activeRoles = userRoleRepository.findActiveByUser(user, LocalDateTime.now())
        val authorities = activeRoles.map { userRole ->
            SimpleGrantedAuthority("ROLE_${userRole.role.name}")
        }

        return org.springframework.security.core.userdetails.User(
            user.username,
            user.password,
            user.enabled,
            true,
            true,
            true,
            authorities
        )
    }

    fun loadUserEntityByUsername(username: String): User {
        return userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }
    }
}
