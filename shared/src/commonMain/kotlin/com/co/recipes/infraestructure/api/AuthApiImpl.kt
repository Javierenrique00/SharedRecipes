package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthApiImpl(private val client: SupabaseClient) : AuthApi {

    override val status: Flow<AuthStatus> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthStatus.SignedIn
            is SessionStatus.NotAuthenticated -> AuthStatus.SignedOut
            // Initializing and RefreshFailure both mean "no verdict yet": the session
            // may still be restored or refreshed, so the UI keeps waiting.
            SessionStatus.Initializing, is SessionStatus.RefreshFailure -> AuthStatus.Initializing
        }
    }

    override val isSignedIn: Boolean
        get() = client.auth.currentSessionOrNull() != null

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun signIn(email: String, password: String): Result<Unit> = apiCall {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
    ): Result<Unit> = apiCall {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            if (displayName.isNotBlank()) {
                this.data = buildJsonObject {
                    put("display_name", JsonPrimitive(displayName.trim()))
                }
            }
        }
        Unit
    }

    override suspend fun signOut(): Result<Unit> = apiCall {
        client.auth.signOut()
    }

    override suspend fun currentProfile(): Result<ProfileDto> = apiCall {
        val userId = currentUserId() ?: error("Not signed in")
        client.from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<ProfileDto>()
    }
}
