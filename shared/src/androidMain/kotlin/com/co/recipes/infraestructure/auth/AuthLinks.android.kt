package com.co.recipes.infraestructure.auth

/**
 * Android receives the link itself: `MainActivity` is registered for
 * `sharedrecipes://auth` in the manifest and forwards the intent to
 * `SupabaseClient.handleDeeplinks` (see `AndroidEntry.kt`).
 */
actual fun passwordResetRedirectUrl(): String = AUTH_DEEP_LINK

actual val requiresRecoveryLinkPaste: Boolean = false
