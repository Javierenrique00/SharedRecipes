package com.co.recipes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation

/**
 * Browser entry point, the counterpart of `jvmMain/main.kt` and
 * `iosMain/MainViewController.kt`.
 *
 * [ComposeViewport] mounts the composition into the `<div id="composeTarget">` provided by
 * `src/wasmJsMain/resources/index.html` (that page gives the container a real height, which
 * the viewport needs because it sizes its canvas from it). Nothing else changes: `App()` is
 * the same composable the native targets render.
 *
 * It also wires the browser into the navigation graph: once `App()` has attached its
 * `NavHost`, the controller is bound to the window history, so Back/Forward move between
 * screens and the current screen shows up as a `#fragment` that can be bookmarked or pasted
 * into another tab. The path itself never changes (`/SharedRecipes/#…`), which is why the
 * GitHub Pages deployment needs no rewrite rules: a refresh always lands on `index.html`.
 *
 * The opt-ins are required by Compose Multiplatform 1.12.x and by navigation 2.10.0; both
 * APIs are expected to stabilise in a later release.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    // `bindToBrowserNavigation()` installs a permanent `popstate` listener and offers no way
    // to remove it, so it must run at most once per page load. Without this guard, signing
    // out and back in (which recreates the NavHost) would stack listeners and handle every
    // Back/Forward twice.
    var browserHistoryBound = false

    ComposeViewport(viewportContainerId = "composeTarget") {
        App(
            onNavHostReady = { navController ->
                if (!browserHistoryBound) {
                    browserHistoryBound = true
                    navController.bindToBrowserNavigation()
                }
            },
        )
    }
}
