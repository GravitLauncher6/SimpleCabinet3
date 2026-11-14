package com.gravitlauncher.simplecabinet.cli

import com.gravitlauncher.simplecabinet.entity.Role
import com.gravitlauncher.simplecabinet.entity.User
import com.gravitlauncher.simplecabinet.entity.Session
import com.gravitlauncher.simplecabinet.entity.UserRole
import com.gravitlauncher.simplecabinet.repository.SessionRepository
import com.gravitlauncher.simplecabinet.repository.RoleRepository
import com.gravitlauncher.simplecabinet.repository.UserRepository
import com.gravitlauncher.simplecabinet.repository.UserRoleRepository
import com.gravitlauncher.simplecabinet.security.JwtUtil
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

@Component
@Order(0)
class AdminCommandRunner(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val sessionRepository: SessionRepository,
    private val jwtUtil: JwtUtil
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val arguments = args.toList()
        when {
            "--create-admin-user" in arguments -> {
                requireArguments(arguments, "--create-admin-user", 3)
                createAdmin(arguments[arguments.indexOf("--create-admin-user") + 1], arguments[arguments.indexOf("--create-admin-user") + 2], arguments[arguments.indexOf("--create-admin-user") + 3])
                exitProcess(0)
            }
            "--admin-change-password" in arguments -> {
                requireArguments(arguments, "--admin-change-password", 2)
                changePassword(arguments[arguments.indexOf("--admin-change-password") + 1], arguments[arguments.indexOf("--admin-change-password") + 2])
                exitProcess(0)
            }
            "--create-admin-token" in arguments -> {
                requireArguments(arguments, "--create-admin-token", 1)
                createAdminToken(arguments[arguments.indexOf("--create-admin-token") + 1])
                exitProcess(0)
            }
        }
    }

    @Transactional
    fun createAdmin(username: String, email: String, password: String) {
        require(username.isNotBlank() && email.isNotBlank() && password.isNotEmpty()) {
            "Username, email and password are required"
        }
        require(!userRepository.existsByUsername(username)) { "Username already exists: $username" }
        require(!userRepository.existsByEmail(email)) { "Email already exists: $email" }

        val user = userRepository.save(
            User(username = username, email = email, password = passwordEncoder.encode(password).orEmpty())
        )
        val adminRole = roleRepository.findByName("ADMIN").orElseGet {
            roleRepository.save(Role(name = "ADMIN", description = "Administrator"))
        }
        userRoleRepository.save(UserRole(user = user, role = adminRole))
        println("Created admin user: $username")
    }

    @Transactional
    fun changePassword(username: String, password: String) {
        require(password.isNotEmpty()) { "Password is required" }
        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("User not found: $username") }
        userRepository.save(user.copy(password = passwordEncoder.encode(password).orEmpty()))
        println("Changed password for user: $username")
    }

    @Transactional
    fun createAdminToken(username: String) {
        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("User not found: $username") }
        require(userRoleRepository.findActiveByUser(user).any { it.role.name == "ADMIN" }) {
            "User is not an active administrator: $username"
        }

        val sessionToken = "admin-token-${UUID.randomUUID()}"
        sessionRepository.save(
            Session(
                sessionToken = sessionToken,
                user = user,
            expiresAt = Instant.parse("9999-12-31T23:59:59Z"),
                deviceType = "admin-cli"
            )
        )
        println(jwtUtil.generateNonExpiringToken(username, "ADMIN", sessionToken))
    }

    private fun requireArguments(args: List<String>, command: String, count: Int) {
        val index = args.indexOf(command)
        require(index >= 0 && args.size > index + count) {
            "Usage: $command ${when (command) {
                "--create-admin-user" -> "<username> <email> <password>"
                "--admin-change-password" -> "<username> <password>"
                else -> "<username>"
            }}"
        }
    }
}
