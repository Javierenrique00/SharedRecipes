package com.co.recipes.infraestructure.preferences

import com.russhwolf.settings.Settings

/**
 * Non-secret "remember this profile" preferences.
 *
 * This class deliberately holds **no password**. What keeps a user signed in is the
 * Supabase session (access + refresh token), which is encrypted at rest by
 * `SecureSessionManager`; the password only ever lives in memory while it is typed.
 * Writing it here would put a plaintext credential in SharedPreferences /
 * NSUserDefaults / java.util.prefs, where any backup, rooted device or `adb` session
 * can read it — and it would add nothing over the refresh token.
 *
 * [settings] is injectable so tests can pass `MapSettings` instead of real storage.
 */
class UserPreferences(private val settings: Settings = Settings()) {

    /** Whether the user asked to be remembered. Turning it off forgets the profile. */
    var rememberMe: Boolean
        get() = settings.getBoolean(KEY_REMEMBER_ME, false)
        set(value) {
            settings.putBoolean(KEY_REMEMBER_ME, value)
            if (!value) clearProfile()
        }

    /** Last signed-in email address, pre-filled on the login screen. Not a secret. */
    var lastEmail: String?
        get() = settings.getStringOrNull(KEY_LAST_EMAIL)
        set(value) {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) settings.remove(KEY_LAST_EMAIL)
            else settings.putString(KEY_LAST_EMAIL, trimmed)
        }

    /** Display name last typed at sign-up, pre-filled when registering again. */
    var lastDisplayName: String?
        get() = settings.getStringOrNull(KEY_LAST_DISPLAY_NAME)
        set(value) {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) settings.remove(KEY_LAST_DISPLAY_NAME)
            else settings.putString(KEY_LAST_DISPLAY_NAME, trimmed)
        }

    /** Forgets the remembered profile (the encrypted session is managed by Supabase). */
    fun clearProfile() {
        settings.remove(KEY_LAST_EMAIL)
        settings.remove(KEY_LAST_DISPLAY_NAME)
    }

    private companion object {
        const val KEY_REMEMBER_ME = "auth.remember_me"
        const val KEY_LAST_EMAIL = "auth.last_email"
        const val KEY_LAST_DISPLAY_NAME = "auth.last_display_name"
    }
}
