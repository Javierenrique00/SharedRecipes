package com.co.recipes.infraestructure.dataclass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file an admin can write out and read back.
 *
 * Keys are the table columns (snake_case) rather than Kotlin-style camelCase on purpose:
 * the file is then a transparent dump of `public.recipes` (plus children) that a DBA can
 * read, diff and hand-edit, and [com.co.recipes.infraestructure.api.RecipesApi.importRecipes]
 * can post it straight back to `admin_import_recipes` with no translation layer.
 *
 * [formatVersion] is the only part of the file the app owns; it is checked on decode so a
 * file written by a future build is rejected instead of silently half-restored.
 */
@Serializable
data class RecipeBackupFile(
    /**
     * No default on purpose: with `encodeDefaults = false` a defaulted value would be left
     * out of the file, and the version marker is the one field that must always be written
     * (and always be present when reading).
     */
    val formatVersion: Int,
    val app: String = "Shared Recipes",
    /** ISO-8601 instant, e.g. `2026-11-08T09:41:12Z`. Informational only. */
    val exportedAt: String,
    val recipes: List<RecipeBackupDto> = emptyList(),
)

/** Bumped only when the shape below changes in a way older code cannot read. */
const val RECIPE_BACKUP_FORMAT_VERSION: Int = 2

@Serializable
data class RecipeBackupDto(
    /** Kept on restore, which is what makes importing a backup idempotent. */
    val id: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    /** Storage object path of the cover photo; the bytes live in the `.zip` beside the JSON. */
    @SerialName("image_path") val imagePath: String? = null,
    val servings: Int? = null,
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Non-null = the recipe was soft-deleted when the backup was taken. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    val ingredients: List<RecipeBackupIngredientDto> = emptyList(),
    val steps: List<RecipeBackupStepDto> = emptyList(),
    /**
     * Comments travel with the backup (they are part of a recipe's history) but are only
     * re-inserted on restore when the author profile still exists — see migration 9.
     */
    val comments: List<RecipeBackupCommentDto> = emptyList(),
) {
    val isDeleted: Boolean get() = deletedAt != null

    /**
     * Every storage path this recipe references, including its steps.
     *
     * The backup archive downloads exactly these objects; the importer uploads the ones
     * belonging to the recipes the admin ticked.
     */
    fun imagePaths(): List<String> = buildList {
        imagePath?.takeIf { it.isNotBlank() }?.let(::add)
        steps.forEach { step -> step.imagePath?.takeIf { it.isNotBlank() }?.let(::add) }
    }
}

@Serializable
data class RecipeBackupIngredientDto(
    val position: Int = 0,
    val quantity: Double? = null,
    val unit: String? = null,
    val name: String,
)

@Serializable
data class RecipeBackupStepDto(
    @SerialName("step_number") val stepNumber: Int,
    val instruction: String,
    @SerialName("image_path") val imagePath: String? = null,
)

@Serializable
data class RecipeBackupCommentDto(
    val id: String? = null,
    @SerialName("author_id") val authorId: String? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
)

/** What `admin_import_recipes` reports back, shown verbatim in the admin screen. */
@Serializable
data class ImportSummary(
    val inserted: Int = 0,
    val updated: Int = 0,
    val restored: Int = 0,
    val skipped: Int = 0,
) {
    val total: Int get() = inserted + updated + restored

    fun describe(): String = buildString {
        append("$inserted added, $updated updated, $restored restored")
        if (skipped > 0) append(", $skipped skipped")
    }
}

/**
 * One serializer for both directions and for the RPC payload.
 *
 * `explicitNulls = false` keeps a backup file free of `"unit": null` noise (the SQL
 * importer treats a missing key and a null identically), and `ignoreUnknownKeys` lets a
 * file written by a newer build still be restored by an older one when the new fields are
 * additive.
 */
internal val RecipeBackupJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

