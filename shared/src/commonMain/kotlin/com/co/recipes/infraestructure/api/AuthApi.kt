package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.ProfileDto
import kotlinx.coroutines.flow.Flow

/** Coarse auth state the UI cares about, decoupled from supabase-kt's own types. */
enum class AuthStatus {
    /** The stored session has not been read yet (cold start) or is being refreshed. */
    Initializing,
    SignedIn,
    SignedOut,
}

interface AuthApi {

    /**
     * Emits [AuthStatus.Initializing] first, then the result of reading the stored
     * session. Observe this instead of [isSignedIn] during startup: restoring the
     * session is asynchronous, so a one-shot check reports "signed out" before the
     * encrypted session has been read and the user is wrongly sent to the login screen.
     */
    val status: Flow<AuthStatus>

    val isSignedIn: Boolean

    fun currentUserId(): String?

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /**
     * Sends the "Reset password" email. Supabase answers the same whether or not the address
     * belongs to an account, so the UI can show a neutral message instead of leaking which
     * emails are registered.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Completes a recovery link the user pasted into the app (desktop only; Android, iOS and
     * the browser deliver the link to the app themselves). Exchanges the PKCE `code`, or
     * imports the session from an implicit-flow fragment.
     */
    suspend fun completeRecoveryLink(link: String): Result<Unit>

    /** Replaces the password of the recovery session imported by the link. */
    suspend fun updatePassword(newPassword: String): Result<Unit>

    /** Loads the signed-in user's profile, which carries the role used by the UI. */
    suspend fun currentProfile(): Result<ProfileDto>
}
