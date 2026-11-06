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

        // The password must never reach local preferences; only the profile does.
        assertTrue(
            settings.keys.none { it.contains("password", ignoreCase = true) },
            "unexpected preference keys: ${settings.keys}",
        )
        assertTrue(settings.keys.all { it.startsWith("auth.") }, "unexpected keys: ${settings.keys}")
    }
}
