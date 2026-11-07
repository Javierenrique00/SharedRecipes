package com.co.recipes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * App theme: follows the OS light/dark setting on every target.
 *
 * `isSystemInDarkTheme()` is common-code API in Compose Multiplatform 1.12.2 and is implemented
 * per platform, reacting to live changes rather than only at startup:
 *  - Android -> `Configuration.uiMode` (the Activity lists `uiMode` in `configChanges`, so the
 *    switch costs no Activity restart and the composition simply recomposes);
 *  - iOS -> `UITraitCollection`, re-provided by `ComposeContainer` when the trait changes;
 *  - Desktop -> skiko's system theme, polled every second (macOS/Windows; upstream disables the
 *    polling on Linux);
 *  - Web -> a `prefers-color-scheme` `matchMedia` listener.
 *
 * The palettes are the Material3 baseline ones on purpose: no screen hardcodes a color, they all
 * read `MaterialTheme.colorScheme` roles, so the whole UI follows these two schemes for free.
 * The boot-time backgrounds in `index.html`, `androidApp/src/main/res/values{,-night}`
 * (`#FEF7FF` light, `#141218` dark) mirror the same two values — keep them in sync if the
 * palettes ever change.
 *
 * [darkTheme] stays a parameter, defaulted from the system, so a preview or a test can force one
 * appearance without touching the detection above.
 */
@Composable
fun SharedRecipesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
