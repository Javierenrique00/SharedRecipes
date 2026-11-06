package com.co.recipes.infraestructure

import com.co.recipes.infraestructure.security.SecureSessionManager
import com.co.recipes.infraestructure.security.createSecureStore
import com.co.recipes.infraestructure.security.migrateLegacySession
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds the single Supabase client used by the app.
 *
 * Returns null when [SupabaseConfig] still holds placeholders, so the UI can show
 * a setup message instead of crashing on an invalid URL.
 */
fun createSupabaseClientOrNull(): SupabaseClient? {
    if (!SupabaseConfig.isConfigured) return null

    val sessionManager = secureSessionManager()

    return createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
    ) {
        install(Auth) {
            // Tokens live in the platform secret store (Keystore / Keychain), not in
            // plaintext preferences, and refresh in the background.
            this.sessionManager = sessionManager
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            autoSaveToStorage = true
        }
        install(Postgrest)
    }
}

/**
 * Builds the encrypted session manager, first migrating the plaintext session that
 * earlier builds wrote through supabase-kt's `SettingsSessionManager`.
 *
 * [Settings] is only *read* here (and the plaintext entry removed); new sessions go
 * straight to the secure store.
 */
private fun secureSessionManager(): SessionManager {
    val store = createSecureStore()
    migrateLegacySession(
        legacy = Settings(),
        store = store,
        newKey = SupabaseConfig.sessionStorageKey,
        markerKey = SupabaseConfig.sessionMigrationKey,
    )
    return SecureSessionManager(store)
}

