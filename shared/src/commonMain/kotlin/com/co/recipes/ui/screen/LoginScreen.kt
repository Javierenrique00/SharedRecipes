package com.co.recipes.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.co.recipes.domain.viewmodel.AuthViewModel
import com.co.recipes.resources.Res
import com.co.recipes.resources.sheff_welcoming
import com.co.recipes.ui.common.screenContentPadding
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(authViewModel: AuthViewModel) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Sits *outside* the `verticalScroll` of the form below on purpose: `imePadding()`
                // (inside this helper) must shrink the scrollport so the fields can be scrolled
                // above the keyboard, instead of scrolling with the content.
                .screenContentPadding(padding),
        ) {
            // The welcome illustration owns the first quarter of the screen. The two weights add
            // up to the whole height, so 1:3 is exactly 25 % / 75 % with nothing measured, and the
            // split keeps holding when the window is resized, the phone rotates, or the keyboard
            // shrinks the column (`imePadding()` above) — the drawing just gets smaller.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.sheff_welcoming),
                    // Decorative on purpose: the headline right below already names the app, so a
                    // description would only make a screen reader announce it twice.
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Shared Recipes",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (authViewModel.isRegisterMode) {
                        "Create an account to read and comment on recipes."
                    } else {
                        "Sign in to browse the recipe collection."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                if (authViewModel.isRegisterMode) {
                    OutlinedTextField(
                        value = authViewModel.displayName,
                        onValueChange = { authViewModel.displayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = authViewModel.email,
                    onValueChange = { authViewModel.email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = authViewModel.password,
                    onValueChange = { authViewModel.password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = authViewModel.rememberMe,
                        onCheckedChange = { authViewModel.rememberMe = it },
                    )
                    Text(
                        text = "Remember my email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { authViewModel.submit() },
                    enabled = authViewModel.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (authViewModel.isRegisterMode) "Create account" else "Sign in")
                    }
                }

                Spacer(Modifier.height(4.dp))

                TextButton(onClick = { authViewModel.toggleMode() }) {
                    Text(
                        if (authViewModel.isRegisterMode) {
                            "I already have an account"
                        } else {
                            "Create an account"
                        }
                    )
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
}
