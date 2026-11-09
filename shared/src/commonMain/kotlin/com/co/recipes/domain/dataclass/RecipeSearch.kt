package com.co.recipes.domain.dataclass

private val Whitespace = Regex("\\s+")

/**
 * Everything the list search looks at: the title, the description and the ingredient names.
 *
 * That is exactly what a card shows and what the summary query already loads
 * (`RecipesApiImpl.SUMMARY_COLUMNS`), so a keystroke never costs a round trip. Steps are not
 * searched because they are detail-only columns: the list never has them.
 */
private fun Recipe.searchableParts(): List<String> = buildList {
    add(title)
    description?.takeIf { it.isNotBlank() }?.let(::add)
    ingredients.forEach { add(it.name) }
}

/** Whitespace-separated, lowercased terms; a blank query has none. */
private fun searchTerms(query: String): List<String> =
    query.lowercase().split(Whitespace).filter { it.isNotEmpty() }

/**
 * Case-insensitive substring match, so a *part* of a word is enough: `chic` and `ken` both
 * find "Chicken curry". Every term has to be present somewhere (AND), so a two-word query
 * narrows the list instead of widening it. A blank query matches every recipe.
 */
fun Recipe.matchesSearch(query: String): Boolean = matchesTerms(searchTerms(query))

private fun Recipe.matchesTerms(terms: List<String>): Boolean {
    if (terms.isEmpty()) return true

    val parts = searchableParts().map { it.lowercase() }
    return terms.all { term -> parts.any { part -> part.contains(term) } }
}

/** The list the search box renders: this list narrowed by [query]; a blank query keeps it all. */
fun List<Recipe>.filterBySearch(query: String): List<Recipe> {
    // Parsed once here instead of once per recipe: `visibleRecipes` is read on every
    // recomposition of the list.
    val terms = searchTerms(query)
    return if (terms.isEmpty()) this else filter { it.matchesTerms(terms) }
}
