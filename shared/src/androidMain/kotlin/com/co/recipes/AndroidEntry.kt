package com.co.recipes

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.co.recipes.infraestructure.security.SecureStoreInitializer

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
    setContent {
        App()
    }
}
