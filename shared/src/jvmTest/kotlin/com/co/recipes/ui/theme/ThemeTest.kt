package com.co.recipes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Guards the light/dark selection in [SharedRecipesTheme].
 *
 * The system detection itself (`isSystemInDarkTheme()`) is platform code that cannot be faked
 * from a unit test, so the theme takes the resolved flag as a parameter: this pins down that the
 * flag really swaps the whole [ColorScheme] for the matching Material3 baseline, which is what
 * every screen renders from (none of them hardcodes a color).
 *
 * `ImageComposeScene` composes offscreen — no window is opened — and is the desktop counterpart
 * of a UI test, so this runs in the ordinary `:shared:jvmTest` task.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ThemeTest {

    private fun colorSchemeFor(darkTheme: Boolean): ColorScheme {
        var captured: ColorScheme? = null
        val scene = ImageComposeScene(width = 32, height = 32, density = Density(1f)) {
            SharedRecipesTheme(darkTheme = darkTheme) {
                captured = MaterialTheme.colorScheme
            }
        }
        try {
            scene.render()
        } finally {
            scene.close()
        }
        return requireNotNull(captured) { "SharedRecipesTheme did not compose" }
    }

    @Test
    fun usesTheLightBaselineWhenTheSystemIsLight() {
        val scheme = colorSchemeFor(darkTheme = false)

        assertEquals(lightColorScheme().background, scheme.background)
        assertEquals(lightColorScheme().onBackground, scheme.onBackground)
    }

    @Test
    fun usesTheDarkBaselineWhenTheSystemIsDark() {
        val scheme = colorSchemeFor(darkTheme = true)

        assertEquals(darkColorScheme().background, scheme.background)
        assertEquals(darkColorScheme().onBackground, scheme.onBackground)
    }

    @Test
    fun theTwoAppearancesAreActuallyDifferent() {
        val light = colorSchemeFor(darkTheme = false)
        val dark = colorSchemeFor(darkTheme = true)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.onSurface, dark.onSurface)
    }
}
