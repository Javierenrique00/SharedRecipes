package com.co.recipes.infraestructure

/**
 * Supabase project configuration.
 *
 * [PUBLISHABLE_KEY] is the *publishable* (anon) key and is safe to ship inside the
 * app: it only ever grants what Row Level Security allows. The `service_role` key
 * must NEVER be placed here — it bypasses RLS entirely and anyone who unzips the
 * APK could read and write every row in the database.
 *
 * ## Pointing the app at your project
 *
 * Edit **only the two `const val` values** below — never the `*_MARKER` constants, and
 * never by renaming or re-purposing anything in this file:
 *
 * 1. [URL] = your project URL, `https://<project-ref>.supabase.co`. `<project-ref>` is the
 *    subdomain in the dashboard address bar
 *    (`https://supabase.com/dashboard/project/<project-ref>`) and is also shown as
 *    "Project URL" under **Project Settings → Data API**.
 * 2. [PUBLISHABLE_KEY] = the key from **Project Settings → API Keys**
 *    (`sb_publishable_...`, or the legacy anon key, an `eyJ...` JWT).
 *
 * Nothing else in the codebase needs to change: `createSupabaseClientOrNull()` already
 * reads both values, so the Android, iOS and Desktop targets all pick them up.
 * While a placeholder is still present, the UI shows a "Supabase is not configured"
 * screen (with the reason from [configurationError]) instead of hitting a bad host.
 */
object SupabaseConfig {

    /** Text that must be gone from [URL] before the app can connect. */
    private const val URL_MARKER = "YOUR-PROJECT-REF"

    /** Text that must be gone from [PUBLISHABLE_KEY] before the app can connect. */
    private const val KEY_MARKER = "YOUR-PUBLISHABLE-KEY"

    /**
     * Project URL, e.g. "https://abcdefghijklmnopqrst.supabase.co".
     *
     * Replace this whole string. No trailing slash and no `/rest/v1` suffix —
     * supabase-kt appends the API paths itself.
     */
    const val URL: String = "https://dbwzrjgctmfckdsljznf.supabase.co"

    /**
     * Publishable/anon key, e.g. "sb_publishable_...".
     *
     * Replace this whole string. Paste the key on its own — a legacy anon key starts
     * with `eyJ` and must not keep any `sb_publishable_` prefix.
     */
    const val PUBLISHABLE_KEY: String = "sb_publishable_koUrzzaiGN7h0l-zaBkHuA_sbLT4FqC"

    /**
     * The `<project-ref>` of [URL] ("dbwzrjgctmfckdsljznf" here).
     *
     * Used only to namespace on-device storage keys, so pointing a build at another
     * project never reuses this project's stored session.
     */
    val projectRef: String
        get() = URL.removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .trim()
            .removeSuffix(".supabase.co")
            .ifBlank { "default" }

    /** Storage key for the persisted session (supabase-kt's own namespacing scheme). */
    internal val sessionStorageKey: String
        get() = "sb-$projectRef-session"

    /** Marks the one-time move of the old plaintext session into the secure store. */
    internal val sessionMigrationKey: String
        get() = "sb-$projectRef-session-migrated"

    /** True when both values are usable; see [configurationError] for the reason if not. */
    val isConfigured: Boolean
        get() = configurationError == null

    /**
     * Why the client cannot be created, or `null` when [isConfigured].
     *
     * Rendered by the "Supabase is not configured" screen so the misconfiguration is
     * obvious at a glance instead of silently failing on the first request.
     */
    val configurationError: String?
        get() = when {
            URL.contains(URL_MARKER) ->
                "SupabaseConfig.URL still contains \"$URL_MARKER\". Paste the project " +
                    "URL from Project Settings → Data API, e.g. " +
                    "\"https://<project-ref>.supabase.co\"."

            !URL.startsWith("https://") ->
                "SupabaseConfig.URL must start with \"https://\" (current value: \"$URL\")."

            URL != URL.trim() || URL.endsWith("/") ->
                "SupabaseConfig.URL has surrounding spaces or a trailing slash " +
                    "(current value: \"$URL\")."

            PUBLISHABLE_KEY.contains(KEY_MARKER) ->
                "SupabaseConfig.PUBLISHABLE_KEY still contains \"$KEY_MARKER\". Paste the " +
                    "publishable/anon key from Project Settings → API Keys."

            PUBLISHABLE_KEY.isBlank() ->
                "SupabaseConfig.PUBLISHABLE_KEY is empty."

            else -> null
        }
}
