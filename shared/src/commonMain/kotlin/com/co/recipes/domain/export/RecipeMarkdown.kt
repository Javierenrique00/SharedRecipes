package com.co.recipes.domain.export

import com.co.recipes.domain.dataclass.Ingredient
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.formatQuantity

/**
 * Markdown rendering of a recipe — the "export to a document" option that needs no
 * platform code at all: it is a pure string, so the file is identical on Android, iOS,
 * desktop and the browser (only the save dialog differs).
 *
 * The output is plain CommonMark (headings, a bullet list, an ordered list) so it opens
 * correctly in Obsidian, Notion, GitHub, VS Code and any email client that renders it.
 * Comments are left out on purpose: the exported document is the recipe, not the thread.
 *
 * [imageFileNames] maps a storage path to the relative file name the image will have
 * inside the exported `.zip` (`images/cover.jpg`, `images/step-2.jpg`, …). A recipe with
 * photos therefore stays one self-contained document that works offline; a recipe without
 * them produces exactly the same Markdown as before.
 */
fun Recipe.toMarkdown(
    exportedAt: String,
    imageFileNames: Map<String, String> = emptyMap(),
): String = buildString {
    appendLine("# ${escapeMarkdownLine(title)}")
    appendLine()

    description?.takeIf { it.isNotBlank() }?.let { text ->
        appendLine(escapeMarkdownInline(text.trim()))
        appendLine()
    }

    val meta = listOfNotNull(
        servings?.let { "- **Servings:** $it" },
        prepMinutes?.let { "- **Prep:** $it min" },
        cookMinutes?.let { "- **Cook:** $it min" },
        totalMinutes?.let { "- **Total:** $it min" },
        youtubeWatchUrl?.let { "- **Video:** $it" },
    )
    if (meta.isNotEmpty()) {
        meta.forEach { appendLine(it) }
        appendLine()
    }

    imagePath?.let { imageFileNames[it] }?.let { fileName ->
        appendLine("![${escapeMarkdownInline(title)}]($fileName)")
        appendLine()
    }

    if (ingredients.isNotEmpty()) {
        appendLine("## Ingredients")
        appendLine()
        ingredients.forEach { ingredient ->
            appendLine(ingredient.toMarkdown())
        }
        appendLine()
    }

    if (steps.isNotEmpty()) {
        appendLine("## Method")
        appendLine()
        steps.forEach { step ->
            appendLine(step.toMarkdownItem())
            step.imagePath?.let { imageFileNames[it] }?.let { fileName ->
                appendLine()
                appendLine("![Step ${step.number}]($fileName)")
            }
        }
        appendLine()
    }

    appendLine("---")
    appendLine()
    appendLine("*Exported from Shared Recipes on $exportedAt*")
}

/** `- **200 g** flat rice noodles`, or just `- 2 eggs` when there is no amount. */
private fun Ingredient.toMarkdown(): String {
    val amount = listOfNotNull(
        quantity?.let { formatQuantity(it) },
        unit?.takeIf { it.isNotBlank() }?.trim(),
    ).joinToString(" ")

    val name = escapeMarkdownInline(name.trim())

    return if (amount.isEmpty()) {
        "- $name"
    } else {
        "- **${escapeMarkdownInline(amount)}** $name"
    }
}

/**
 * Numbered list item. Continuation lines are indented by three spaces so a multi-line
 * instruction stays inside its own item instead of turning into a new paragraph.
 */
private fun com.co.recipes.domain.dataclass.Step.toMarkdownItem(): String {
    val lines = instruction.trim().lines()
        .map { escapeMarkdownLine(it.trim()) }
        .filter { it.isNotEmpty() }

    if (lines.isEmpty()) return "$number. ${escapeMarkdownLine(instruction.trim())}"

    return buildString {
        append("$number. ")
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n   ")
            append(line)
        }
    }
}

/** Characters that would start a block when they are the first thing on a line. */
private const val LineStartMarkers = "#-*+>=|"

/**
 * Escapes the inline markers a recipe title or instruction could contain, so a title like
 * `*Best* noodles` renders literally instead of turning half the document bold.
 */
internal fun escapeMarkdownInline(text: String): String = text
    .replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("*", "\\*")
    .replace("_", "\\_")
    .replace("[", "\\[")
    .replace("]", "\\]")

/**
 * [escapeMarkdownInline] plus a guard against accidentally starting a new block.
 *
 * A leading `#`, `-`, `>`, … gets a backslash. For something like `1. Soak`, CommonMark
 * ignores `\1` (a backslash only escapes punctuation), so the *period* is escaped instead —
 * that is the form that actually stops the line becoming an ordered list.
 */
internal fun escapeMarkdownLine(text: String): String {
    if (text.isEmpty()) return text

    val escaped = escapeMarkdownInline(text)
    val firstCharacter = escaped[0]

    val digits = escaped.takeWhile { it.isDigit() }
    val afterDigits = digits.length
    if (digits.isNotEmpty() && escaped.getOrNull(afterDigits) in listOf('.', ')')) {
        return escaped.substring(0, afterDigits) + "\\" + escaped.substring(afterDigits)
    }

    return if (firstCharacter in LineStartMarkers) "\\$escaped" else escaped
}
