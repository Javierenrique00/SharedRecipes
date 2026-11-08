package com.co.recipes

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.co.recipes.infraestructure.security.SecureStoreInitializer
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

/**
 * Hosts the shared Compose UI inside an Android Activity.
 *
 * Kept here (rather than in :androidApp) so the app module needs no Compose
 * dependencies of its own.
 *
 * Also hands the application context to the encrypted session store before the UI
 * starts; `createSecureStore()` fails fast with a clear message if this is skipped.
 */
fun ComponentActivity.startSharedRecipes() {
    SecureStoreInitializer.init(applicationContext)

    // FileKit's *suspend* dialog API (openFilePicker / openFileSaver, used by the backup
    // and export flows, which live in the view models) needs the Activity's
    // ActivityResultRegistry before it can show anything. Must run in onCreate, before the
    // activity is started. Compose launchers would register themselves, but we do not use
    // them: the dialogs are opened from coroutines, not from the composition.
    FileKit.init(this)

    setContent {
        App()
    }
}
