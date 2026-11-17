package com.co.recipes.infraestructure.auth

/**
 * iOS receives the link itself: `iOSApp.swift` handles it with `.onOpenURL` and forwards it
 * to `handleAuthDeepLink` (see `MainViewController.kt`), which calls
 * `SupabaseClient.handleDeeplinks`.
 */
actual fun passwordResetRedirectUrl(): String = AUTH_DEEP_LINK

actual val requiresRecoveryLinkPaste: Boolean = false
