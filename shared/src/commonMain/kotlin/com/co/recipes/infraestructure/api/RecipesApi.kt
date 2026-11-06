package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.RecipeDto

interface RecipesApi {

    suspend fun listRecipes(): Result<List<RecipeDto>>

    suspend fun getRecipe(id: String): Result<RecipeDto>

    /** Creates a recipe plus its ingredients and steps atomically. Returns the new id. */
    suspend fun createRecipe(params: CreateRecipeParams): Result<String>

    suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit>

    suspend fun deleteRecipe(id: String): Result<Unit>

    suspend fun addComment(recipeId: String, authorId: String, body: String): Result<Unit>

    suspend fun deleteComment(commentId: String): Result<Unit>
}
