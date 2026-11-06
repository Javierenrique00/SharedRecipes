package com.co.recipes.domain.viewmodel

import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.RecipeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Only the constructor is needed here; the editor view model does no I/O on creation. */
private class NoopRecipesApi : RecipesApi {
    override suspend fun listRecipes(): Result<List<RecipeDto>> = Result.failure(NotImplementedError())
    override suspend fun getRecipe(id: String): Result<RecipeDto> = Result.failure(NotImplementedError())
    override suspend fun createRecipe(params: CreateRecipeParams): Result<String> =
        Result.failure(NotImplementedError())

    override suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun deleteRecipe(id: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun addComment(recipeId: String, authorId: String, body: String): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun deleteComment(commentId: String): Result<Unit> =
        Result.failure(NotImplementedError())
}

/**
 * Regression guard for `IllegalArgumentException: Key "0" was already used`.
 *
 * The editor renders ingredients and steps in one `LazyColumn`, and both lists start at
 * key 0, so Compose sees two items with the same key on a brand-new recipe unless the
 * keys are namespaced per row type.
 */
class RecipeEditorRowKeysTest {

    @Test
    fun ingredientAndStepRowsWithTheSameOrdinalHaveDifferentLazyKeys() {
        assertNotEquals(
            RecipeEditorViewModel.IngredientRow(key = 0).lazyKey,
            RecipeEditorViewModel.StepRow(key = 0).lazyKey,
        )
    }

    @Test
    fun addedRowsStayUniqueAcrossBothLists() {
        val viewModel = RecipeEditorViewModel(NoopRecipesApi())

        repeat(3) { viewModel.addIngredientRow() }
        repeat(3) { viewModel.addStepRow() }

        assertUniqueKeys(viewModel)
        assertEquals(4, viewModel.ingredients.size) // the seeded blank row + 3
        assertEquals(4, viewModel.steps.size)
    }

    @Test
    fun keysStayUniqueAfterRemovingRows() {
        val viewModel = RecipeEditorViewModel(NoopRecipesApi())
        repeat(2) { viewModel.addIngredientRow() }
        repeat(2) { viewModel.addStepRow() }

        viewModel.removeIngredientRow(viewModel.ingredients.first().key)
        viewModel.removeStepRow(viewModel.steps.first().key)

        assertUniqueKeys(viewModel)
        assertEquals(2, viewModel.ingredients.size)
        assertEquals(2, viewModel.steps.size)
    }

    private fun assertUniqueKeys(viewModel: RecipeEditorViewModel) {
        val keys = viewModel.ingredients.map { it.lazyKey } + viewModel.steps.map { it.lazyKey }
        assertEquals(keys.size, keys.toSet().size, "duplicate LazyColumn keys: $keys")
    }
}
