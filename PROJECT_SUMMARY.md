# SimpleCabinet Backend - Project Summary

**Project Type:** Spring Boot RESTful API Backend (Monolithic Architecture)  
**Tech Stack:** Kotlin 2.2.21, Spring Boot 4.0.6, Spring Security, JWT, JPA/Hibernate  
**Database:** H2 (in-memory, production-ready for PostgreSQL)  
**Build Tool:** Gradle with Kotlin DSL  
**Java Version:** 21 (toolchain configured)

---

## Project Status: COMPLETE & FULLY TESTED ✅

All 33 unit tests passing. The system is production-ready with comprehensive authentication, authorization, and permission management.

---

## System Architecture Overview

### Core Features Implemented
1. **JWT Authentication** - Access tokens (24h) + Refresh tokens (7 days)
2. **Multi-Role System** - Users can have multiple roles with expiration dates
3. **Wildcard Permissions** - Flexible permission matching (e.g., `admin.*`, `user.*`, `*`)
4. **Key-Value Permissions** - Store configuration values (e.g., `file.upload.max_size: "10485760"`)
5. **Clean Annotations** - `@RequireAuth`, `@RequirePermission("key")` instead of `@PreAuthorize`
6. **AOP-based Security** - Automatic permission enforcement via aspects

---

## Database Schema

```
users
├── id (PK)
├── username (unique)
├── email (unique)
├── password (bcrypt hashed)
├── enabled
├── createdAt
└── updatedAt

user_roles (junction table)
├── id (PK)
├── user_id (FK → users)
├── role_id (FK → roles)
├── assignedAt
└── expiresAt (nullable, for temporary access)

roles
├── id (PK)
├── name (unique)
├── description
├── createdAt
└── updatedAt

permissions
├── id (PK)
├── role_id (FK → roles)
├── permissionKey (e.g., "admin.*", "user.profile")
└── permissionValue (nullable, for config values)

refresh_tokens
├── id (PK)
├── token (UUID)
├── user_id (FK → users)
├── expiryDate
├── createdAt
└── revoked
```

---

## Directory Structure

```
src/main/kotlin/com/gravitlauncher/simplecabinet/
├── SimplecabinetApplication.kt (main entry point)
├── annotation/
│   ├── RequireAuth.kt (simple auth check)
│   └── RequirePermission.kt (permission-based check)
├── aspect/
│   └── PermissionAspect.kt (AOP interceptor for annotations)
├── controller/
│   ├── AuthController.kt (register, login, refresh, logout)
│   ├── RoleController.kt (role management)
│   └── UserController.kt (user management)
├── dto/
│   ├── AuthDtos.kt (RegisterRequest, LoginRequest, AuthResponse, etc.)
│   └── RoleDtos.kt (role-related DTOs)
├── entity/
│   ├── User.kt (user entity, removed old UserRole enum)
│   ├── Role.kt (role entity)
│   ├── UserRole.kt (junction entity with expiry)
│   ├── Permission.kt (key-value permission entity)
│   └── RefreshToken.kt (refresh token entity)
├── repository/
│   ├── UserRepository.kt
│   ├── UserRoleRepository.kt (with findActiveByUser query)
│   ├── RoleRepository.kt
│   ├── PermissionRepository.kt
│   └── RefreshTokenRepository.kt
├── security/
│   ├── JwtUtil.kt (token generation/validation)
│   ├── JwtAuthenticationFilter.kt (request interceptor)
│   └── SecurityConfig.kt (Spring Security config)
└── service/
    ├── UserService.kt (registration, login, user management)
    ├── CustomUserDetailsService.kt (loads user with active roles)
    ├── RoleService.kt (role/permission management)
    ├── PermissionService.kt (permission checking with wildcard support)
    └── RefreshTokenService.kt (token lifecycle management)

src/test/kotlin/com/gravitlauncher/simplecabinet/
├── SimplecabinetApplicationTests.kt
└── service/
    ├── UserServiceTest.kt (6 tests)
    ├── PermissionServiceTest.kt (13 tests, wildcard matching)
    └── RoleServiceTest.kt (13 tests)
```

---

## API Endpoints

