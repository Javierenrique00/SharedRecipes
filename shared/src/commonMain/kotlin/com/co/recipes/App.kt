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
import com.co.recipes.domain.viewmodel.AuthViewModel
import com.co.recipes.infraestructure.SupabaseConfig
import com.co.recipes.infraestructure.api.AuthApiImpl
import com.co.recipes.infraestructure.api.RecipesApiImpl
import com.co.recipes.infraestructure.createSupabaseClientOrNull
import com.co.recipes.infraestructure.preferences.UserPreferences
import com.co.recipes.ui.navigation.RecipesNavigation
import com.co.recipes.ui.screen.LoginScreen

@Composable
fun App() {
    // The Supabase client owns the auth session, so it must survive recomposition.
    val supabase = remember { createSupabaseClientOrNull() }
    // Non-secret "remember my email" preferences; the session itself is encrypted
    // and owned by Supabase's session manager.
    val preferences = remember { UserPreferences() }

    MaterialTheme {
        if (supabase == null) {
            ConfigurationMissingScreen(SupabaseConfig.configurationError)
        } else {
            val authApi = remember { AuthApiImpl(supabase) }
            val recipesApi = remember { RecipesApiImpl(supabase) }
            val authViewModel = viewModel { AuthViewModel(authApi, preferences) }

            val session = authViewModel.session
            when {
                session != null -> RecipesNavigation(
                    recipesApi = recipesApi,
                    session = session,
                    onSignOut = authViewModel::signOut,
                )

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

