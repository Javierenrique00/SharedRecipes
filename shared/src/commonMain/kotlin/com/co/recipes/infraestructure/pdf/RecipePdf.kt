package com.co.recipes.infraestructure.pdf

import com.co.recipes.domain.dataclass.Ingredient
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.formatQuantity

/**
 * Renders a recipe as a printable A4 PDF.
 *
 * Pure Kotlin: same bytes on Android, iOS, desktop and the browser, no renderer, no
 * platform font, nothing to keep in sync. The layout is deliberately plain (title, meta,
 * description, ingredients, numbered method, footer) because it has to survive being printed
 * on paper and being read on a phone.
 *
 * [images] maps a storage path to the JPEG bytes already downloaded by the exporter. A
 * path that is missing from the map is simply skipped, so an unreachable bucket never
 * blocks the export — the document keeps its text.
 *
 * Comments are not included: the document is the recipe, not the conversation.
 */
fun recipeToPdf(
    recipe: Recipe,
    exportedAt: String,
    images: Map<String, ByteArray> = emptyMap(),
): ByteArray {
    val document = PdfDocumentBuilder(title = recipe.title)
    val textWidth = PDF_A4_WIDTH - MARGIN * 2

    document.ensurePage()

    // Title
    wrapRuns(
        document = document,
        runs = listOf(PdfTextRun(recipe.title, size = 22f, bold = true)),
        maxWidth = textWidth,
        lineHeight = 26f,
    )
    document.moveDown(4f)

    // Meta line: only the values the recipe actually has.
    val meta = listOfNotNull(
        recipe.servings?.let { "$it servings" },
        recipe.prepMinutes?.let { "prep $it min" },
        recipe.cookMinutes?.let { "cook $it min" },
        recipe.totalMinutes?.let { "total $it min" },
    ).joinToString("   ·   ")
    if (meta.isNotEmpty()) {
        wrapRuns(document, listOf(PdfTextRun(meta, 10f, gray = 0.4f)), textWidth, 13f)
    }

    recipe.youtubeWatchUrl?.let { url ->
        wrapRuns(document, listOf(PdfTextRun("Video: $url", 9f, gray = 0.4f)), textWidth, 12f)
    }

    document.moveDown(6f)

    photo(document, images[recipe.imagePath], maxHeight = 260f, textWidth = textWidth, caption = null)

    recipe.description?.takeIf { it.isNotBlank() }?.let { description ->
        paragraphs(document, description, size = 10.5f, maxWidth = textWidth)
        document.moveDown(6f)
    }

    if (recipe.ingredients.isNotEmpty()) {
        sectionTitle(document, "Ingredients")
        recipe.ingredients.forEach { ingredient ->
            ingredientRuns(ingredient).let { runs ->
                wrapRuns(
                    document = document,
                    runs = runs,
                    maxWidth = textWidth,
                    lineHeight = 14f,
                    indent = 10f,
                    hangingIndent = 10f,
                )
            }
        }
        document.moveDown(6f)
    }

    if (recipe.steps.isNotEmpty()) {
        sectionTitle(document, "Method")
        recipe.steps.sortedBy { it.number }.forEach { step ->
            val runs = listOf(
                PdfTextRun("${step.number}.", 10.5f, bold = true),
                PdfTextRun("  " + step.instruction.trim(), 10.5f),
            )
            wrapRuns(
                document = document,
                runs = runs,
                maxWidth = textWidth,
                lineHeight = 14f,
                hangingIndent = 18f,
            )
            document.moveDown(3f)

            // A photo per step is what makes the printed method usable while cooking.
            photo(
                document = document,
                bytes = step.imagePath?.let { images[it] },
                maxHeight = 200f,
                textWidth = textWidth,
                caption = "Step ${step.number}",
            )
        }
    }

    return document.build(footerLeft = "Shared Recipes · $exportedAt")
}

// ---------------------------------------------------------------------------
// Layout helpers: greedy word wrapping with hanging indents and page breaks.
// ---------------------------------------------------------------------------

private fun sectionTitle(document: PdfDocumentBuilder, text: String) {
    document.moveDown(6f)
    document.line(listOf(PdfTextRun(text, size = 13f, bold = true)), lineHeight = 16f)
    document.rule()
    document.moveDown(4f)
}

/**
 * Draws one JPEG at its natural aspect ratio, with an optional grey caption underneath.
 * Missing or unreadable bytes are silently skipped: a failed image download must not cost
 * the reader the rest of the document.
 */
private fun photo(
    document: PdfDocumentBuilder,
    bytes: ByteArray?,
    maxHeight: Float,
    textWidth: Float,
    caption: String?,
) {
    if (bytes == null) return
    val size = jpegSize(bytes) ?: return
    if (!document.image(bytes, size.width, size.height, maxWidth = textWidth, maxHeight = maxHeight)) {
        return
    }

    document.moveDown(3f)
    if (caption != null) {
        document.line(listOf(PdfTextRun(caption, 8.5f, gray = 0.4f)), lineHeight = 11f)
    }
    document.moveDown(6f)
}

