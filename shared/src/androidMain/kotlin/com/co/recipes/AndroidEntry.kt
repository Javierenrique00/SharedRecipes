package com.co.recipes

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.co.recipes.infraestructure.createSupabaseClientOrNull
import com.co.recipes.infraestructure.security.SecureStoreInitializer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The client for this process, kept outside the composition so `onNewIntent` can reach it.
 *
 * The manifest marks `MainActivity` as `singleTask`, so a recovery link that arrives while
 * the app is open reuses this activity (and this client) instead of starting a second one
 * with its own session manager.
 */
private var sharedClient: SupabaseClient? = null

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

    val client = createSupabaseClientOrNull()
    sharedClient = client

    // A recovery link arrives as this activity's launch intent when the app was closed.
    client?.let { forwardAuthIntent(it, intent) }

    setContent {
        App(supabase = client)
    }
}

/**
 * Handles a recovery link that re-opened the running app: `MainActivity.onNewIntent`
 * calls this (the manifest's `singleTask` launch mode is what produces that callback).
 */
fun ComponentActivity.handleAuthIntent(intent: Intent) {
    sharedClient?.let { forwardAuthIntent(it, intent) }
}

private fun forwardAuthIntent(client: SupabaseClient, intent: Intent) {
    // Wait for Auth to finish reading the stored session first: `autoLoadFromStorage` runs
    // while the client initialises, and importing the link before that would let the saved
    // (old) session overwrite the recovery session.
    CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).launch {
        client.auth.awaitInitialization()
        client.handleDeeplinks(intent)
    }
}
