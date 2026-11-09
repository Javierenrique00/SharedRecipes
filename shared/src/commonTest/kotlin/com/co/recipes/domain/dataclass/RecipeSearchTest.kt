package com.co.recipes.domain.dataclass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The list search runs in common code over the recipes the list already loaded, so a
 * partial word has to find its recipe on every target. These tests pin the rule; the
 * wiring (query state → rendered cards) is covered by RecipeListViewModelTest.
 */
class RecipeSearchTest {

    private fun recipe(
        id: String = "1",
        title: String = "Chicken curry",
        description: String? = "Coconut milk and red curry paste.",
        ingredients: List<Ingredient> = listOf(
            Ingredient(quantity = 500.0, unit = "g", name = "chicken thighs"),
            Ingredient(quantity = 400.0, unit = "ml", name = "coconut milk"),
        ),
    ) = Recipe(
        id = id,
        title = title,
        description = description,
        youtubeId = null,
        servings = 2,
        prepMinutes = 10,
        cookMinutes = 25,
        ingredients = ingredients,
        steps = listOf(Step(number = 1, instruction = "Brown the chicken.")),
        comments = emptyList(),
    )

    @Test
    fun aPartOfAWordIsEnough() {
        val recipe = recipe()

        assertTrue(recipe.matchesSearch("chic"), "a word prefix must match")
        assertTrue(recipe.matchesSearch("ken"), "a word suffix must match")
        assertTrue(recipe.matchesSearch("urry"), "a piece out of the middle must match")
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(recipe().matchesSearch("CURRY"))
        assertTrue(recipe().matchesSearch("ChIcKeN"))
    }

    @Test
    fun theDescriptionAndTheIngredientsAreSearched() {
        val recipe = recipe()

        assertTrue(recipe.matchesSearch("paste"), "the description counts")
        assertTrue(recipe.matchesSearch("thighs"), "ingredient names count")
    }

    @Test
    fun stepsAreNotSearched() {
        // Steps are detail-only columns: the summary the list loads never has them, so
        // matching here would be a promise the recipe card cannot keep.
        assertFalse(recipe().matchesSearch("brown"))
    }

    /** A second recipe that shares none of the first one's words. */
    private fun rendang() = recipe(
        id = "2",
        title = "Beef rendang",
        description = "Slow-cooked beef in coconut and lemongrass.",
        ingredients = listOf(Ingredient(quantity = 800.0, unit = "g", name = "beef shin")),
    )

    @Test
    fun everyTermHasToMatch() {
        val recipes = listOf(recipe(), rendang())

        assertEquals(listOf("1"), recipes.filterBySearch("chic curry").map { it.id })
        assertTrue(
            recipes.filterBySearch("chic beef").isEmpty(),
            "no single recipe has both words, so the AND must empty the list",
        )
    }

    @Test
    fun extraWhitespaceBetweenTermsDoesNotBreakTheAnd() {
        val recipes = listOf(recipe(), rendang())

        assertEquals(listOf("1"), recipes.filterBySearch("  chic \t curry  ").map { it.id })
    }

    @Test
    fun aBlankQueryKeepsEverything() {
        val recipes = listOf(recipe(), rendang())

        assertEquals(listOf("1", "2"), recipes.filterBySearch("").map { it.id })
        assertEquals(listOf("1", "2"), recipes.filterBySearch("   ").map { it.id })
    }

    @Test
    fun aQueryThatMatchesNothingEmptiesTheList() {
        assertTrue(listOf(recipe()).filterBySearch("zzz").isEmpty())
    }
}
