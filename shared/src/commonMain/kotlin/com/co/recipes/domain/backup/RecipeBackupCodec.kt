package com.co.recipes.domain.backup

import com.co.recipes.infraestructure.dataclass.RECIPE_BACKUP_FORMAT_VERSION
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupFile
import com.co.recipes.infraestructure.dataclass.RecipeBackupJson

/**
 * Reads and writes the admin backup file.
 *
 * Pure Kotlin (no platform APIs), so the round trip is covered by commonTest and the
 * admin screen only has to deal with "pick a file" / "save a file".
 */
object RecipeBackupCodec {

    fun encode(recipes: List<RecipeBackupDto>, exportedAt: String): String =
        RecipeBackupJson.encodeToString(
            RecipeBackupFile(
                formatVersion = RECIPE_BACKUP_FORMAT_VERSION,
                exportedAt = exportedAt,
                // Newest first, matching the list screen.
                recipes = recipes.sortedByDescending { it.createdAt ?: "" },
            ),
        )

    /**
     * Parses a backup file. Failures are user-facing: the admin screen shows the message
     * as-is, so they are phrased for a person holding a wrong file, not for a log.
     */
    fun decode(text: String): Result<RecipeBackupFile> = runCatching {
        val file = try {
            RecipeBackupJson.decodeFromString<RecipeBackupFile>(text.trim())
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "That file is not a Shared Recipes backup. " +
                    (error.message?.takeIf { it.isNotBlank() } ?: ""),
            )
        }

        require(file.formatVersion <= RECIPE_BACKUP_FORMAT_VERSION) {
            "The backup was written by a newer version of the app " +
                "(format ${file.formatVersion}, this build reads up to " +
                "$RECIPE_BACKUP_FORMAT_VERSION). Update the app and try again."
        }

        require(file.formatVersion > 0) {
            "The backup file has an invalid format version (${file.formatVersion})."
        }

        file.copy(recipes = file.recipes.filter { it.title.isNotBlank() })
    }

    /** `recipes-backup-2026-11-08.json`, the name offered in the save dialog. */
    fun suggestFileName(date: String): String = "recipes-backup-$date.json"

    fun isBackupFileName(name: String): Boolean =
        name.endsWith(".json", ignoreCase = true)
}
