package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Optional<Role>
    fun existsByName(name: String): Boolean
}
