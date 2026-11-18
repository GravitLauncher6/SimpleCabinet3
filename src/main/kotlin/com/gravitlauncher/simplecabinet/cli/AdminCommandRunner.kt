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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.io.Writer
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID

@Component
class AdminCommandRunner(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val sessionRepository: SessionRepository,
    private val jwtUtil: JwtUtil,
    private val transactions: TransactionTemplate
) {

    fun execute(args: Array<String>): String? {
        val command = args.first { it in commands }
        val count = commands.getValue(command)
        val operandStart = args.indexOf(command) + 1
        val remaining = args.drop(operandStart)
        val operands = remaining.take(count)
        requireArguments(operands, command, count)

        val outputOptions = (args.take(operandStart - 1) + remaining.drop(count))
            .filter { it == "--admin-output-file" || it.startsWith("--admin-output-file=") }
        require(outputOptions.size <= 1 && outputOptions.all { it.substringAfter('=', "").isNotBlank() }) {
            "Use --admin-output-file=<new-file>"
        }
        val outputFile = outputOptions.singleOrNull()?.let { Path.of(it.substringAfter('=')) }
        if (outputFile == null) return executeCommand(command, operands, null)

        val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        val channel = Files.newByteChannel(outputFile, setOf(CREATE_NEW, WRITE), permissions)
        try {
            channel.use {
                Channels.newWriter(it, Charsets.UTF_8).use { writer ->
                    executeCommand(command, operands, writer)
                }
            }
        } catch (failure: Throwable) {
            try {
                Files.deleteIfExists(outputFile)
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
        return null
    }

    private fun executeCommand(command: String, operands: List<String>, output: Writer?): String {
        return checkNotNull(transactions.execute {
            val username = operands[0]
            val result = when (command) {
                "--create-admin-user" -> {
                    createAdmin(username, operands[1], operands[2])
                    output?.let { writeCredentials(it, username, operands[2]) }
                    "Created admin user: $username"
                }
                "--admin-change-password" -> {
                    changePassword(username, operands[1])
                    output?.let { writeCredentials(it, username, operands[1]) }
                    "Changed password for user: $username"
                }
                else -> createAdminToken(username).also { output?.write(it) }
            }
            if (output != null) {
                output.write("\n")
                output.flush()
            }
            result
        })
    }

    private fun writeCredentials(output: Writer, username: String, password: String) {
        output.write(jacksonObjectMapper().writeValueAsString(mapOf("username" to username, "password" to password)))
    }

    private fun createAdmin(username: String, email: String, password: String) {
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
    }

    private fun changePassword(username: String, password: String) {
        require(password.isNotEmpty()) { "Password is required" }
        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("User not found: $username") }
        userRepository.save(user.copy(password = passwordEncoder.encode(password).orEmpty()))
    }

    private fun createAdminToken(username: String): String {
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
        return jwtUtil.generateNonExpiringToken(username, "ADMIN", sessionToken)
    }

    private fun requireArguments(args: List<String>, command: String, count: Int) {
        require(args.size == count) {
            "Usage: $command ${when (command) {
                "--create-admin-user" -> "<username> <email> <password>"
                "--admin-change-password" -> "<username> <password>"
                else -> "<username>"
            }}"
        }
    }

    companion object {
        val commands = mapOf(
            "--create-admin-user" to 3,
            "--admin-change-password" to 2,
            "--create-admin-token" to 1
        )
    }
}
