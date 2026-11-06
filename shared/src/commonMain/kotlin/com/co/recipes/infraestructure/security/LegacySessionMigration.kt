package com.co.recipes.infraestructure.security

import com.russhwolf.settings.Settings

/**
 * Moves the session written by supabase-kt's *plaintext* `SettingsSessionManager`
 * into the [SecureStore], then deletes the plaintext copy.
 *
 * Builds before the secure store used `SettingsSessionManager()` with its default key,
 * so existing installs have a full session JSON under [LEGACY_SESSION_KEY] in
 * SharedPreferences / NSUserDefaults / java.util.prefs. Without this, upgrading would
 * silently sign every user out.
 *
 * Runs exactly once per project ([markerKey]); any failure degrades to "sign in again"
 * rather than to a crash, and the plaintext entry is removed either way.
 */
internal fun migrateLegacySession(
    legacy: Settings,
    store: SecureStore,
    newKey: String,
    markerKey: String,
) {
    if (store.get(markerKey) != null) return

    try {
        val legacySession = runCatching { legacy.getStringOrNull(LEGACY_SESSION_KEY) }.getOrNull()
        if (legacySession != null && store.get(newKey) == null) {
            runCatching { store.put(newKey, legacySession) }
        }
    } finally {
        runCatching { legacy.remove(LEGACY_SESSION_KEY) }
        runCatching { store.put(markerKey, "true") }
    }
}

/** Key used by supabase-kt's `SettingsSessionManager` when no custom key is passed. */
internal const val LEGACY_SESSION_KEY = "session"
