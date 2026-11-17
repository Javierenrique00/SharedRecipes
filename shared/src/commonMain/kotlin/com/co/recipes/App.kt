package com.co.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.co.recipes.domain.viewmodel.AuthViewModel
import com.co.recipes.infraestructure.SupabaseConfig
import com.co.recipes.infraestructure.api.AuthApiImpl
import com.co.recipes.infraestructure.api.RecipesApiImpl
import com.co.recipes.infraestructure.createSupabaseClientOrNull
import com.co.recipes.infraestructure.preferences.UserPreferences
import com.co.recipes.infraestructure.storage.SupabaseRecipeImageStore
import com.co.recipes.ui.navigation.RecipesNavigation
import com.co.recipes.ui.screen.LoginScreen
import com.co.recipes.ui.screen.ResetPasswordScreen
import com.co.recipes.ui.theme.SharedRecipesTheme
import io.github.jan.supabase.SupabaseClient

@Composable
fun App(
    /**
     * Client prepared by the platform entry point when it has to inspect the launching
     * intent first: Android and iOS forward recovery links to
     * `SupabaseClient.handleDeeplinks`. Every other target leaves this null and the client
     * is created here.
     *
     * The client owns the auth session, so it must survive recomposition.
     */
    supabase: SupabaseClient? = null,
    /**
     * Web-only hook, invoked once the signed-in navigation graph is attached, so the browser
     * target can bind it to window history. Android, iOS and desktop use the no-op default.
     */
    onNavHostReady: suspend (NavHostController) -> Unit = {},
) {
    // `remember` is called unconditionally: the composition's call order must not depend on
    // whether a client was passed in.
    val createdHere = remember { if (supabase == null) createSupabaseClientOrNull() else null }
    val client = supabase ?: createdHere
    // Non-secret "remember my email" preferences; the session itself is encrypted
    // and owned by Supabase's session manager.
    val preferences = remember { UserPreferences() }

    // Light or dark comes from the OS on every target (see SharedRecipesTheme).
    SharedRecipesTheme {
        if (client == null) {
            ConfigurationMissingScreen(SupabaseConfig.configurationError)
        } else {
            val authApi = remember { AuthApiImpl(client) }
            val recipesApi = remember { RecipesApiImpl(client) }
            val imageStore = remember { SupabaseRecipeImageStore(client) }
            val authViewModel = viewModel { AuthViewModel(authApi, preferences) }

            val session = authViewModel.session
            when {
                // A recovery link signs the user in before the new password has been chosen,
                // so this has to win over the recipe list below.
                authViewModel.isResettingPassword -> ResetPasswordScreen(authViewModel)

                session != null -> {
                    // Created inside the signed-in branch on purpose: signing out disposes the
                    // controller, so the next session starts from RecipeList with a clean stack
                    // instead of restoring the previous user's screens.
                    val navController = rememberNavController()
                    RecipesNavigation(
                        navController = navController,
                        recipesApi = recipesApi,
                        imageStore = imageStore,
                        session = session,
                        onSignOut = authViewModel::signOut,
                        onNavHostReady = onNavHostReady,
                    )
                }

                // Reading the encrypted session is async: wait instead of flashing the
                // login form for a user who is already signed in.
                authViewModel.isRestoringSession -> SessionRestoringScreen()

                else -> LoginScreen(authViewModel)
            }
        }
    }
}

@Composable
private fun SessionRestoringScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Restoring your session…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigurationMissingScreen(reason: String?) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Supabase is not configured",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Set SupabaseConfig.URL and SupabaseConfig.PUBLISHABLE_KEY in " +
                    "shared/src/commonMain/kotlin/com/co/recipes/infraestructure/" +
                    "SupabaseConfig.kt, then rebuild.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reason != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

