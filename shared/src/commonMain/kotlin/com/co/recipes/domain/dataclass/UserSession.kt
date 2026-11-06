package com.co.recipes.domain.dataclass

/** The signed-in user as the UI cares about them. */
data class UserSession(
    val userId: String,
    val displayName: String,
    val role: UserRole,
) {
    val isAdmin: Boolean get() = role.isAdmin
}
