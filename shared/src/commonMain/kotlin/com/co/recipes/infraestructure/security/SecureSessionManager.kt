package com.co.recipes.infraestructure.security

import com.co.recipes.infraestructure.SupabaseConfig
import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * [SessionManager] that keeps the access/refresh tokens in the platform secret store
 * instead of the plaintext preferences used by supabase-kt's `SettingsSessionManager`.
 *
 * The JSON setup mirrors supabase-kt (`encodeDefaults = true`): without it the
 * `expiresAt` / `type` fields are dropped and the restored session cannot be refreshed.
 */
internal class SecureSessionManager(
    private val store: SecureStore = createSecureStore(),
    private val key: String = SupabaseConfig.sessionStorageKey,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        store.put(key, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val raw = store.get(key) ?: throw NoSessionFoundException()
        return try {
            json.decodeFromString(UserSession.serializer(), raw)
        } catch (_: Exception) {
            // Corrupted or older payload must not trap the user on a broken screen:
            // drop it and let them sign in again.
            store.remove(key)
            throw NoSessionFoundException()
        }
    }

    override suspend fun deleteSession() {
        store.remove(key)
    }

    private companion object {
        /** supabase-kt requires `encodeDefaults`; see the class KDoc. */
        val json = Json { encodeDefaults = true }
    }
}
