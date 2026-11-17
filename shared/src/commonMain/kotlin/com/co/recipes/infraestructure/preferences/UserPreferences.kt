package com.co.recipes.infraestructure.preferences

import com.russhwolf.settings.Settings
import kotlin.time.Clock

/**
 * A password reset the user asked for but has not completed yet.
 *
 * Deliberately not a secret: it is the address the email went to plus a timestamp, used to
 * recognise the recovery session as "this user is choosing a new password" instead of a
 * normal sign-in. No password or token is stored here.
 */
data class PendingPasswordReset(
    val email: String,
    val requestedAtMillis: Long,
)

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
 * The recovery marker follows the same rule: an email address and a timestamp only.
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

    /**
     * Records that a recovery email was just requested for [email].
     *
     * Set *before* the link can be opened: on Android and iOS the click can start a fresh
     * process, and this marker is what tells the app to show "choose a new password"
     * instead of the recipe list once the link has signed the user in.
     */
    fun rememberPendingPasswordReset(
        email: String,
        requestedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        val address = email.trim()
        if (address.isBlank()) {
            clearPendingPasswordReset()
            return
        }
        settings.putString(KEY_RECOVERY_EMAIL, address)
        settings.putLong(KEY_RECOVERY_REQUESTED_AT, requestedAtMillis)
    }

    /**
     * The pending request, or `null` when there is none or it is older than
     * [RECOVERY_LINK_TTL_MILLIS] — Supabase expires the recovery email after an hour, so an
     * older marker can only produce a confusing "set a password" screen.
     *
     * [nowMillis] is a parameter so the expiry is testable without a fake clock.
     */
    fun pendingPasswordReset(
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): PendingPasswordReset? {
        val address = settings.getStringOrNull(KEY_RECOVERY_EMAIL)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val requestedAt = settings.getLongOrNull(KEY_RECOVERY_REQUESTED_AT) ?: return null
        val age = nowMillis - requestedAt
        // A clock that moved backwards would otherwise keep a marker alive forever.
        if (age !in 0..RECOVERY_LINK_TTL_MILLIS) return null
        return PendingPasswordReset(address, requestedAt)
    }

    /** Drops the marker once the reset is done, abandoned, or replaced by a normal sign-in. */
    fun clearPendingPasswordReset() {
        settings.remove(KEY_RECOVERY_EMAIL)
        settings.remove(KEY_RECOVERY_REQUESTED_AT)
    }

    private companion object {
        const val KEY_REMEMBER_ME = "auth.remember_me"
        const val KEY_LAST_EMAIL = "auth.last_email"
        const val KEY_LAST_DISPLAY_NAME = "auth.last_display_name"

        // "recovery", not "password": no key here may look like a credential (see
        // UserPreferencesTest.neverWritesACredential).
        const val KEY_RECOVERY_EMAIL = "auth.recovery_email"
        const val KEY_RECOVERY_REQUESTED_AT = "auth.recovery_requested_at"

        /** Matches the recovery email's `otp_expiry` (1 hour) in config.toml. */
        const val RECOVERY_LINK_TTL_MILLIS = 60L * 60L * 1000L
    }
}
