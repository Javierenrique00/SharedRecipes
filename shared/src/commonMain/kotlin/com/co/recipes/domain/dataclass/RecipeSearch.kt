package com.co.recipes.domain.dataclass

private val Whitespace = Regex("\\s+")

/** Suggestions offered at once: more than this buries the cards the search is about. */
private const val MaxSuggestions = 8

/** One-letter words ("a", "I") would match half the collection, so they are never offered. */
private const val MinWordLength = 2

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

// ---------------------------------------------------------------------------------------
// Suggestions (the menu under the search field)
// ---------------------------------------------------------------------------------------

/**
 * The word being typed: everything after the last space.
 *
 * Empty when the query is empty or ends in a space — the user finished a word and has not
 * started the next one, which is exactly what keeps the suggestion menu closed between words.
 */
fun currentSearchTerm(query: String): String {
    if (query.isEmpty() || query.last().isWhitespace()) return ""

    val cut = query.indexOfLast { it.isWhitespace() }
    return if (cut < 0) query else query.substring(cut + 1)
}

/**
 * [text] split into words. Punctuation separates words instead of being part of one, so
 * "Slow-cooked" offers `Slow` and `cooked`.
 *
 * `Char.isLetterOrDigit` rather than a `\p{L}` regex on purpose: Kotlin/JS and Kotlin/Wasm
 * run on a JavaScript engine where Unicode property escapes need a flag this code cannot set.
 */
private fun wordsIn(text: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.length >= MinWordLength) words += current.toString()
        current.clear()
    }

    text.forEach { char ->
        if (char.isLetterOrDigit()) current.append(char) else flush()
    }
    flush()
    return words
}

/**
 * Every distinct word the search can match, in the order the collection first uses it.
 *
 * Built from the same three fields [matchesSearch] looks at, so a suggestion can never offer
 * a word that returns nothing. Deduped case-insensitively while keeping the first spelling,
 * which is what makes an entry read "Chicken" instead of "chicken".
 */
fun List<Recipe>.searchVocabulary(): List<String> {
    val seen = mutableSetOf<String>()

    return buildList {
        this@searchVocabulary.forEach { recipe ->
            recipe.searchableParts().forEach { part ->
                wordsIn(part).forEach { word ->
                    if (seen.add(word.lowercase())) add(word)
                }
            }
        }
    }
}

/**
 * Words that complete the word being typed, best first.
 *
 * The pool is narrowed to the recipes the *earlier* terms already matched, so accepting a
 * suggestion cannot empty the list: with `beef cur` no chicken ingredient is offered. While
 * the first word is being typed there are no earlier terms, so the whole collection is used.
 *
 * Prefix matches come first — that is what completing a word usually means — then the words
 * that merely contain the term, so `urry` still offers `curry`. Each group is alphabetical
 * and the whole list is capped at [limit].
 */
fun List<Recipe>.searchSuggestions(query: String, limit: Int = MaxSuggestions): List<String> {
    val term = currentSearchTerm(query)
    if (term.isEmpty()) return emptyList()

    val needle = term.lowercase()
    // `term` is a slice of `query`, so dropping exactly its length leaves the earlier terms
    // untouched — no re-splitting, no whitespace normalisation.
    val pool = filterBySearch(query.dropLast(term.length)).searchVocabulary()

    val startsWith = mutableListOf<String>()
    val contains = mutableListOf<String>()

    pool.forEach { word ->
        val lower = word.lowercase()
        when {
            // Already typed: there is nothing left to complete.
            lower == needle -> Unit

            lower.startsWith(needle) -> startsWith += word
            lower.contains(needle) -> contains += word
        }
    }

    return (startsWith.sortedBy { it.lowercase() } + contains.sortedBy { it.lowercase() }).take(limit)
}

/**
 * Puts [word] where the word being typed was, leaving a trailing space so the next word can
 * follow straight away: `chic cur` + `curry` → `chic curry `.
 *
 * Only the last word is replaced: the earlier terms are the user's, and completing a word is
 * not a reason to throw them away.
 */
fun completeSearchQuery(query: String, word: String): String {
    val term = currentSearchTerm(query)
    return query.dropLast(term.length) + word + " "
}

