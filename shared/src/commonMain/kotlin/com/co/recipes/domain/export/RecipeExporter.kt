package com.co.recipes.domain.export

import com.co.recipes.domain.dataclass.Recipe

/**
 * The formats a user can export a single recipe to.
 *
 * [description] is what the picker dialog shows, so the choice is explained in one line
 * instead of "PDF" vs "MD" alone.
 */
enum class ExportFormat(
    val extension: String,
    val label: String,
    val description: String,
) {
    PDF(
        extension = "pdf",
        label = "PDF document",
        description = "Ready to print or share; opens everywhere.",
    ),
    MARKDOWN(
        extension = "md",
        label = "Markdown (.md)",
        description = "Plain text for notes apps; renders the same on every platform.",
    ),
}

sealed interface ExportResult {
    /** [detail] carries extra context, e.g. the folder a desktop PDF was written to. */
    data class Saved(val fileName: String, val detail: String? = null) : ExportResult

    data object Cancelled : ExportResult

    data class Failed(val message: String) : ExportResult
}

/**
 * Turns a [Recipe] into a document the user keeps.
 *
 * An interface rather than a concrete object so view models can be tested without a
 * dialog, a file system or a PDF renderer — see `RecipeDetailViewModelTest`.
 */
interface RecipeExporter {
    suspend fun export(recipe: Recipe, format: ExportFormat): ExportResult
}
