package com.gravitlauncher.simplecabinet.exception

enum class ErrorCode(val code: String, val message: String) {
    // Authentication & Authorization errors (1xxx)
    INVALID_CREDENTIALS("AUTH_001", "Invalid username or password"),
    UNAUTHORIZED("AUTH_002", "Authentication required"),
    ACCESS_DENIED("AUTH_003", "Access denied"),
    TOKEN_EXPIRED("AUTH_004", "Token has expired"),
    INVALID_TOKEN("AUTH_005", "Invalid token"),
    TOKEN_NOT_FOUND("AUTH_006", "Refresh token not found"),
    
    // User errors (2xxx)
    USER_NOT_FOUND("USER_001", "User not found"),
    USER_ALREADY_EXISTS("USER_002", "User already exists"),
    INVALID_USER_DATA("USER_003", "Invalid user data"),
    USER_CANNOT_DELETE_SELF("USER_004", "Cannot delete your own account"),
    
    // Role errors (3xxx)
    ROLE_NOT_FOUND("ROLE_001", "Role not found"),
    ROLE_ALREADY_EXISTS("ROLE_002", "Role already exists"),
    INVALID_ROLE_DATA("ROLE_003", "Invalid role data"),
    ROLE_IN_USE("ROLE_004", "Role is assigned to users and cannot be deleted"),
    
    // Permission errors (4xxx)
    PERMISSION_NOT_FOUND("PERM_001", "Permission not found"),
    INVALID_PERMISSION("PERM_002", "Invalid permission"),
    
    // Validation errors (5xxx)
    VALIDATION_ERROR("VAL_001", "Validation error"),
    INVALID_INPUT("VAL_002", "Invalid input data"),
    
    // General errors (9xxx)
    INTERNAL_ERROR("GEN_001", "Internal server error"),
    RESOURCE_NOT_FOUND("GEN_002", "Resource not found"),
    BAD_REQUEST("GEN_003", "Bad request"),
    DUPLICATE_RESOURCE("GEN_004", "Resource already exists")
}
