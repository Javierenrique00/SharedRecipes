package com.co.recipes.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
object RecipeList

@Serializable
data class RecipeDetail(val recipeId: String)

/** `recipeId == null` means "create a new recipe". */
@Serializable
data class RecipeEditor(val recipeId: String? = null)

/** Admin-only: backup, restore and clear the whole collection. */
@Serializable
object AdminRecipes
