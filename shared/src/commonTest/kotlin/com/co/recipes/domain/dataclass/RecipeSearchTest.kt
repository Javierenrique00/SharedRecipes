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

    // -----------------------------------------------------------------------------------
    // Suggestions (the menu under the search field)
    // -----------------------------------------------------------------------------------

    @Test
    fun theVocabularyIsSplitIntoWordsAndDedupedCaseInsensitively() {
        assertEquals(
            // "coconut"/"chicken" appear capitalised in the description and lowercased in the
            // ingredients; only the first spelling survives.
            listOf("Chicken", "curry", "Coconut", "milk", "and", "red", "paste", "thighs"),
            listOf(recipe()).searchVocabulary(),
        )
    }

    @Test
    fun oneLetterWordsAreNotOffered() {
        val vocabulary = listOf(recipe(description = "Add a bit of oil and I mean it."))
            .searchVocabulary()

        assertFalse("a" in vocabulary, "a one-letter suggestion would match half the collection")
        assertFalse("I" in vocabulary)
        assertTrue("oil" in vocabulary)
    }

    @Test
    fun suggestionsCompleteTheWordBeingTyped() {
        assertEquals(listOf("Chicken"), listOf(recipe()).searchSuggestions("chic"))
    }

    @Test
    fun aPartOfAWordIsOfferedToo() {
        assertEquals(listOf("curry"), listOf(recipe()).searchSuggestions("urry"))
    }

    @Test
    fun prefixMatchesComeBeforeTheOnesThatMerelyContainTheTerm() {
        val cocoa = recipe(
            title = "Cocoa cake",
            description = "Desiccated coconut.",
            ingredients = emptyList(),
        )

        assertEquals(listOf("Cocoa", "coconut"), listOf(cocoa).searchSuggestions("co"))
    }

    @Test
    fun theWordAlreadyTypedIsNotOffered() {
        assertTrue(
            listOf(recipe()).searchSuggestions("chicken").isEmpty(),
            "there is nothing left to complete once the word is typed out",
        )
    }

    @Test
    fun suggestionsAreTakenOnlyFromTheRecipesTheEarlierTermsMatched() {
        val recipes = listOf(recipe(), rendang())

        // "beef" narrows the pool to the rendang, which has no word containing "cur", so the
        // chicken recipe's "curry" is never offered — accepting a suggestion cannot empty the
        // list.
        assertTrue(recipes.searchSuggestions("beef cur").isEmpty())
        assertEquals(listOf("curry"), recipes.searchSuggestions("chic cur"))
    }

    @Test
    fun noSuggestionsBetweenWordsOrOnAnEmptyField() {
        assertTrue(listOf(recipe()).searchSuggestions("").isEmpty())
        assertTrue(listOf(recipe()).searchSuggestions("   ").isEmpty())
        assertTrue(
            listOf(recipe()).searchSuggestions("chicken ").isEmpty(),
            "a finished word leaves the next one empty, which closes the menu",
        )
    }

    @Test
    fun theSuggestionListIsCapped() {
        assertEquals(
            listOf("Chicken", "Coconut"),
            listOf(recipe()).searchSuggestions("c", limit = 2),
        )
    }

    @Test
    fun completingAWordReplacesOnlyThatWordAndLeavesASpace() {
        assertEquals("chic curry ", completeSearchQuery("chic cur", "curry"))
        assertEquals("curry ", completeSearchQuery("", "curry"))
        assertEquals("chic curry ", completeSearchQuery("chic ", "curry"))
        assertEquals("  chic   curry ", completeSearchQuery("  chic   cur", "curry"))
    }
}