/** `•  **200 g**  flat rice noodles`, or just the name when there is no amount. */
private fun ingredientRuns(ingredient: Ingredient): List<PdfTextRun> {
    val amount = listOfNotNull(
        ingredient.quantity?.let { formatQuantity(it) },
        ingredient.unit?.takeIf { it.isNotBlank() }?.trim(),
    ).joinToString(" ")

    return if (amount.isEmpty()) {
        listOf(
            PdfTextRun("\u2022", 10.5f, gray = 0.4f),
            PdfTextRun("  " + ingredient.name.trim(), 10.5f),
        )
    } else {
        listOf(
            PdfTextRun("\u2022", 10.5f, gray = 0.4f),
            PdfTextRun("  $amount", 10.5f, bold = true),
            PdfTextRun("  " + ingredient.name.trim(), 10.5f),
        )
    }
}

/** Free text, honouring the author's own line breaks. */
private fun paragraphs(
    document: PdfDocumentBuilder,
    text: String,
    size: Float,
    maxWidth: Float,
) {
    text.trim().lines().forEach { line ->
        if (line.isBlank()) {
            document.moveDown(size * 0.8f)
        } else {
            wrapRuns(document, listOf(PdfTextRun(line, size)), maxWidth, size + 4f)
        }
    }
}

private data class StyledWord(
    val text: String,
    val spaceBefore: Boolean,
    val run: PdfTextRun,
)

/**
 * Wraps [runs] into [maxWidth] and draws them, starting a new page whenever the cursor runs
 * out of room. [indent] applies to the first line and [hangingIndent] to the rest, which is
 * what keeps a two-line step aligned under its number.
 */
internal fun wrapRuns(
    document: PdfDocumentBuilder,
    runs: List<PdfTextRun>,
    maxWidth: Float,
    lineHeight: Float,
    indent: Float = 0f,
    hangingIndent: Float = 0f,
) {
    wordsOf(runs).forEach { line ->
        if (document.remainingHeight() < lineHeight) document.newPage()

        val x = MARGIN + if (line.isFirstLine) indent else indent + hangingIndent
        document.line(line.words.toRuns(), x = x, lineHeight = lineHeight)
    }
}

private class WrappedLine(val words: List<StyledWord>, val isFirstLine: Boolean)

private fun List<StyledWord>.toRuns(): List<PdfTextRun> {
    val merged = mutableListOf<PdfTextRun>()
    forEachIndexed { index, word ->
        val prefix = if (word.spaceBefore && index > 0) " " else ""
        val last = merged.lastOrNull()
        if (last != null && last.size == word.run.size && last.bold == word.run.bold && last.gray == word.run.gray) {
            merged[merged.lastIndex] = last.copy(text = last.text + prefix + word.text)
        } else {
            merged += word.run.copy(text = prefix + word.text)
        }
    }
    return merged
}

private fun wordsOf(runs: List<PdfTextRun>): List<WrappedLine> {
    val words = mutableListOf<StyledWord>()
    runs.forEach { run ->
        run.text.split(' ').filter { it.isNotEmpty() }.forEach { token ->
            words += StyledWord(token, spaceBefore = words.isNotEmpty(), run = run)
        }
    }
    if (words.isEmpty()) return emptyList()

    val lines = mutableListOf<WrappedLine>()
    var line = mutableListOf<StyledWord>()
    var lineWidth = 0f

    words.forEach { word ->
        val wordWidth = widthOf(word)
        // Hard-split a word that cannot fit on a line of its own (a long URL, say).
        if (wordWidth > PDF_A4_WIDTH - MARGIN * 2) {
            if (line.isNotEmpty()) {
                lines += WrappedLine(line, lines.isEmpty())
                line = mutableListOf()
                lineWidth = 0f
            }
            lines += WrappedLine(listOf(word), lines.isEmpty())
            return@forEach
        }

        if (line.isNotEmpty() && lineWidth + wordWidth > PDF_A4_WIDTH - MARGIN * 2) {
            lines += WrappedLine(line, lines.isEmpty())
            line = mutableListOf()
            lineWidth = 0f
            line += word
            lineWidth = wordWidth
        } else {
            line += word
            lineWidth += wordWidth
        }
    }

    if (line.isNotEmpty()) lines += WrappedLine(line, lines.isEmpty())
    return lines
}

private fun widthOf(word: StyledWord): Float {
    val space = if (word.spaceBefore) textWidth(" ", word.run.size, word.run.bold) else 0f
    return textWidth(word.text, word.run.size, word.run.bold) + space
}
