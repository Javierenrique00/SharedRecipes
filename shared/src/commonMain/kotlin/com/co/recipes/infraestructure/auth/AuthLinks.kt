package com.co.recipes.infraestructure.auth

/**
 * The custom scheme Supabase sends users back to after a recovery (or confirmation) link.
 *
 * It must stay in sync with three places, or the link silently lands nowhere:
 * - the `<intent-filter>` in `androidApp/src/main/AndroidManifest.xml`,
 * - the `CFBundleURLTypes` entry in `iosApp/iosApp/Info.plist`,
 * - the `scheme`/`host` pair configured in [com.co.recipes.infraestructure.createSupabaseClientOrNull].
 *
 * The resulting `scheme://host` URL also has to be listed under
 * **Authentication → URL Configuration → Redirect URLs** in the Supabase dashboard.
 */
const val AUTH_LINK_SCHEME: String = "sharedrecipes"

/** See [AUTH_LINK_SCHEME]. Any value works as long as every layer agrees. */
const val AUTH_LINK_HOST: String = "auth"

/** `sharedrecipes://auth` — the redirect the recovery email comes back to. */
internal const val AUTH_DEEP_LINK: String = "$AUTH_LINK_SCHEME://$AUTH_LINK_HOST"

/**
 * Redirect used on desktop, where the JVM has no way to register a URL scheme.
 *
 * The browser has to open *something*, so the link points at a loopback address. Nothing
 * listens there: the page fails to load, but the address bar keeps the `?code=…`, which the
 * user copies into the app (see `AuthViewModel.needsRecoveryLinkPaste`). It is a dev-harness
 * trade-off, not a production flow.
 */
const val DESKTOP_RECOVERY_REDIRECT_URL: String = "http://127.0.0.1:8090/"

/**
 * Where Supabase should send the user after the recovery link is verified.
 *
 * Mobile targets pass the deep link; the browser passes its own URL (so the GitHub Pages
 * sub-path survives — the library default would be the bare origin); desktop passes
 * [DESKTOP_RECOVERY_REDIRECT_URL].
 */
expect fun passwordResetRedirectUrl(): String

/**
 * True only on desktop, where the app cannot receive the link and the user has to paste it.
 * The login screen uses it to decide whether to render the "paste the link" field.
 */
expect val requiresRecoveryLinkPaste: Boolean

/**
 * The `code` query parameter of a PKCE redirect (`…?code=abc`), or `null` when the URL has
 * none — which also covers the implicit flow, whose tokens live in the fragment instead.
 *
 * Hand-rolled on purpose: common Kotlin has no URL parser, and GoTrue codes are plain
 * URL-safe strings, so splitting is enough. The parameter is compared by name only, so a
 * `?next=…?code=…` value can never be mistaken for the real one.
 */
internal fun extractAuthCode(url: String): String? {
    val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    if (query.isBlank()) return null
    return query
        .split('&')
        .firstOrNull { parameter -> parameter.substringBefore('=').trim() == "code" }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/**
 * Drops `?query`, `#fragment` and a trailing slash, keeping scheme, host and path exactly
 * as written.
 *
 * Used by the web target so `https://host/SharedRecipes/?code=…` becomes
 * `https://host/SharedRecipes` instead of the origin, which would redirect users to the
 * GitHub Pages root. The trailing slash is dropped too, because Supabase matches redirect
 * URLs literally: the dashboard lists `https://host/SharedRecipes`, not `…/SharedRecipes/`.
 */
internal fun stripQueryAndFragment(url: String): String =
    url.substringBefore('?').substringBefore('#').trimEnd('/')
