package com.gravitlauncher.simplecabinet.repository

import com.gravitlauncher.simplecabinet.entity.Permission
import com.gravitlauncher.simplecabinet.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByRole(role: Role): List<Permission>
    fun findByRoleAndPermissionKey(role: Role, permissionKey: String): List<Permission>

    @Query(
        value = """
        WITH RECURSIVE effective_roles(role_id, depth) AS (
            SELECT ur.role_id, 0
            FROM user_roles ur
            WHERE ur.user_id = :userId
              AND (ur.expires_at IS NULL OR ur.expires_at > :currentTime)

            UNION ALL

            SELECT r.parent_role_id, er.depth + 1
            FROM roles r
            JOIN effective_roles er ON er.role_id = r.id
            WHERE r.parent_role_id IS NOT NULL
        )
        SELECT p.*
        FROM permissions p
        JOIN effective_roles er ON er.role_id = p.role_id
        ORDER BY er.depth, p.id
        """,
        nativeQuery = true
    )
    fun findEffectiveByUserId(userId: Long, currentTime: LocalDateTime): List<Permission>
}
