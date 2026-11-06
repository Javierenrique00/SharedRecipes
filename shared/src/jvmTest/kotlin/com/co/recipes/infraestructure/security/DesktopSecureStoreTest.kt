package com.co.recipes.infraestructure.security

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the desktop store against a throwaway `Preferences` node, including the
 * chunking that works around the 8192-char limit `java.util.prefs` imposes per value
 * (a Supabase session with an embedded user object can exceed it).
 */
class DesktopSecureStoreTest {

    private val node = Preferences.userRoot().node("com/co/recipes/test-${UUID.randomUUID()}")
    private val store = DesktopSecureStore(node)

    @AfterTest
    fun cleanUp() {
        node.removeNode()
    }

    @Test
    fun roundTripsSmallValues() {
        store.put("session", """{"access_token":"abc"}""")

        assertEquals("""{"access_token":"abc"}""", store.get("session"))
        assertNull(store.get("missing"))
    }

    @Test
    fun roundTripsValuesLargerThanThePreferencesLimit() {
        val huge = "x".repeat(20_000)

        store.put("session", huge)

        assertEquals(huge.length, store.get("session")?.length)
        assertEquals(huge, store.get("session"))
    }

    @Test
    fun removeDropsEveryChunk() {
        store.put("session", "y".repeat(9_000))

        store.remove("session")

        assertNull(store.get("session"))
        assertNull(node.get("session.chunks", null))
        assertNull(node.get("session.0", null))
    }

    @Test
    fun overwritingReplacesThePreviousChunkCount() {
        store.put("session", "z".repeat(9_000))
        store.put("session", "short")

        assertEquals("short", store.get("session"))
        assertNull(node.get("session.5", null))
    }
}
