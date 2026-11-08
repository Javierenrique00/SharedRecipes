package com.co.recipes.domain.export

import com.co.recipes.domain.dataclass.Ingredient
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.Step
import com.co.recipes.infraestructure.pdf.recipeToPdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PDF is written by hand, so its bytes are checked here rather than on a device: this
 * runs the same code every platform runs, and parses the result far enough to prove it is a
 * well-formed document (header, page objects, xref, trailer) whose text content is really in
 * there — uncompressed streams make that greppable.
 */
class RecipePdfTest {

    private fun recipe(steps: Int = 3, ingredients: Int = 2) = Recipe(
        id = "1",
        title = "Pad Thai",
        description = "Rice noodles with tamarind.",
        youtubeId = "BWk1C0dMj8o",
        servings = 2,
        prepMinutes = 20,
        cookMinutes = 10,
        ingredients = (1..ingredients).map {
            Ingredient(quantity = 200.0, unit = "g", name = "ingredient $it")
        },
        steps = (1..steps).map { Step(number = it, instruction = "Step $it: stir the noodles.") },
        comments = emptyList(),
    )

    private fun ByteArray.asText(): String = decodeToString()

    @Test
    fun theFileIsAWellFormedPdf() {
        val pdf = recipeToPdf(recipe(), exportedAt = "2026-11-08 09:41").asText()

        assertTrue(pdf.startsWith("%PDF-1.4"), pdf.take(20))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"), "missing EOF marker")
        assertTrue(pdf.contains("/Type /Catalog"), "missing catalog")
        assertTrue(pdf.contains("/Type /Pages"), "missing page tree")
        assertTrue(pdf.contains("startxref"), "missing xref pointer")
        assertTrue(pdf.contains("/BaseFont /Helvetica"), "fonts must be the base-14 pair")
    }

    @Test
    fun theXrefOffsetsPointAtRealObjects() {
        val bytes = recipeToPdf(recipe(), exportedAt = "2026-11-08 09:41")
        val pdf = bytes.decodeToString()

        val startxref = pdf.substringAfterLast("startxref\n").substringBefore('\n').trim().toInt()
        assertTrue(
            pdf.substring(startxref).startsWith("xref"),
            "startxref must point at the xref table",
        )

        // Every entry after the free one must land on "<n> 0 obj" — in *byte* offsets, which
        // is why this reads from the ByteArray and not from the decoded string.
        pdf.substring(startxref)
            .lineSequence()
            .drop(3)
            .takeWhile { it.endsWith(" n ") }
            .forEach { entry ->
                val offset = entry.substring(0, 10).toInt()
                val atOffset = bytes.copyOfRange(offset, offset + 8).decodeToString()
                assertTrue(atOffset.contains("0 obj"), "offset $offset points at '$atOffset'")
            }
    }

    @Test
    fun theRecipeTextIsActuallyInTheDocument() {
        val pdf = recipeToPdf(recipe(), exportedAt = "2026-11-08 09:41").asText()

        assertTrue(pdf.contains("(Pad Thai) Tj"), "title is missing")
        assertTrue(pdf.contains("Ingredients"), "ingredients heading is missing")
        assertTrue(pdf.contains("Method"), "method heading is missing")
        assertTrue(pdf.contains("ingredient 2"), "every ingredient must be printed")
        assertTrue(pdf.contains("Step 3: stir the noodles."), "every step must be printed")
        assertTrue(pdf.contains("Shared Recipes"), "footer is missing")
        assertTrue(pdf.contains("Video: https://www.youtube.com/watch?v=BWk1C0dMj8o"))
    }

    @Test
    fun aLongRecipeIsSplitIntoSeveralPagesWithNumberedFooters() {
        val pdf = recipeToPdf(recipe(steps = 40), exportedAt = "2026-11-08 09:41").asText()

        val pageCount = Regex("/Type /Page ").findAll(pdf).count()
        assertTrue(pageCount > 1, "40 steps must not fit on one page")
        assertTrue(pdf.contains("(Page 1 of $pageCount) Tj"), "footers are not numbered")
        assertTrue(pdf.contains("(Page $pageCount of $pageCount) Tj"))

        // And no step is lost on the way.
        (1..40).forEach { number ->
            assertTrue(pdf.contains("Step $number: stir the noodles."), "step $number went missing")
        }
    }

    @Test
    fun parenthesesAreEscapedAndAccentsStaySingleByte() {
        val tricky = recipe().copy(title = "Pad Thai (especial) — ají")
        val bytes = recipeToPdf(tricky, exportedAt = "2026-11-08 09:41")
        val text = bytes.decodeToString()

        // The parentheses are reserved by the format, so they must come out escaped.
        assertTrue(text.contains("(Pad Thai \\(especial\\)"), text.take(600))

        // WinAnsi is what makes this a *byte* offset document: the em dash (0x97) and the
        // í (0xED) each occupy exactly one byte, so the xref stays valid.
        assertTrue(bytes.any { it == 0x97.toByte() }, "em dash was not encoded as WinAnsi")
        assertTrue(bytes.any { it == 0xED.toByte() }, "í was not encoded as WinAnsi")
    }

    @Test
    fun unicodeOutsideWinAnsiIsReplacedInsteadOfBreakingTheDocument() {
        val emoji = recipe().copy(title = "Pad Thai \uD83C\uDF5C")
        val text = recipeToPdf(emoji, exportedAt = "2026-11-08 09:41").decodeToString()

        // A lone surrogate cannot be encoded; it must degrade to '?' and leave a valid file.
        assertTrue(text.contains("(Pad Thai ?") || text.contains("(Pad Thai ??"), text.take(200))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun fileNameSlugIsSafeOnEveryFileSystem() {
        assertEquals("pad-thai", recipeFileSlug("Pad Thai"))
        // Accents are kept: they are legal on every file system we target, and a Spanish
        // title stays readable in the save dialog.
        assertEquals("sopa-de-ají", recipeFileSlug("Sopa de ají"))
        assertEquals("recipe", recipeFileSlug("***"))
        assertEquals("pad-thai.md", recipeFileName("Pad Thai", "md"))
        assertEquals("pad-thai.pdf", recipeFileName("Pad Thai", "pdf"))
    }
}
