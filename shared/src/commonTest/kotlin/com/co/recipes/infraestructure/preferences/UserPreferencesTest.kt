package com.co.recipes.infraestructure.preferences

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPreferencesTest {

    @Test
    fun remembersEmailAndDisplayName() {
        val settings = MapSettings()
        val preferences = UserPreferences(settings)

        preferences.rememberMe = true
        preferences.lastEmail = "  cook@example.com  "
        preferences.lastDisplayName = "Cook"

        assertEquals("cook@example.com", preferences.lastEmail)
        assertEquals("Cook", preferences.lastDisplayName)
        assertTrue(preferences.rememberMe)
    }

    @Test
    fun optingOutForgetsTheProfile() {
        val settings = MapSettings()
        val preferences = UserPreferences(settings)
        preferences.rememberMe = true
        preferences.lastEmail = "cook@example.com"
        preferences.lastDisplayName = "Cook"

        preferences.rememberMe = false

        assertFalse(preferences.rememberMe)
        assertNull(preferences.lastEmail)
        assertNull(preferences.lastDisplayName)
    }

    @Test
    fun blankValuesAreNotPersisted() {
        val settings = MapSettings()
        val preferences = UserPreferences(settings)

        preferences.lastEmail = "   "
        preferences.lastDisplayName = ""

        assertNull(preferences.lastEmail)
        assertNull(preferences.lastDisplayName)
    }

    @Test
    fun neverWritesACredential() {
        val settings = MapSettings()
        val preferences = UserPreferences(settings)

        preferences.rememberMe = true
        preferences.lastEmail = "cook@example.com"
        preferences.lastDisplayName = "Cook"
        preferences.rememberPendingPasswordReset("cook@example.com")

        // The password must never reach local preferences; only the profile does.
        assertTrue(
            settings.keys.none { it.contains("password", ignoreCase = true) },
            "unexpected preference keys: ${settings.keys}",
        )
        assertTrue(settings.keys.all { it.startsWith("auth.") }, "unexpected keys: ${settings.keys}")
    }

    @Test
    fun aPendingResetIsRememberedForAnHour() {
        val preferences = UserPreferences(MapSettings())
        val requestedAt = 1_000_000L

        preferences.rememberPendingPasswordReset(
            email = "  cook@example.com  ",
            requestedAtMillis = requestedAt,
        )

        val pending = preferences.pendingPasswordReset(nowMillis = requestedAt + 1)
        assertEquals("cook@example.com", pending?.email)
        assertEquals(requestedAt, pending?.requestedAtMillis)

        // Supabase expires the recovery email after an hour, so the marker has to expire too
        // or the app would offer a reset form for a link that no longer works.
        assertNull(preferences.pendingPasswordReset(nowMillis = requestedAt + 60 * 60 * 1000 + 1))
    }

    @Test
    fun aClockThatMovedBackwardsExpiresTheMarker() {
        val preferences = UserPreferences(MapSettings())
        preferences.rememberPendingPasswordReset("cook@example.com", requestedAtMillis = 10_000L)

        assertNull(preferences.pendingPasswordReset(nowMillis = 9_000L))
    }

    @Test
    fun clearingThePendingResetForgetsIt() {
        val settings = MapSettings()
        val preferences = UserPreferences(settings)
        preferences.rememberPendingPasswordReset("cook@example.com")

        preferences.clearPendingPasswordReset()

        assertNull(preferences.pendingPasswordReset())
        assertFalse(settings.keys.any { it.startsWith("auth.recovery") }, "keys: ${settings.keys}")
    }

    @Test
    fun aBlankEmailDoesNotLeaveAMarkerBehind() {
        val preferences = UserPreferences(MapSettings())
        preferences.rememberPendingPasswordReset("cook@example.com")

        preferences.rememberPendingPasswordReset("   ")

        assertNull(preferences.pendingPasswordReset())
    }
}
