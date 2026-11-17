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
import com.co.recipes.infraestructure.auth.requiresRecoveryLinkPaste
import com.co.recipes.infraestructure.preferences.UserPreferences
import kotlinx.coroutines.launch

/**
 * Which form the unauthenticated part of the app is showing.
 *
 * Opening a recovery link does not use a mode: it signs the user in, and
 * [AuthViewModel.isResettingPassword] then switches the whole app to the
 * "choose a new password" screen.
 */
enum class AuthMode {
    SignIn,
    SignUp,
    ForgotPassword,
}

class AuthViewModel(
    private val authApi: AuthApi,
    private val preferences: UserPreferences,
) : ViewModel() {

    var mode by mutableStateOf(AuthMode.SignIn)
        private set

    var email by mutableStateOf(preferences.lastEmail.orEmpty())
    var password by mutableStateOf("")
    var displayName by mutableStateOf(preferences.lastDisplayName.orEmpty())

    /** New password typed on the reset screen (see [isResettingPassword]). */
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    /**
     * Recovery link pasted on desktop, where the OS cannot hand the link to the app.
     * Unused on Android, iOS and the browser, which receive it themselves.
     */
    var recoveryLink by mutableStateOf("")

    /**
     * Mirrors the "Remember my email" checkbox. Only the email/display name are
     * persisted — never the password (see [UserPreferences]).
     */
    var rememberMe by mutableStateOf(preferences.rememberMe)

    var isLoading by mutableStateOf(false)
        private set

    /** True while the encrypted session is being read on a cold start. */
    var isRestoringSession by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Non-error feedback, such as "we sent you a link". */
    var infoMessage by mutableStateOf<String?>(null)
        private set

    /** Non-null means there is an authenticated user with a resolved role. */
    var session by mutableStateOf<UserSession?>(null)
        private set

    /** True as soon as a session exists, even before its profile has been loaded. */
    var hasSession by mutableStateOf(false)
        private set

    /** A reset was requested and has been neither completed nor abandoned. */
    var isResetPending by mutableStateOf(preferences.pendingPasswordReset() != null)
        private set

    val isRegisterMode: Boolean get() = mode == AuthMode.SignUp

    val isForgotPasswordMode: Boolean get() = mode == AuthMode.ForgotPassword

    /**
     * True once the recovery link has been opened but the new password has not been saved.
     *
     * `App` renders the reset screen instead of the recipe list while this holds: the link
     * imports a real session, so `AuthStatus` already reports SignedIn at this point.
     *
     * Deliberately based on [hasSession] rather than [session]: the reset form must not
     * depend on the profile row loading, because a user whose profile is missing still has
     * to be able to set a password.
     */
    val isResettingPassword: Boolean get() = hasSession && isResetPending

    /** Desktop: the user has to copy the link from the browser into the app. */
    val needsRecoveryLinkPaste: Boolean get() = requiresRecoveryLinkPaste

    val passwordsMismatch: Boolean
        get() = confirmPassword.isNotEmpty() && newPassword != confirmPassword

    val canSubmit: Boolean
        get() = when (mode) {
            AuthMode.ForgotPassword ->
                email.contains('@') && !isLoading && !isRestoringSession

            AuthMode.SignIn, AuthMode.SignUp ->
                email.contains('@') && password.length >= 6 && !isLoading && !isRestoringSession
        }

    val canSubmitRecoveryLink: Boolean get() = recoveryLink.isNotBlank() && !isLoading

    val canSavePassword: Boolean
        get() = newPassword.length >= 6 && newPassword == confirmPassword && !isLoading

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
                        hasSession = true
                        // A marker that expired while the app was closed must not hijack the
                        // UI, so re-read it (and its TTL) on every status change.
                        isResetPending = preferences.pendingPasswordReset() != null
                        if (session == null) loadProfile()
                    }

                    AuthStatus.SignedOut -> {
                        isRestoringSession = false
                        hasSession = false
                        session = null
                    }
                }
            }
        }
    }

    /** Switches between the sign-in and sign-up forms. */
    fun toggleMode() {
        mode = if (mode == AuthMode.SignUp) AuthMode.SignIn else AuthMode.SignUp
        errorMessage = null
        infoMessage = null
    }

    /** Opens the "forgot password" form (email only). */
    fun showForgotPassword() {
        mode = AuthMode.ForgotPassword
        password = ""
        errorMessage = null
        infoMessage = null
    }

    /** Returns from the "forgot password" form to the sign-in form. */
    fun showSignIn() {
        mode = AuthMode.SignIn
        errorMessage = null
        infoMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun submit() {
        if (!canSubmit) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null

            if (mode == AuthMode.ForgotPassword) {
                requestReset(email.trim())
            } else {
                signInOrUp()
            }

            isLoading = false
        }
    }

    /**
     * Asks Supabase for the recovery email and records the pending marker.
     *
     * The marker is written before the link can possibly be opened: on Android and iOS the
     * click may start a cold process, and this is what makes that process show the reset
     * screen instead of the recipe list.
     */
    private suspend fun requestReset(address: String) {
        authApi.requestPasswordReset(address)
            .onSuccess {
                preferences.rememberPendingPasswordReset(address)
                isResetPending = true
                // Neutral wording on purpose: Supabase answers the same for unknown emails,
                // so the message must not reveal whether an account exists.
                infoMessage = if (needsRecoveryLinkPaste) {
                    "If that email exists, a reset link is on its way. Open it in your " +
                        "browser, then paste the full address below."
                } else {
                    "If that email exists, a reset link is on its way. Open it on this " +
                        "device to choose a new password."
                }
            }
            .onFailure { errorMessage = it.readableMessage() }
    }

    private suspend fun signInOrUp() {
        // A normal sign-in abandons any half-finished reset, so the app does not jump to the
        // password screen right after a successful sign-in.
        clearPendingReset()

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
                    mode = AuthMode.SignIn
                } else {
                    rememberProfile()
                    if (session == null) loadProfile()
                }
            }
            .onFailure { errorMessage = it.readableMessage() }
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

    /**
     * Desktop only: exchanges the link the user copied out of the browser.
     *
     * On success the Auth plugin imports the recovery session, `AuthStatus` reports
     * SignedIn, and `isResettingPassword` — already true because the marker is pending —
     * sends the app to the "choose a new password" screen.
     */
    fun submitRecoveryLink() {
        if (!canSubmitRecoveryLink) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            authApi.completeRecoveryLink(recoveryLink.trim())
                .onSuccess {
                    recoveryLink = ""
                    infoMessage = null
                }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }

    /**
     * Saves the new password on the recovery session and hands the app back to the recipe
     * list. The session stays signed in on purpose: the link already proved access to the
     * mailbox, and Supabase's own clients behave the same way.
     */
    fun completePasswordReset() {
        if (!canSavePassword) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            authApi.updatePassword(newPassword)
                .onSuccess {
                    clearPendingReset()
                    newPassword = ""
                    confirmPassword = ""
                    recoveryLink = ""
                    infoMessage = null
                    // The profile may have been skipped while the reset screen was showing;
                    // loading it now is what lets `App` render the recipe list.
                    if (session == null) loadProfile()
                }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }

    /** Gives up on the reset: drops the marker and signs the recovery session out. */
    fun cancelPasswordReset() {
        viewModelScope.launch {
            clearPendingReset()
            newPassword = ""
            confirmPassword = ""
            recoveryLink = ""
            infoMessage = null
            authApi.signOut()
            session = null
            hasSession = false
            mode = AuthMode.SignIn
        }
    }

    private fun clearPendingReset() {
        preferences.clearPendingPasswordReset()
        isResetPending = false
    }

    fun signOut() {
        viewModelScope.launch {
            clearPendingReset()
            authApi.signOut()
            session = null
            hasSession = false
            password = ""
            newPassword = ""
            confirmPassword = ""
            recoveryLink = ""
            mode = AuthMode.SignIn
            if (!rememberMe) {
                email = ""
                displayName = ""
            }
        }
    }
}
