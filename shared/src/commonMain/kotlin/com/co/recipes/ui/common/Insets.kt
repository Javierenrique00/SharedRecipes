package com.co.recipes.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

/**
 * Standard modifier for the body of a `Scaffold` screen that can show a text field.
 *
 * Three things happen, in this order, and all three matter:
 *
 *  1. `padding(innerPadding)` — the Scaffold's own padding (top app bar, system bars).
 *  2. `consumeWindowInsets(innerPadding)` — tells the inset machinery those insets are already
 *     applied. Without it the bottom navigation-bar inset that `innerPadding` contains would be
 *     added *again* on top of the keyboard inset, leaving a ~48dp gap above the IME.
 *  3. `imePadding()` — reserves the keyboard's height, so a scrollable child gets a viewport that
 *     ends where the keyboard starts.
 *
 * Point 3 is what removes the "jumping". When the viewport excludes the IME, Compose's
 * `bringIntoView` (which every focused text field triggers) scrolls the focused cell up **once,
 * smoothly**, and the content stops moving on every subsequent keystroke. It also replaces the
 * per-keystroke window panning that `android:windowSoftInputMode` would otherwise do — see the
 * comment on `MainActivity` in androidApp/src/main/AndroidManifest.xml.
 *
 * `imePadding()` is common-code API in Compose Multiplatform: real on Android and iOS, a no-op on
 * desktop and the browser, so this one helper is safe for all four targets.
 */
fun Modifier.screenContentPadding(innerPadding: PaddingValues): Modifier =
    this
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding()
