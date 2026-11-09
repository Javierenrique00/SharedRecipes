package com.co.recipes.domain.viewmodel

import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records the delete/restore calls; everything else is irrelevant to the list screen. */
private class RecordingRecipesApi(
    private val listed: List<RecipeDto> = emptyList(),
    private val deleteResult: Result<Unit> = Result.success(Unit),
    private val restoreResult: Result<Unit> = Result.success(Unit),
) : RecipesApi {

    val deleted = mutableListOf<String>()
    val restored = mutableListOf<String>()

    override suspend fun listRecipes(): Result<List<RecipeDto>> = Result.success(listed)

    override suspend fun getRecipe(id: String): Result<RecipeDto> =
        Result.failure(NotImplementedError())

    override suspend fun createRecipe(params: CreateRecipeParams): Result<String> =
        Result.failure(NotImplementedError())

    override suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun deleteRecipe(id: String): Result<Unit> {
        deleted += id
        return deleteResult
    }

    override suspend fun restoreRecipe(id: String): Result<Unit> {
        restored += id
        return restoreResult
    }

    override suspend fun addComment(recipeId: String, authorId: String, body: String): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun deleteComment(commentId: String): Result<Unit> =
        Result.failure(NotImplementedError())

    // The list screen never touches the admin API; these exist because the interface grew
    // for the admin console (migration 9) and this fake has to keep compiling.
    override suspend fun exportRecipes(): Result<List<RecipeBackupDto>> =
        Result.failure(NotImplementedError())

    override suspend fun importRecipes(
        recipes: List<RecipeBackupDto>,
    ): Result<ImportSummary> = Result.failure(NotImplementedError())

    override suspend fun restoreRecipes(ids: List<String>): Result<Int> =
        Result.failure(NotImplementedError())

    override suspend fun clearRecipes(hard: Boolean): Result<Int> =
        Result.failure(NotImplementedError())
}

private fun recipeDto(id: String, title: String) = RecipeDto(id = id, title = title)

/**
 * Undo on the recipe list. Deleting is optimistic (the card goes away at once) and the
 * row is only soft-deleted server side, so Undo is a restore — these tests pin the
 * state machine the Undo snackbar drives, not the network.
 */
class RecipeListViewModelTest {

    @Test
    fun deletingHidesTheCardAndKeepsItForUndo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(listOf(recipeDto("1", "Pad Thai"), recipeDto("2", "Pho")))
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.delete(viewModel.recipes.first())
            advanceUntilIdle()

            assertEquals(listOf("2"), viewModel.recipes.map { it.id })
            assertEquals(listOf("1"), api.deleted)
            assertEquals("1", viewModel.pendingDeletion?.recipe?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun undoPutsTheRecipeBackWhereItWas() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(
                listOf(recipeDto("1", "Pad Thai"), recipeDto("2", "Pho"), recipeDto("3", "Ramen")),
            )
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()
            val middle = viewModel.recipes[1]

            viewModel.delete(middle)
            advanceUntilIdle()
            assertEquals(listOf("1", "3"), viewModel.recipes.map { it.id })

            viewModel.undoDelete()
            advanceUntilIdle()

            assertEquals(listOf("1", "2", "3"), viewModel.recipes.map { it.id })
            assertEquals(listOf("2"), api.restored)
            assertNull(viewModel.pendingDeletion, "Undo must not stay armed once used")
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dismissingTheSnackbarForgetsTheDeletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(listOf(recipeDto("1", "Pad Thai")))
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.delete(viewModel.recipes.single())
            advanceUntilIdle()
            viewModel.dismissUndo("1")

            assertNull(viewModel.pendingDeletion)
            viewModel.undoDelete() // nothing is pending, so this is a no-op
            advanceUntilIdle()
            assertTrue(api.restored.isEmpty())
            assertTrue(viewModel.recipes.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aStaleDismissDoesNotDisarmTheLatestDeletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(listOf(recipeDto("1", "Pad Thai"), recipeDto("2", "Pho")))
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.delete(viewModel.recipes.first()) // "1"
            advanceUntilIdle()
            viewModel.delete(viewModel.recipes.first()) // "2", while "1" is still pending
            advanceUntilIdle()

            // The replaced snackbar for "1" reports its dismissal late.
            viewModel.dismissUndo("1")

            val pending = viewModel.pendingDeletion
            assertNotNull(pending)
            assertEquals("2", pending.recipe.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedDeletePutsTheCardBackAndReportsWhy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(
                listed = listOf(recipeDto("1", "Pad Thai"), recipeDto("2", "Pho")),
                deleteResult = Result.failure(IllegalStateException("network down")),
            )
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.delete(viewModel.recipes.first())
            advanceUntilIdle()

            assertEquals(listOf("1", "2"), viewModel.recipes.map { it.id })
            assertNull(viewModel.pendingDeletion, "nothing was deleted, so nothing to undo")
            assertEquals("network down", viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theSearchNarrowsTheCardsWithoutDroppingTheLoadedRecipes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(
                listOf(
                    recipeDto("1", "Chicken curry"),
                    recipeDto("2", "Pad Thai"),
                    recipeDto("3", "Chicken soup"),
                ),
            )
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onSearchQueryChange("chic")

            assertEquals(listOf("1", "3"), viewModel.visibleRecipes.map { it.id })
            assertEquals(
                3,
                viewModel.recipes.size,
                "the filter is a view: the loaded collection must stay complete, or Undo " +
                    "would have nothing to restore into",
            )
            assertEquals("chic", viewModel.searchQuery)
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearingTheSearchShowsEverythingAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(
                listOf(recipeDto("1", "Chicken curry"), recipeDto("2", "Pad Thai")),
            )
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()

            viewModel.onSearchQueryChange("thai")
            assertEquals(listOf("2"), viewModel.visibleRecipes.map { it.id })

            viewModel.clearSearch()

            assertEquals("", viewModel.searchQuery)
            assertEquals(listOf("1", "2"), viewModel.visibleRecipes.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun undoPutsTheCardBackInPlaceWhileTheSearchIsActive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = RecordingRecipesApi(
                listOf(
                    recipeDto("1", "Chicken curry"),
                    recipeDto("2", "Pad Thai"),
                    recipeDto("3", "Chicken soup"),
                ),
            )
            val viewModel = RecipeListViewModel(api)
            viewModel.refresh()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("chicken")

            // "3" is the last visible card but the third recipe in the full list.
            viewModel.delete(viewModel.visibleRecipes.last())
            advanceUntilIdle()
            assertEquals(listOf("1", "2"), viewModel.recipes.map { it.id })
            assertEquals(listOf("1"), viewModel.visibleRecipes.map { it.id })

            viewModel.undoDelete()
            advanceUntilIdle()

            assertEquals(listOf("1", "2", "3"), viewModel.recipes.map { it.id })
            assertEquals(listOf("1", "3"), viewModel.visibleRecipes.map { it.id })
            assertEquals(listOf("3"), api.restored)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