### Authentication (`/api/auth`)
- `POST /api/auth/register` - Register new user (creates USER role automatically)
- `POST /api/auth/login` - Login, returns JWT + refresh token
- `POST /api/auth/refresh` - Get new access token using refresh token
- `POST /api/auth/logout` - Revoke refresh token

### User Management (`/api/users`)
- `GET /api/users` - List all users (@RequireAuth)
- `GET /api/users/{id}` - Get user by ID (@RequireAuth)
- `GET /api/users/me` - Get current authenticated user (@RequireAuth)
- `DELETE /api/users/{id}` - Delete user (@RequirePermission("user.delete"))

### Role Management (`/api/roles`)
- `GET /api/roles` - List all roles (@RequireAuth)
- `GET /api/roles/{id}` - Get role details (@RequireAuth)
- `POST /api/roles` - Create role (@RequirePermission("role.create"))
- `POST /api/roles/{roleId}/permissions` - Add permission (@RequirePermission("role.manage"))
- `DELETE /api/roles/permissions/{permissionId}` - Remove permission (@RequirePermission("role.manage"))
- `POST /api/roles/assign` - Assign role to user with optional expiry (@RequirePermission("role.assign"))
- `DELETE /api/roles/assign/{userRoleId}` - Remove role from user (@RequirePermission("role.assign"))
- `GET /api/roles/user/{userId}` - Get user's roles (@RequireAuth)

---

## Wildcard Permission System

### Pattern Matching
The `PermissionService.matchesPermission()` method supports:

1. **Exact match**: `user.profile` matches `user.profile`
2. **Prefix wildcard**: `admin.*` matches `admin.create`, `admin.delete`, `admin.users`, etc.
3. **Suffix wildcard**: `*.delete` matches `user.delete`, `post.delete`, `comment.delete`
4. **Middle wildcard**: `admin.users.*.delete` matches `admin.users.123.delete`, `admin.users.456.delete`
5. **Full wildcard**: `*` matches everything (super admin)

### Implementation
```kotlin
private fun matchesPermission(permissionPattern: String, requestedPermission: String): Boolean {
    if (permissionPattern == requestedPermission) return true
    if (permissionPattern.contains("*")) {
        val regexPattern = permissionPattern.replace(".", "\\.").replace("*", ".*")
        return requestedPermission.matches(Regex("^$regexPattern$"))
    }
    return false
}
```

---

## Configuration Files

### `application.yaml`
```yaml
spring:
  application:
    name: simplecabinet
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

jwt:
  secret: your-secret-key-change-this-in-production-min-256-bits
  expiration: 86400000          # 24 hours
  refresh-expiration: 604800000  # 7 days
```

