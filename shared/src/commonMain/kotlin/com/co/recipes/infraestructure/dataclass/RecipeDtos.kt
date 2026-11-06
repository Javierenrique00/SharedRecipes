package com.co.recipes.infraestructure.dataclass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val email: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val role: String = "client",
)

@Serializable
data class IngredientDto(
    val id: String? = null,
    val position: Int? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val name: String,
)

@Serializable
data class StepDto(
    val id: String? = null,
    @SerialName("step_number") val stepNumber: Int,
    val instruction: String,
)

@Serializable
data class CommentDto(
    val id: String? = null,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * Mirrors the nested PostgREST payload:
 * `*, recipe_ingredients(*), recipe_steps(*), recipe_comments(*)`
 */
@Serializable
data class RecipeDto(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    @SerialName("youtube_id") val youtubeId: String? = null,
    val servings: Int? = null,
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("recipe_ingredients") val ingredients: List<IngredientDto> = emptyList(),
    @SerialName("recipe_steps") val steps: List<StepDto> = emptyList(),
    @SerialName("recipe_comments") val comments: List<CommentDto> = emptyList(),
)

@Serializable
data class NewCommentDto(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
)

// ---------------------------------------------------------------------------
// RPC payloads. Names must match the plpgsql parameter names exactly,
// because PostgREST resolves RPC arguments by name.
// ---------------------------------------------------------------------------

@Serializable
data class IngredientParam(
    val position: Int,
    val quantity: Double? = null,
    val unit: String? = null,
    val name: String,
)

@Serializable
data class StepParam(
    @SerialName("step_number") val stepNumber: Int,
    val instruction: String,
)

@Serializable
data class CreateRecipeParams(
    @SerialName("p_title") val title: String,
    @SerialName("p_description") val description: String? = null,
    @SerialName("p_youtube_url") val youtubeUrl: String? = null,
    @SerialName("p_servings") val servings: Int? = null,
    @SerialName("p_prep_minutes") val prepMinutes: Int? = null,
    @SerialName("p_cook_minutes") val cookMinutes: Int? = null,
    @SerialName("p_ingredients") val ingredients: List<IngredientParam> = emptyList(),
    @SerialName("p_steps") val steps: List<StepParam> = emptyList(),
)
