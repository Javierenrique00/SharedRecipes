package com.co.recipes.infraestructure.auth

import kotlinx.browser.window

/**
 * The browser is its own redirect target: the recovery email comes back to the page the user
 * requested it from.
 *
 * The query string and fragment are stripped so the redirect is the app URL itself. This
 * matters for the GitHub Pages deployment, which lives under `/SharedRecipes/`: the library's
 * default is `window.location.origin`, and that would drop the sub-path.
 *
 * No link handling is needed here — supabase-kt checks the page URL while the Auth plugin
 * initialises, exchanges the `?code=`, imports the session and clears the parameter with
 * `history.replaceState` (`setupPlatform.kt` in auth-kt's `webMain`).
 */
actual fun passwordResetRedirectUrl(): String = stripQueryAndFragment(window.location.href)

actual val requiresRecoveryLinkPaste: Boolean = false
