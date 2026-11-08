package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeDto

interface RecipesApi {

    suspend fun listRecipes(): Result<List<RecipeDto>>

    suspend fun getRecipe(id: String): Result<RecipeDto>

    /** Creates a recipe plus its ingredients and steps atomically. Returns the new id. */
    suspend fun createRecipe(params: CreateRecipeParams): Result<String>

    suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit>

    /**
     * Soft delete: the row is stamped with `deleted_at` and disappears from every
     * query, but stays restorable with [restoreRecipe] (see migration 8).
     */
    suspend fun deleteRecipe(id: String): Result<Unit>

    /** Clears `deleted_at`, bringing the recipe back exactly as it was. */
    suspend fun restoreRecipe(id: String): Result<Unit>

    suspend fun addComment(recipeId: String, authorId: String, body: String): Result<Unit>

    suspend fun deleteComment(commentId: String): Result<Unit>

    // -----------------------------------------------------------------------
    // Recipe administration (admins only; RLS rejects everyone else server side).
    // See migration 9.
    // -----------------------------------------------------------------------

    /**
     * Every recipe — **including soft-deleted ones** — in the backup shape, so the admin
     * screen can offer "export to a file" and list what is currently deleted from one call.
     */
    suspend fun exportRecipes(): Result<List<RecipeBackupDto>>

    /**
     * Restores a backup. Idempotent: rows are matched on their id, so importing the same
     * file twice updates instead of duplicating, and importing a backup of a cleared
     * collection brings the recipes back with their original ids.
     */
    suspend fun importRecipes(recipes: List<RecipeBackupDto>): Result<ImportSummary>

    /** Clears `deleted_at` on the given recipes. Returns how many rows came back. */
    suspend fun restoreRecipes(ids: List<String>): Result<Int>

    /**
     * Empties the collection. `hard = false` is a soft delete (recoverable with
     * [restoreRecipes] or [importRecipes]); `hard = true` purges the rows for good.
     * Returns how many recipes were affected.
     */
    suspend fun clearRecipes(hard: Boolean): Result<Int>
}
