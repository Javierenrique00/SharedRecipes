package com.co.recipes.infraestructure.api

import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.NewCommentDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupJson
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
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

    override suspend fun exportRecipes(): Result<List<RecipeBackupDto>> = apiCall {
        // The function returns one jsonb array, so this is a single round trip for the
        // whole collection (children and comments included).
        client.postgrest.rpc("admin_export_recipes").decodeList<RecipeBackupDto>()
    }

    override suspend fun importRecipes(recipes: List<RecipeBackupDto>): Result<ImportSummary> =
        apiCall {
            val payload = buildJsonObject {
                put("p_recipes", RecipeBackupJson.encodeToJsonElement(recipes))
            }
            client.postgrest.rpc("admin_import_recipes", payload).decodeAs<ImportSummary>()
        }

    override suspend fun restoreRecipes(ids: List<String>): Result<Int> = apiCall {
        if (ids.isEmpty()) {
            0
        } else {
            val payload = buildJsonObject {
                putJsonArray("p_ids") { ids.forEach { id -> add(id) } }
            }
            client.postgrest.rpc("admin_restore_recipes", payload).decodeAs<Int>()
        }
    }

    override suspend fun clearRecipes(hard: Boolean): Result<Int> = apiCall {
        val payload = buildJsonObject { put("p_hard", hard) }
        client.postgrest.rpc("admin_clear_recipes", payload).decodeAs<Int>()
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

    // internal (not private) only so RecipeColumnsTest can pin the projections: a
    // summary select that forgets a child silently renders "0 ingredients" on every
    // card instead of failing.
    internal companion object {
        const val COMMON_COLUMNS =
            "id,title,description,youtube_url,youtube_id,servings,prep_minutes,cook_minutes," +
                "created_at"

        // Child ordering is not guaranteed by PostgREST, so the mapper sorts.
        const val INGREDIENT_COLUMNS = "recipe_ingredients(id,position,quantity,unit,name)"
        const val STEP_COLUMNS = "recipe_steps(id,step_number,instruction)"
        const val COMMENT_COLUMNS = "recipe_comments(id,recipe_id,author_id,body,created_at)"

        // The list card renders "${recipe.ingredients.size} ingredients" and
        // "${recipe.comments.size} comments", so both children have to be embedded:
        // RecipeDto defaults a child that was not selected to emptyList(), which is what
        // made every card read "0 ingredients". Steps stay out of the summary — the card
        // never shows them.
        const val SUMMARY_COLUMNS = "$COMMON_COLUMNS,$INGREDIENT_COLUMNS,$COMMENT_COLUMNS"

        // Nested selects: one round trip for a whole detail screen.
        const val DETAIL_COLUMNS =
            "$COMMON_COLUMNS,$INGREDIENT_COLUMNS,$STEP_COLUMNS,$COMMENT_COLUMNS"
    }
}
