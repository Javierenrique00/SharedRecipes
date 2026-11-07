package com.co.recipes

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is enforced automatically from targetSdk 35 on, but older devices still
        // fit the window to the system bars. Asking for it explicitly (before super.onCreate,
        // as the AndroidX docs require) gives every API level the same transparent system bars
        // and, more importantly, the same window insets — so `Modifier.imePadding()` and
        // `Scaffold`'s system-bar padding behave identically on API 26 and on Android 15+.
        // Status/navigation bar icon tone still follows the light/dark resource qualifier.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The Compose UI itself lives in :shared so Android needs no Compose setup.
        startSharedRecipes()
    }

    /**
     * `uiMode` is in the manifest's `configChanges`, so switching light/dark does not recreate the
     * Activity (see the theme note in README). Re-applying the system-bar styles here is what keeps
     * the status/navigation bar icon tone in sync with the scheme Compose has just switched to;
     * otherwise the tone stays whatever it was at launch.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}

