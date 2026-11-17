package com.co.recipes.infraestructure.auth

/**
 * The JVM has no URL-scheme registration, so the recovery email cannot reopen the app.
 * The link points at a loopback address instead and the user pastes the resulting URL back
 * into the reset form (see `AuthViewModel.needsRecoveryLinkPaste`).
 */
actual fun passwordResetRedirectUrl(): String = DESKTOP_RECOVERY_REDIRECT_URL

actual val requiresRecoveryLinkPaste: Boolean = true
