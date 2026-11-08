package com.co.recipes.infraestructure.export

import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.export.ExportFormat
import com.co.recipes.domain.export.ExportResult
import com.co.recipes.domain.export.RecipeExporter
import com.co.recipes.domain.export.currentExportTimestamp
import com.co.recipes.domain.export.recipeFileName
import com.co.recipes.domain.export.toMarkdown
import com.co.recipes.infraestructure.files.DeviceDocumentFiles
import com.co.recipes.infraestructure.files.DocumentFileStore
import com.co.recipes.infraestructure.files.SaveResult
import com.co.recipes.infraestructure.pdf.recipeToPdf

/**
 * The two export formats, side by side:
 *
 *  - **Markdown** is pure Kotlin ([toMarkdown]) written through the same save dialog the
 *    admin backup uses, so it produces byte-identical output on all four targets.
 *  - **PDF** is rendered by the hand-written writer in `infraestructure/pdf` and saved the
 *    same way: a real "save as" dialog on Android, iOS and desktop, a download in the
 *    browser. No platform PDF API, no Skia, nothing that can drift with a Compose upgrade —
 *    the bytes are identical everywhere, and the text stays selectable/searchable.
 *
 * The clock is injectable so tests can pin a timestamp; production code uses the default.
 */
class RecipeExporterImpl(
    private val timestamp: () -> String = ::currentExportTimestamp,
    private val files: DocumentFileStore = DeviceDocumentFiles,
) : RecipeExporter {

    override suspend fun export(recipe: Recipe, format: ExportFormat): ExportResult =
        when (format) {
            ExportFormat.MARKDOWN -> exportMarkdown(recipe)
            ExportFormat.PDF -> exportPdf(recipe)
        }

    private suspend fun exportMarkdown(recipe: Recipe): ExportResult {
        val fileName = recipeFileName(recipe.title, ExportFormat.MARKDOWN.extension)
        val markdown = recipe.toMarkdown(exportedAt = timestamp())
        return files.saveMarkdownFile(fileName, markdown).toExportResult()
    }

    private suspend fun exportPdf(recipe: Recipe): ExportResult {
        val fileName = recipeFileName(recipe.title, ExportFormat.PDF.extension)
        val bytes = recipeToPdf(recipe, exportedAt = timestamp())
        return files.savePdfFile(fileName, bytes).toExportResult()
    }
}

private fun SaveResult.toExportResult(): ExportResult = when (this) {
    is SaveResult.Saved -> ExportResult.Saved(name)
    SaveResult.Cancelled -> ExportResult.Cancelled
    is SaveResult.Failed -> ExportResult.Failed(message)
}