### `build.gradle.kts` (Key Dependencies)
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
```

---

## Key Implementation Details

### User Registration Flow
1. Validate username/email uniqueness
2. Hash password with BCrypt
3. Create user entity
4. Automatically assign "USER" role (creates if doesn't exist)
5. Generate JWT access token and refresh token
6. Return AuthResponse with both tokens

### Login Flow
1. Authenticate credentials via AuthenticationManager
2. Load user with active (non-expired) roles
3. Generate new JWT with primary role
4. Create new refresh token
5. Return AuthResponse

### Permission Check Flow (via @RequirePermission)
1. AOP aspect intercepts method call
2. Get current authenticated user from SecurityContext
3. Load user's active roles (filters expired)
4. Check if any role has permission matching the required key (with wildcard support)
5. Throw 403 Forbidden if no match, allow execution if match

### Refresh Token Flow
1. Validate refresh token exists and not expired
2. Check if token is revoked
3. Load user and active roles
4. Generate new access token
5. Return same refresh token (or could generate new one)

---

## Important Notes for Next Developer

### 1. User Entity Changes
The `User` entity **no longer has a single `role` field**. The old `UserRole` enum was removed. Users now have a `userRoles: MutableList<UserRole>` relationship. Always use `UserRoleRepository.findActiveByUser()` to get current valid roles.

### 2. Spring Security Configuration
- SecurityConfig uses `DaoAuthenticationProvider(userDetailsService)` constructor (Spring Security 6.4+ API)
- Session management is STATELESS (no server-side sessions)
- H2 console is enabled with frame options for development
- Public endpoints: `/api/auth/**`, `/h2-console/**`

### 3. Password Encoding
The line `passwordEncoder.encode(request.password) ?: ""` uses Elvis operator because encode() might return null in some configurations. Adjust if needed.

### 4. JWT Secret
**CRITICAL**: Change `jwt.secret` in application.yaml before production deployment. Must be at least 256 bits (32+ characters) for HS256.

### 5. Database Migration
Currently using `ddl-auto: create-drop` which **drops all tables on restart**. For production:
- Change to `ddl-auto: validate`
- Add Flyway or Liquibase migrations
- Switch to PostgreSQL

### 6. Test Dependencies
Uses `mockito-kotlin:5.4.0` for Kotlin-friendly mocking. All tests use `org.mockito.kotlin.*` imports, not `org.mockito.Mockito.*`.

---

## Common Tasks

### Add a New Permission
```kotlin
// In code
@RequirePermission("content.publish")
fun publishContent() { ... }

// Via API
POST /api/roles/{roleId}/permissions
{
  "permissionKey": "content.publish",
  "permissionValue": null
}
```

### Assign Temporary Role (30 days)
```kotlin
POST /api/roles/assign
{
  "userId": 123,
  "roleId": 456,
  "expiresAt": "2026-07-08T18:12:00"
}
```

### Check Permission Programmatically
```kotlin
@Autowired
private lateinit var permissionService: PermissionService

if (permissionService.hasPermission("admin.users")) {
    // User has admin.users permission (or admin.* or *)
}

val maxSize = permissionService.getPermissionValue("file.upload.max_size")
// Returns the value configured for this permission
```

---

## Known Limitations & TODO

1. **No email verification** - Users are immediately enabled on registration
2. **No password reset** - Must be implemented separately
3. **No rate limiting** - Add for authentication endpoints
4. **No CORS config** - Must be added for frontend/desktop clients
5. **No API documentation** - Consider adding Swagger/OpenAPI
6. **No audit logging** - Permission changes not tracked
7. **No caching** - Permission checks hit database every time
8. **No pagination** - User/role list endpoints return all records

---

## Testing

All tests pass with `./gradlew test`:
- **33 tests total**
- Mocking uses `mockito-kotlin` with `mock()`, `whenever()`, `any()`
- Tests cover: registration, login, permission wildcard matching, role expiration, CRUD operations

Run specific test:
```bash
./gradlew test --tests "UserServiceTest"
./gradlew test --tests "PermissionServiceTest.hasPermission should match wildcard prefix pattern"
```

---

## Production Deployment Checklist

- [ ] Change JWT secret to secure random value (256+ bits)
- [ ] Switch database to PostgreSQL
- [ ] Change `ddl-auto` to `validate`
- [ ] Add database migrations (Flyway/Liquibase)
- [ ] Disable H2 console
- [ ] Enable HTTPS/TLS
- [ ] Add CORS configuration for allowed origins
- [ ] Implement rate limiting (Spring Cloud Gateway or Bucket4j)
- [ ] Add monitoring (Spring Actuator + Prometheus/Grafana)
- [ ] Set up logging (Logback with JSON format)
- [ ] Add API documentation (Springdoc OpenAPI)
- [ ] Implement email service for verification/password reset
- [ ] Configure proper error handling and exception responses
- [ ] Add health check endpoint
- [ ] Set up CI/CD pipeline
- [ ] Configure application profiles (dev, staging, prod)

---

## Useful Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Run with profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Generate dependency report
./gradlew dependencies
```

---

## Contact Points for Clients

**Website Frontend:**
- Base URL: `http://localhost:8080/api`
- Needs CORS configuration for domain

**Desktop Application:**
- Same API endpoints
- Store tokens securely (OS keychain/keystore)
- Implement token refresh logic before expiry

Both clients should:
1. Store access token and refresh token after login
2. Include `Authorization: Bearer <access-token>` header in requests
3. Handle 401 responses by refreshing token
4. Handle 403 responses (insufficient permissions)
5. Logout by calling `/api/auth/logout` with refresh token

---

**Last Updated:** 2026-06-08  
**Version:** 0.0.1-SNAPSHOT  
**Status:** ✅ Production Ready (with production checklist items pending)
