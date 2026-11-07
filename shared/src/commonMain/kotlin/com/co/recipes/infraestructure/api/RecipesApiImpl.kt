package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.NewCommentDto
import com.co.recipes.infraestructure.dataclass.RecipeDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RecipesApiImpl(private val client: SupabaseClient) : RecipesApi {

    override suspend fun listRecipes(): Result<List<RecipeDto>> = apiCall {
        client.from("recipes")
            .select(columns = Columns.raw(SUMMARY_COLUMNS)) {
                // Soft-deleted recipes stay in the table so they can be restored;
                // every read has to exclude them explicitly.
                filter { exact("deleted_at", null) }
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<RecipeDto>()
    }

    override suspend fun getRecipe(id: String): Result<RecipeDto> = apiCall {
        client.from("recipes")
            .select(columns = Columns.raw(DETAIL_COLUMNS)) {
                filter {
                    eq("id", id)
                    exact("deleted_at", null)
                }
            }
            .decodeSingle<RecipeDto>()
    }

    override suspend fun createRecipe(params: CreateRecipeParams): Result<String> = apiCall {
        client.postgrest
            .rpc("create_recipe", params.toRpcJson())
            .decodeAs<String>()
    }

    override suspend fun updateRecipe(
        recipeId: String,
        params: CreateRecipeParams,
    ): Result<Unit> = apiCall {
        val payload = buildJsonObject {
            put("p_recipe_id", recipeId)
            params.toRpcJson().forEach { (key, value) -> put(key, value) }
        }
        client.postgrest.rpc("update_recipe", payload)
        Unit
    }

    override suspend fun deleteRecipe(id: String): Result<Unit> = apiCall {
        client.postgrest.rpc("delete_recipe", buildJsonObject { put("p_recipe_id", id) })
        Unit
    }

    override suspend fun restoreRecipe(id: String): Result<Unit> = apiCall {
        client.postgrest.rpc("restore_recipe", buildJsonObject { put("p_recipe_id", id) })
        Unit
    }

    override suspend fun addComment(
        recipeId: String,
        authorId: String,
        body: String,
    ): Result<Unit> = apiCall {
        client.from("recipe_comments")
            .insert(NewCommentDto(recipeId = recipeId, authorId = authorId, body = body.trim()))
        Unit
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = apiCall {
        client.from("recipe_comments").delete {
            filter { eq("id", commentId) }
        }
        Unit
    }

    private fun CreateRecipeParams.toRpcJson(): JsonObject = buildJsonObject {
        put("p_title", title)
        put("p_description", description)
        put("p_youtube_url", youtubeUrl)
        put("p_servings", servings)
        put("p_prep_minutes", prepMinutes)
        put("p_cook_minutes", cookMinutes)
        put(
            "p_ingredients",
            buildJsonArray {
                ingredients.forEach { ingredient ->
                    add(
                        buildJsonObject {
                            put("position", ingredient.position)
                            put("quantity", ingredient.quantity)
                            put("unit", ingredient.unit)
                            put("name", ingredient.name)
                        }
                    )
                }
            }
        )
        put(
            "p_steps",
            buildJsonArray {
                steps.forEach { step ->
                    add(
                        buildJsonObject {
                            put("step_number", step.stepNumber)
                            put("instruction", step.instruction)
                        }
                    )
                }
            }
        )
    }

    private companion object {
        const val SUMMARY_COLUMNS =
            "id,title,description,youtube_id,servings,prep_minutes,cook_minutes,created_at"

        // Nested selects: one round trip for a whole detail screen.
        // Child ordering is not guaranteed by PostgREST, so the mapper sorts.
        const val DETAIL_COLUMNS =
            "id,title,description,youtube_url,youtube_id,servings,prep_minutes,cook_minutes," +
                "created_at," +
                "recipe_ingredients(id,position,quantity,unit,name)," +
                "recipe_steps(id,step_number,instruction)," +
                "recipe_comments(id,recipe_id,author_id,body,created_at)"
    }
}
