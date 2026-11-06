package com.co.recipes.infraestructure.security

import kotlinx.browser.localStorage

/**
 * Browser secret store backed by `localStorage`.
 *
 * Browsers expose no keychain and [SecureStore] is synchronous, so — exactly like the
 * Desktop dev harness — the Supabase session JSON is written in **plaintext**. That is
 * called out in the README's platform table: `localStorage` is per-origin and readable by
 * any script running on that origin, so an XSS on the app would expose the refresh token.
 *
 * No credential is ever stored here; the only value is the session JSON produced by
 * [SecureSessionManager], and its key is namespaced by project (`sb-<project-ref>-session`)
 * so a build pointed at another Supabase project never reuses it.
 *
 * Every access is guarded: `localStorage` throws in some privacy modes (and when the
 * quota is exhausted), so a failure degrades to "sign in again" (`null`) instead of
 * crashing — the contract documented on [SecureStore].
 */
internal class WebSecureStore : SecureStore {

    override fun get(key: String): String? =
        runCatching { localStorage.getItem(key) }.getOrNull()

    override fun put(key: String, value: String) {
        runCatching { localStorage.setItem(key, value) }
    }

    override fun remove(key: String) {
        runCatching { localStorage.removeItem(key) }
    }
}

internal actual fun createSecureStore(): SecureStore = WebSecureStore()
