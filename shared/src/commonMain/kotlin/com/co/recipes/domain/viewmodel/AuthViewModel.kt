package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.domain.dataclass.UserSession
import com.co.recipes.domain.dataclass.toSession
import com.co.recipes.infraestructure.api.AuthApi
import com.co.recipes.infraestructure.api.AuthStatus
import com.co.recipes.infraestructure.api.readableMessage
import com.co.recipes.infraestructure.preferences.UserPreferences
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authApi: AuthApi,
    private val preferences: UserPreferences,
) : ViewModel() {

    var email by mutableStateOf(preferences.lastEmail.orEmpty())
    var password by mutableStateOf("")
    var displayName by mutableStateOf(preferences.lastDisplayName.orEmpty())

    /**
     * Mirrors the "Remember my email" checkbox. Only the email/display name are
     * persisted — never the password (see [UserPreferences]).
     */
    var rememberMe by mutableStateOf(preferences.rememberMe)

    var isRegisterMode by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** True while the encrypted session is being read on a cold start. */
    var isRestoringSession by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Non-null means there is an authenticated user with a resolved role. */
    var session by mutableStateOf<UserSession?>(null)
        private set

    val canSubmit: Boolean
        get() = email.contains('@') && password.length >= 6 && !isLoading && !isRestoringSession

    init {
        observeAuthStatus()
    }

    /**
     * Reading the stored (encrypted) session is asynchronous, so the status stream is
     * observed rather than checked once: a one-shot check is exactly what made the app
     * show the login screen while a valid session was still being restored.
     */
    private fun observeAuthStatus() {
        viewModelScope.launch {
            authApi.status.collect { status ->
                when (status) {
                    AuthStatus.Initializing -> isRestoringSession = true

                    AuthStatus.SignedIn -> {
                        isRestoringSession = false
                        if (session == null) loadProfile()
                    }

                    AuthStatus.SignedOut -> {
                        isRestoringSession = false
                        session = null
                    }
                }
            }
        }
    }

    fun toggleMode() {
        isRegisterMode = !isRegisterMode
        errorMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun submit() {
        if (!canSubmit) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            val result = if (isRegisterMode) {
                authApi.signUp(email.trim(), password, displayName.trim())
            } else {
                authApi.signIn(email.trim(), password)
            }

            result
                .onSuccess {
                    if (!authApi.isSignedIn) {
                        // Project still has "Confirm email" enabled, so no session yet.
                        errorMessage =
                            "Account created. Confirm your email address, then sign in."
                        isRegisterMode = false
                    } else {
                        rememberProfile()
                        if (session == null) loadProfile()
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }

            isLoading = false
        }
    }

    /**
     * Persists the non-secret part of the profile only. The password is never written
     * anywhere: the encrypted Supabase session is what keeps the user signed in.
     */
    private fun rememberProfile() {
        preferences.rememberMe = rememberMe
        if (rememberMe) {
            preferences.lastEmail = email
            preferences.lastDisplayName = displayName
        }
    }

    private suspend fun loadProfile() {
        authApi.currentProfile()
            .onSuccess {
                session = it.toSession()
                password = ""
            }
            .onFailure { errorMessage = it.readableMessage() }
    }

    fun signOut() {
        viewModelScope.launch {
            authApi.signOut()
            session = null
            password = ""
            isRegisterMode = false
            if (!rememberMe) {
                email = ""
                displayName = ""
            }
        }
    }
}
