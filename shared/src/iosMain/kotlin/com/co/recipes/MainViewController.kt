package com.co.recipes

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.co.recipes.infraestructure.createSupabaseClientOrNull
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSURL

/**
 * The client for this process, reachable from Swift through [handleAuthDeepLink].
 */
private var sharedClient: SupabaseClient? = null

/**
 * A recovery link can be delivered before Compose has created the client (SwiftUI is still
 * setting up the view controller), so it is buffered and replayed on attach rather than lost.
 */
private var pendingAuthUrl: NSURL? = null

/** Entry point consumed by iosApp/ContentView.swift. */
fun MainViewController() = ComposeUIViewController {
    val client = remember { createSupabaseClientOrNull() }

    LaunchedEffect(client) {
        sharedClient = client
        val pending = pendingAuthUrl
        pendingAuthUrl = null
        if (pending != null) client?.let { forwardAuthUrl(it, pending) }
    }

    App(supabase = client)
}

/**
 * Called from `iOSApp.swift`'s `.onOpenURL`.
 *
 * The URL scheme is registered in `Info.plist` (`CFBundleURLTypes`), and the same
 * `sharedrecipes://auth` value has to be listed under Authentication → URL Configuration →
 * Redirect URLs in the dashboard.
 */
fun handleAuthDeepLink(url: NSURL) {
    val client = sharedClient
    if (client == null) {
        pendingAuthUrl = url
        return
    }
    forwardAuthUrl(client, url)
}

private fun forwardAuthUrl(client: SupabaseClient, url: NSURL) {
    // Wait for Auth to restore the stored session first, otherwise the storage load could
    // overwrite the recovery session imported from the link.
    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
        client.auth.awaitInitialization()
        client.handleDeeplinks(url)
    }
}
