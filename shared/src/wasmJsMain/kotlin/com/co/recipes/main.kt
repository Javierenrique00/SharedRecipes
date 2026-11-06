package com.co.recipes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Browser entry point, the counterpart of `jvmMain/main.kt` and
 * `iosMain/MainViewController.kt`.
 *
 * [ComposeViewport] mounts the composition into the `<div id="composeTarget">` provided by
 * `src/wasmJsMain/resources/index.html` (that page gives the container a real height, which
 * the viewport needs because it sizes its canvas from it). Nothing else changes: `App()` is
 * the same composable the native targets render.
 *
 * The opt-in is required by Compose Multiplatform 1.12.x; the API is expected to stabilise
 * in a later release.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeTarget") {
        App()
    }
}
