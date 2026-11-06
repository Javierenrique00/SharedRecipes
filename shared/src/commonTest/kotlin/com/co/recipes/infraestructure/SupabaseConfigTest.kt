package com.co.recipes.infraestructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the app's connection settings.
 *
 * [shippedValuesAreUsable] is the important one: it fails the build if the real project
 * URL / publishable key are ever reverted to placeholders, which would otherwise only
 * show up as the "Supabase is not configured" screen at runtime.
 */
class SupabaseConfigTest {

    @Test
    fun shippedValuesAreUsable() {
        assertNull(
            SupabaseConfig.configurationError,
            "SupabaseConfig still has a placeholder or malformed value.",
        )
        assertTrue(SupabaseConfig.isConfigured)
        assertTrue(SupabaseConfig.PUBLISHABLE_KEY.isNotBlank())
    }

    @Test
    fun urlPointsAtASupabaseProject() {
        assertTrue(
            SupabaseConfig.URL.startsWith("https://"),
            "URL must be https, was \"${SupabaseConfig.URL}\"",
        )
        assertTrue(
            SupabaseConfig.URL.endsWith(".supabase.co"),
            "URL must end with .supabase.co, was \"${SupabaseConfig.URL}\"",
        )
    }

    @Test
    fun urlHasNoTrailingSlashOrWhitespace() {
        assertEquals(SupabaseConfig.URL.trim(), SupabaseConfig.URL)
        assertTrue(!SupabaseConfig.URL.endsWith("/"))
    }

    @Test
    fun projectRefNamespacesOnDeviceStorage() {
        assertEquals(
            "dbwzrjgctmfckdsljznf",
            SupabaseConfig.projectRef,
            "projectRef must be the subdomain of the project URL",
        )
        assertEquals("sb-dbwzrjgctmfckdsljznf-session", SupabaseConfig.sessionStorageKey)
        assertTrue(SupabaseConfig.sessionMigrationKey.endsWith("-session-migrated"))
    }

    @Test
    fun keyIsNotTheServiceRoleKey() {
        // The service_role key is a JWT whose payload contains that role name; the
        // publishable/anon key never does. Shipping it would bypass RLS entirely.
        assertTrue(
            !SupabaseConfig.PUBLISHABLE_KEY.contains("service_role"),
            "Never ship the service_role key in the app.",
        )
    }
}
