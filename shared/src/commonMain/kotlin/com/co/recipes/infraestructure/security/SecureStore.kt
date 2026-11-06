package com.co.recipes.infraestructure.security

/**
 * Tiny key/value store for the few secrets this app keeps on the device.
 *
 * Only the Supabase session (access + refresh token) is written here. User
 * credentials are never stored anywhere — see `UserPreferences`.
 *
 * Implementations must:
 * - persist across process death (a cold start must not lose the session);
 * - be readable only by this app (Android Keystore / iOS Keychain backed);
 * - **never** fall back to plaintext: when a value cannot be decrypted, [get] returns
 *   `null` so the app asks the user to sign in again instead of crashing.
 */
internal interface SecureStore {

    /** The stored value, or `null` when absent/unreadable. */
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}

/**
 * Platform secret store:
 * - Android: AES-256-GCM under a non-exportable Android Keystore key;
 * - iOS: Keychain (`kSecClassGenericPassword`);
 * - Desktop: `java.util.prefs` — the JDK has no keychain API, so this target stays
 *   plaintext and is only used as the local dev harness (documented in README).
 */
internal expect fun createSecureStore(): SecureStore
