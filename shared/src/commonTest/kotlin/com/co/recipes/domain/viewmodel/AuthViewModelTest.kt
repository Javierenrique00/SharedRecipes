package com.co.recipes.domain.viewmodel

import com.co.recipes.infraestructure.api.AuthApi
import com.co.recipes.infraestructure.api.AuthStatus
import com.co.recipes.infraestructure.dataclass.ProfileDto
import com.co.recipes.infraestructure.preferences.UserPreferences
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAuthApi(initialStatus: AuthStatus = AuthStatus.SignedOut) : AuthApi {

    private val statusFlow = MutableStateFlow(initialStatus)
    private var signedIn = initialStatus == AuthStatus.SignedIn

    var signOutCalls = 0
        private set

    override val status: Flow<AuthStatus> = statusFlow

    override val isSignedIn: Boolean
        get() = signedIn

    override fun currentUserId(): String? = if (signedIn) USER_ID else null

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signedIn = true
        statusFlow.value = AuthStatus.SignedIn
        return Result.success(Unit)
    }

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> =
        signIn(email, password)

    override suspend fun signOut(): Result<Unit> {
        signOutCalls++
        signedIn = false
        statusFlow.value = AuthStatus.SignedOut
        return Result.success(Unit)
    }

    override suspend fun currentProfile(): Result<ProfileDto> = Result.success(
        ProfileDto(id = USER_ID, email = "cook@example.com", displayName = "Cook", role = "client"),
    )

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
    }
}

class AuthViewModelTest {

    @Test
    fun restoresAStoredSessionWithoutAskingForThePasswordAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AuthViewModel(FakeAuthApi(AuthStatus.SignedIn), UserPreferences(MapSettings()))

            advanceUntilIdle()

            assertFalse(viewModel.isRestoringSession)
            assertEquals("Cook", viewModel.session?.displayName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun waitsWhileTheSessionIsStillBeingRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AuthViewModel(FakeAuthApi(AuthStatus.Initializing), UserPreferences(MapSettings()))

            assertTrue(viewModel.isRestoringSession)
            assertFalse(viewModel.canSubmit, "login form must not be actionable yet")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun remembersTheEmailWhenTheCheckboxIsOn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = UserPreferences(MapSettings())
            val viewModel = AuthViewModel(FakeAuthApi(), preferences)
            advanceUntilIdle() // let the initial "signed out" status land first

            viewModel.email = "cook@example.com"
            viewModel.password = "sup3r-secret"
            viewModel.rememberMe = true
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("cook@example.com", preferences.lastEmail)
            assertTrue(preferences.rememberMe)
            assertNull(viewModel.errorMessage)
            // The password is cleared from memory once the session exists.
            assertEquals("", viewModel.password)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun forgetsTheEmailWhenTheCheckboxIsOff() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = MapSettings().apply { putString("auth.last_email", "old@example.com") }
            val preferences = UserPreferences(settings)
            val viewModel = AuthViewModel(FakeAuthApi(), preferences)
            advanceUntilIdle() // let the initial "signed out" status land first

            viewModel.rememberMe = false
            viewModel.password = "sup3r-secret"
            viewModel.submit()
            advanceUntilIdle()

            assertNull(preferences.lastEmail)
            assertNull(settings.getStringOrNull("auth.last_email"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun preFillsTheRememberedEmail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = MapSettings().apply {
                putBoolean("auth.remember_me", true)
                putString("auth.last_email", "cook@example.com")
            }

            val viewModel = AuthViewModel(FakeAuthApi(), UserPreferences(settings))

            assertEquals("cook@example.com", viewModel.email)
            assertTrue(viewModel.rememberMe)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun signOutKeepsTheEmailOnlyWhenRemembered() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = UserPreferences(MapSettings())
            val api = FakeAuthApi(AuthStatus.SignedIn)
            val viewModel = AuthViewModel(api, preferences)
            viewModel.rememberMe = true
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(1, api.signOutCalls)
            assertNull(viewModel.session)
            assertEquals("", viewModel.password)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
