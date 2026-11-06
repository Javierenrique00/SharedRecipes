package com.co.recipes.infraestructure.security

import com.russhwolf.settings.MapSettings
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory stand-in for the platform secret store (Keystore / Keychain / prefs). */
private class FakeSecureStore : SecureStore {
    val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private fun testSession(accessToken: String = "access-token") = UserSession(
    accessToken = accessToken,
    refreshToken = "refresh-token",
    expiresIn = 3600,
    tokenType = "bearer",
)

class SecureSessionManagerTest {

    @Test
    fun savesAndRestoresTheSession() = runTest {
        val store = FakeSecureStore()
        val manager = SecureSessionManager(store, key = TEST_KEY)

        manager.saveSession(testSession())

        assertEquals("access-token", manager.loadSession().accessToken)
        assertEquals("refresh-token", manager.loadSession().refreshToken)
        assertTrue(store.values.keys.single() == TEST_KEY)
    }

    @Test
    fun keepsTheFieldsSupabaseNeedsForRefreshing() = runTest {
        val store = FakeSecureStore()
        val manager = SecureSessionManager(store, key = TEST_KEY)

        manager.saveSession(testSession())

        // encodeDefaults = true must be preserved, otherwise `expiresAt` is dropped and
        // the restored session can never be refreshed.
        val stored = store.values.getValue(TEST_KEY)
        assertTrue(stored.contains("expires_in"), "stored payload was: $stored")
        assertTrue(stored.contains("expiresAt"), "stored payload was: $stored")
    }

    @Test
    fun loadWithoutStoredSessionThrows() = runTest {
        val manager = SecureSessionManager(FakeSecureStore(), key = TEST_KEY)

        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
    }

    @Test
    fun unreadablePayloadIsDroppedSoTheUserCanSignInAgain() = runTest {
        val store = FakeSecureStore().apply { values[TEST_KEY] = "{not json" }
        val manager = SecureSessionManager(store, key = TEST_KEY)

        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
        assertNull(store.values[TEST_KEY], "corrupted payload should be removed")
    }

    @Test
    fun deleteSessionClearsStorage() = runTest {
        val store = FakeSecureStore()
        val manager = SecureSessionManager(store, key = TEST_KEY)
        manager.saveSession(testSession())

        manager.deleteSession()

        assertNull(store.values[TEST_KEY])
        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
    }

    private companion object {
        const val TEST_KEY = "sb-test-session"
    }
}

class LegacySessionMigrationTest {

    @Test
    fun movesThePlaintextSessionIntoTheSecureStore() {
        val legacy = MapSettings().apply { putString(LEGACY_SESSION_KEY, "session-json") }
        val store = FakeSecureStore()

        migrateLegacySession(legacy, store, newKey = NEW_KEY, markerKey = MARKER_KEY)

        assertEquals("session-json", store.get(NEW_KEY))
        assertNull(legacy.getStringOrNull(LEGACY_SESSION_KEY), "plaintext copy must be deleted")
        assertEquals("true", store.get(MARKER_KEY))
    }

    @Test
    fun doesNotOverwriteANewerSecureSession() {
        val legacy = MapSettings().apply { putString(LEGACY_SESSION_KEY, "old-session") }
        val store = FakeSecureStore().apply { put(NEW_KEY, "new-session") }

        migrateLegacySession(legacy, store, newKey = NEW_KEY, markerKey = MARKER_KEY)

        assertEquals("new-session", store.get(NEW_KEY))
        assertNull(legacy.getStringOrNull(LEGACY_SESSION_KEY))
    }

    @Test
    fun runsOnlyOnceSoSignOutIsNotUndoneOnTheNextLaunch() {
        val legacy = MapSettings().apply { putString(LEGACY_SESSION_KEY, "session-json") }
        val store = FakeSecureStore()
        migrateLegacySession(legacy, store, newKey = NEW_KEY, markerKey = MARKER_KEY)

        // User signs out, then a stale plaintext entry reappears (e.g. restored backup).
        store.remove(NEW_KEY)
        legacy.putString(LEGACY_SESSION_KEY, "session-json")
        migrateLegacySession(legacy, store, newKey = NEW_KEY, markerKey = MARKER_KEY)

        assertNull(store.get(NEW_KEY), "migration must not resurrect a signed-out session")
    }

    private companion object {
        const val NEW_KEY = "sb-test-session"
        const val MARKER_KEY = "sb-test-session-migrated"
    }
}
