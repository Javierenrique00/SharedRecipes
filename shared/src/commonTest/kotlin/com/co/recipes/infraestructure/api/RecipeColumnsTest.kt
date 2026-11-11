package com.co.recipes.infraestructure.api

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The list card renders its counts from the loaded recipe (the facts line in
 * `RecipeListScreen`), but `RecipeDto` decodes a child that was *not* selected into
 * `emptyList()`. A summary projection that forgets to embed the children therefore fails
 * silently — every card reads "0 ingredients" — which is exactly what happened before these
 * tests existed.
 */
class RecipeColumnsTest {

    @Test
    fun theSummaryEmbedsTheChildrenTheCardCounts() {
        assertTrue(
            RecipesApiImpl.SUMMARY_COLUMNS.contains("recipe_ingredients("),
            "the list shows an ingredient count, so it must select them: " +
                RecipesApiImpl.SUMMARY_COLUMNS,
        )
        assertTrue(
            RecipesApiImpl.SUMMARY_COLUMNS.contains("recipe_comments("),
            "the list shows a comment count, so it must select them: " +
                RecipesApiImpl.SUMMARY_COLUMNS,
        )
    }

    @Test
    fun theSummaryLeavesTheStepsOut() {
        // Nothing on the card shows the method, so the summary must not download it:
        // that is the difference between this projection and the detail one.
        assertTrue(
            !RecipesApiImpl.SUMMARY_COLUMNS.contains("recipe_steps("),
            "steps are detail-only: ${RecipesApiImpl.SUMMARY_COLUMNS}",
        )
    }

    @Test
    fun theDetailEmbedsEveryChild() {
        listOf("recipe_ingredients(", "recipe_steps(", "recipe_comments(").forEach { child ->
            assertTrue(
                RecipesApiImpl.DETAIL_COLUMNS.contains(child),
                "the detail screen renders $child: ${RecipesApiImpl.DETAIL_COLUMNS}",
            )
        }
    }

    @Test
    fun theImageColumnsAreSelectedWhereTheyAreRendered() {
        // The card thumbnail and the detail header read recipes.image_path; the step photos
        // read recipe_steps.image_path. A projection that forgets either renders nothing.
        assertTrue(
            RecipesApiImpl.SUMMARY_COLUMNS.contains("image_path"),
            "the list thumbnail needs image_path: ${RecipesApiImpl.SUMMARY_COLUMNS}",
        )
        assertTrue(
            RecipesApiImpl.DETAIL_COLUMNS.contains("recipe_steps(id,step_number,instruction,image_path)"),
            "step photos need image_path: ${RecipesApiImpl.DETAIL_COLUMNS}",
        )
    }

    @Test
    fun bothProjectionsSelectTheColumnsTheMapperReads() {
        // Mappers.kt sorts ingredients by position, steps by step_number and comments by
        // created_at, and RecipeDto requires every column below not to be null.
        val required = listOf("id", "title", "description", "youtube_id", "servings")
        listOf(RecipesApiImpl.SUMMARY_COLUMNS, RecipesApiImpl.DETAIL_COLUMNS).forEach { columns ->
            required.forEach { column ->
                assertTrue(columns.contains(column), "$column must be selected: $columns")
            }
        }
    }
}
