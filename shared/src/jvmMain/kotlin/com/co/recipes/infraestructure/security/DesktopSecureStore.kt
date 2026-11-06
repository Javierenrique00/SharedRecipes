package com.co.recipes.infraestructure.security

import java.util.prefs.Preferences

/**
 * Desktop secret store.
 *
 * The JDK ships no OS keychain API, so this target cannot honour the "encrypted at
 * rest" promise that Android and iOS get: the session JSON is written in plaintext to
 * `java.util.prefs` (macOS: `~/Library/Preferences/com.apple.java.util.prefs.plist`).
 * Desktop is only the local development harness, and this is documented in the README.
 *
 * `Preferences` also caps each value at 8192 chars, which a Supabase session (JWT +
 * user JSON) can exceed, so long values are chunked transparently.
 */
internal class DesktopSecureStore(
    private val node: Preferences = Preferences.userRoot().node(NODE_PATH),
) : SecureStore {

    override fun get(key: String): String? {
        val chunks = node.get(chunksKey(key), null)?.toIntOrNull()
            ?: return node.get(key, null) // legacy/short single-value layout

        val parts = (0 until chunks).map { index -> node.get(chunkKey(key, index), null) ?: return null }
        return parts.joinToString(separator = "")
    }

    override fun put(key: String, value: String) {
        remove(key)
        val parts = value.chunked(MAX_CHUNK_LENGTH)
        node.put(chunksKey(key), parts.size.toString())
        parts.forEachIndexed { index, part -> node.put(chunkKey(key, index), part) }
        node.flush()
    }

    override fun remove(key: String) {
        node.get(chunksKey(key), null)?.toIntOrNull()?.let { chunks ->
            repeat(chunks) { index -> node.remove(chunkKey(key, index)) }
        }
        node.remove(chunksKey(key))
        node.remove(key)
        node.flush()
    }

    private fun chunksKey(key: String) = "$key.chunks"

    private fun chunkKey(key: String, index: Int) = "$key.$index"

    private companion object {
        const val NODE_PATH = "com/co/recipes/session"
        const val MAX_CHUNK_LENGTH = 4000
    }
}

internal actual fun createSecureStore(): SecureStore = DesktopSecureStore()
