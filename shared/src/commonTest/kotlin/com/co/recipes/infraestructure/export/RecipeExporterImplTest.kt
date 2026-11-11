package com.co.recipes.infraestructure.export

import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.Step
import com.co.recipes.domain.export.ExportFormat
import com.co.recipes.domain.export.ExportResult
import com.co.recipes.testutil.FakeDocumentFiles
import com.co.recipes.testutil.FakeRecipeImageStore
import kotlinx.coroutines.test.runTest
import no.synth.kmpzip.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a recipe with photos leaves the app: the PDF embeds them, and the Markdown becomes a
 * zip so its `images/` links still resolve when the document is opened offline.
 */
class RecipeExporterImplTest {

    /** SOI + a baseline SOF0 carrying 400×300 + EOI: enough for the PDF's aspect-ratio parser. */
    private val jpeg: ByteArray = bytes(
        0xFF, 0xD8,
        0xFF, 0xC0, 0x00, 0x11, 0x08,
        0x01, 0x2C, // 300 px tall
        0x01, 0x90, // 400 px wide
        0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        0xFF, 0xD9,
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val recipe = Recipe(
        id = "1",
        title = "Pad Thai",
        description = "Rice noodles.",
        youtubeId = null,
        imagePath = "recipes/cover.jpg",
        servings = 2,
        prepMinutes = null,
        cookMinutes = null,
        ingredients = emptyList(),
        steps = listOf(Step(number = 1, instruction = "Stir.", imagePath = "steps/one.jpg")),
        comments = emptyList(),
    )

    private fun exporter(
        images: FakeRecipeImageStore,
        files: FakeDocumentFiles,
    ) = RecipeExporterImpl(images = images, timestamp = { "2026-11-11 10:00" }, files = files)

    @Test
    fun markdownWithPhotosIsSavedAsAZip() = runTest {
        val files = FakeDocumentFiles()
        val images = FakeRecipeImageStore(
            mapOf("recipes/cover.jpg" to jpeg, "steps/one.jpg" to jpeg),
        )

        val result = exporter(images, files).export(recipe, ExportFormat.MARKDOWN)

        assertTrue(result is ExportResult.Saved, result.toString())
        assertEquals("pad-thai.zip", files.savedFileName)

        val entries = unzip(files.savedBytes!!)
        assertEquals(
            setOf("pad-thai.md", "images/cover.jpg", "images/step-1.jpg"),
            entries.keys,
        )
        val markdown = entries.getValue("pad-thai.md").decodeToString()
        assertTrue(markdown.contains("![Step 1](images/step-1.jpg)"), markdown)
        assertTrue(markdown.contains("![Pad Thai](images/cover.jpg)"), markdown)
    }

    @Test
    fun markdownWithoutPhotosStaysASingleMarkdownFile() = runTest {
        val files = FakeDocumentFiles()
        val images = FakeRecipeImageStore()
        val textOnly = recipe.copy(
            imagePath = null,
            steps = listOf(Step(number = 1, instruction = "Stir.")),
        )

        exporter(images, files).export(textOnly, ExportFormat.MARKDOWN)

        assertEquals("pad-thai.md", files.savedFileName)
        assertTrue(files.savedContent.orEmpty().contains("## Method"))
    }

    @Test
    fun thePdfEmbedsTheDownloadedPhotos() = runTest {
        val files = FakeDocumentFiles()
        val images = FakeRecipeImageStore(
            mapOf("recipes/cover.jpg" to jpeg, "steps/one.jpg" to jpeg),
        )

        exporter(images, files).export(recipe, ExportFormat.PDF)

        assertEquals("pad-thai.pdf", files.savedFileName)
        val pdf = files.savedBytes!!.decodeToString()
        assertTrue(pdf.contains("/Filter /DCTDecode"), "the photos must be embedded")
    }

    @Test
    fun aPhotoThatCannotBeDownloadedDoesNotBlockTheExport() = runTest {
        val files = FakeDocumentFiles()
        val images = FakeRecipeImageStore() // the bucket returns nothing

        val result = exporter(images, files).export(recipe, ExportFormat.MARKDOWN)

        assertTrue(result is ExportResult.Saved, result.toString())
        assertEquals("pad-thai.md", files.savedFileName, "no photos, no reason to zip")
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }
}
