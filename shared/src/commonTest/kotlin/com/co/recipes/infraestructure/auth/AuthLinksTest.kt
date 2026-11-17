package com.co.recipes.infraestructure.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthLinksTest {

    @Test
    fun findsThePkceCodeInAMobileDeepLink() {
        assertEquals(
            "5f0c2a11-9d39-4c3e-b0a5-0d5b6f3c9a1e",
            extractAuthCode("sharedrecipes://auth?code=5f0c2a11-9d39-4c3e-b0a5-0d5b6f3c9a1e"),
        )
    }

    @Test
    fun findsTheCodeEvenWhenOtherParametersComeFirst() {
        assertEquals(
            "abc123",
            extractAuthCode("http://127.0.0.1:8090/?state=xyz&code=abc123&next=%2F"),
        )
    }

    @Test
    fun ignoresParametersThatMerelyContainTheWordCode() {
        assertNull(extractAuthCode("http://127.0.0.1:8090/?error_code=400&next=?code=abc"))
    }

    @Test
    fun implicitLinksHaveNoCodeAndFallBackToTheFragment() {
        assertNull(
            extractAuthCode(
                "sharedrecipes://auth#access_token=a&refresh_token=b&expires_in=3600&type=recovery",
            ),
        )
    }

    @Test
    fun aUrlWithoutQueryOrFragmentHasNoCode() {
        assertNull(extractAuthCode("https://javierenrique00.github.io/SharedRecipes/"))
        assertNull(extractAuthCode(""))
    }

    @Test
    fun stripsQueryAndFragmentSoThePageKeepsItsSubPath() {
        assertEquals(
            "https://javierenrique00.github.io/SharedRecipes",
            stripQueryAndFragment(
                "https://javierenrique00.github.io/SharedRecipes/?code=abc#com.co.recipes.ui",
            ),
        )
        assertEquals(
            "http://localhost:8080",
            stripQueryAndFragment("http://localhost:8080/?code=abc"),
        )
    }
}
