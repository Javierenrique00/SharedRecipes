package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.co.recipes.domain.viewmodel.AuthViewModel
import com.co.recipes.ui.common.screenContentPadding

/**
 * Shown instead of the recipe list while a recovery link is open and the new password has
 * not been saved yet (`AuthViewModel.isResettingPassword`).
 *
 * This screen exists because the link signs the user in for real: without it the app would
 * drop them straight into the recipes with the old password still in place.
 */
@Composable
fun ResetPasswordScreen(authViewModel: AuthViewModel) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenContentPadding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Choose a new password",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You opened a recovery link" +
                    (authViewModel.email.takeIf { it.isNotBlank() }
                        ?.let { " for $it" } ?: "") +
                    ". Pick a password of at least 6 characters and you'll be signed in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = authViewModel.newPassword,
                onValueChange = { authViewModel.newPassword = it },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = authViewModel.confirmPassword,
                onValueChange = { authViewModel.confirmPassword = it },
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (authViewModel.passwordsMismatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The two passwords do not match.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { authViewModel.completePasswordReset() },
                enabled = authViewModel.canSavePassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (authViewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save password")
                }
            }

            Spacer(Modifier.height(4.dp))

            // Signing out matters: the recovery session is a real session, so leaving it
            // open would keep the account reachable with a password the user may not know.
            TextButton(onClick = { authViewModel.cancelPasswordReset() }) {
                Text("Not now, sign out")
            }

            authViewModel.errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
