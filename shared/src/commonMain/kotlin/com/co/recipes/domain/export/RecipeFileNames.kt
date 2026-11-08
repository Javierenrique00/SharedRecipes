package com.co.recipes.domain.export

/**
 * File names for exported recipes, shared by the PDF and Markdown paths so both formats
 * land next to each other with the same base name.
 *
 * The slug is intentionally conservative: lowercase, ASCII-ish separators, no spaces and
 * a length cap, because the name has to survive a save dialog on four operating systems.
 */
fun recipeFileSlug(title: String, fallback: String = "recipe"): String {
    val slug = title
        .lowercase()
        .map { character -> if (character.isLetterOrDigit()) character else '-' }
        .joinToString(separator = "")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString(separator = "-")
        .take(60)
        .trim('-')

    return slug.ifBlank { fallback }
}

/** `Pad Thai` + `md` -> `pad-thai.md`. */
fun recipeFileName(title: String, extension: String): String =
    "${recipeFileSlug(title)}.$extension"
