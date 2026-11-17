package com.co.recipes.infraestructure.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop contract: no URL scheme exists on the JVM, so the app asks the user to paste
 * the link instead. If this ever changes (e.g. a loopback server is added), the login screen
 * has to change with it.
 */
class PasswordResetRedirectJvmTest {

    @Test
    fun desktopAsksForTheLinkBecauseItCannotReceiveIt() {
        assertTrue(requiresRecoveryLinkPaste)
        assertEquals(DESKTOP_RECOVERY_REDIRECT_URL, passwordResetRedirectUrl())
    }
}
