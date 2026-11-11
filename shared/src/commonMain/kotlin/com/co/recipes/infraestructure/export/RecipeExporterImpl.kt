package com.co.recipes.infraestructure.export

import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.export.ExportFormat
import com.co.recipes.domain.export.ExportResult
import com.co.recipes.domain.export.RecipeExporter
import com.co.recipes.domain.export.ZIP_EXTENSION
import com.co.recipes.domain.export.currentExportTimestamp
import com.co.recipes.domain.export.recipeFileName
import com.co.recipes.domain.export.recipeImageFileNames
import com.co.recipes.domain.export.toMarkdown
import com.co.recipes.infraestructure.files.DeviceDocumentFiles
import com.co.recipes.infraestructure.files.DocumentFileStore
import com.co.recipes.infraestructure.files.SaveResult
import com.co.recipes.infraestructure.pdf.recipeToPdf
import com.co.recipes.infraestructure.storage.RecipeImageStore
import com.co.recipes.infraestructure.zip.encodeZip

/**
 * The two export formats, side by side:
 *
 *  - **Markdown** is pure Kotlin ([toMarkdown]) written through the same save dialog the
 *    admin backup uses, so it produces identical output on all four targets. A recipe with
 *    photos becomes a `.zip` holding `recipe.md` next to `images/`: relative links are the
 *    only form that still shows the pictures when the document is opened offline.
 *  - **PDF** is rendered by the hand-written writer in `infraestructure/pdf` and saved the
 *    same way: a real "save as" dialog on Android, iOS and desktop, a download in the
 *    browser. Photos are embedded as JPEG streams, so the document is self-contained.
 *
 * Images are downloaded here (not inside the writers) so both formats share one fetch, and
 * a failed download only drops that one picture: the document still gets written.
 *
 * The clock is injectable so tests can pin a timestamp; production code uses the default.
 */
class RecipeExporterImpl(
    private val images: RecipeImageStore,
    private val timestamp: () -> String = ::currentExportTimestamp,
    private val files: DocumentFileStore = DeviceDocumentFiles,
) : RecipeExporter {

    override suspend fun export(recipe: Recipe, format: ExportFormat): ExportResult =
        when (format) {
            ExportFormat.MARKDOWN -> exportMarkdown(recipe)
            ExportFormat.PDF -> exportPdf(recipe)
        }

    private suspend fun exportMarkdown(recipe: Recipe): ExportResult {
        val contents = downloadImages(recipe)
        val names = recipeImageFileNames(recipe).filterKeys { it in contents }
        val markdown = recipe.toMarkdown(exportedAt = timestamp(), imageFileNames = names)

        if (names.isEmpty()) {
            // No photos: keep the plain, human-readable single file.
            val fileName = recipeFileName(recipe.title, ExportFormat.MARKDOWN.extension)
            return files.saveMarkdownFile(fileName, markdown).toExportResult()
        }

        val entries = buildMap {
            put(
                recipeFileName(recipe.title, ExportFormat.MARKDOWN.extension),
                markdown.encodeToByteArray(),
            )
            names.forEach { (path, imageName) -> put(imageName, contents.getValue(path)) }
        }
        return files.saveZipFile(recipeFileName(recipe.title, ZIP_EXTENSION), encodeZip(entries))
            .toExportResult()
    }

    private suspend fun exportPdf(recipe: Recipe): ExportResult {
        val fileName = recipeFileName(recipe.title, ExportFormat.PDF.extension)
        val bytes = recipeToPdf(recipe, exportedAt = timestamp(), images = downloadImages(recipe))
        return files.savePdfFile(fileName, bytes).toExportResult()
    }

    /**
     * Reads every photo the recipe references. A path that cannot be read is left out on
     * purpose: a flaky download must not turn a working export into an error.
     */
    private suspend fun downloadImages(recipe: Recipe): Map<String, ByteArray> {
        val paths = buildList {
            recipe.imagePath?.takeIf { it.isNotBlank() }?.let(::add)
            recipe.steps.forEach { step -> step.imagePath?.takeIf { it.isNotBlank() }?.let(::add) }
        }.distinct()

        val contents = mutableMapOf<String, ByteArray>()
        paths.forEach { path ->
            images.download(path).onSuccess { contents[path] = it }
        }
        return contents
    }
}

private fun SaveResult.toExportResult(): ExportResult = when (this) {
    is SaveResult.Saved -> ExportResult.Saved(name)
    SaveResult.Cancelled -> ExportResult.Cancelled
    is SaveResult.Failed -> ExportResult.Failed(message)
}
