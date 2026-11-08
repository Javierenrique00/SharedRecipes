package com.co.recipes.domain.export

import com.co.recipes.domain.dataclass.Comment
import com.co.recipes.domain.dataclass.Ingredient
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Markdown export is generated in common code, so it has to be exactly right here: the same
 * string is what every platform writes to disk.
 */
class RecipeMarkdownTest {

    private fun recipe(
        title: String = "Pad Thai",
        description: String? = "Rice noodles with tamarind.",
        steps: List<Step> = listOf(
            Step(number = 1, instruction = "Soak the noodles."),
            Step(number = 2, instruction = "Stir-fry everything."),
        ),
    ) = Recipe(
        id = "1",
        title = title,
        description = description,
        youtubeId = "BWk1C0dMj8o",
        servings = 2,
        prepMinutes = 20,
        cookMinutes = 10,
        ingredients = listOf(
            Ingredient(quantity = 200.0, unit = "g", name = "flat rice noodles"),
            Ingredient(quantity = 2.0, unit = null, name = "eggs"),
        ),
        steps = steps,
        comments = listOf(
            Comment(id = "c1", authorId = "u1", body = "Secret comment", createdAt = null),
        ),
    )

    @Test
    fun headingsMetaAndListsArePresent() {
        val markdown = recipe().toMarkdown(exportedAt = "2026-11-08 09:41")

        assertTrue(markdown.startsWith("# Pad Thai\n"), markdown)
        assertTrue(markdown.contains("## Ingredients"), markdown)
        assertTrue(markdown.contains("## Method"), markdown)
        assertTrue(markdown.contains("- **Servings:** 2"), markdown)
        assertTrue(markdown.contains("- **Total:** 30 min"), markdown)
        assertTrue(markdown.contains("https://www.youtube.com/watch?v=BWk1C0dMj8o"), markdown)
        assertTrue(markdown.contains("*Exported from Shared Recipes on 2026-11-08 09:41*"), markdown)
    }

    @Test
    fun everyIngredientAndStepIsWrittenInOrder() {
        val markdown = recipe().toMarkdown(exportedAt = "2026-11-08 09:41")

        assertTrue(markdown.contains("- **200 g** flat rice noodles"), markdown)
        assertTrue(markdown.contains("- **2** eggs"), markdown)
        assertTrue(markdown.contains("1. Soak the noodles."), markdown)
        assertTrue(markdown.indexOf("1. Soak") < markdown.indexOf("2. Stir-fry"))
    }

    @Test
    fun commentsAreNotPartOfTheExportedDocument() {
        assertFalse(recipe().toMarkdown("2026-11-08 09:41").contains("Secret comment"))
    }

    @Test
    fun missingOptionalValuesAreOmitted() {
        val bare = Recipe(
            id = "2",
            title = "Toast",
            description = null,
            youtubeId = null,
            servings = null,
            prepMinutes = null,
            cookMinutes = null,
            ingredients = listOf(Ingredient(quantity = null, unit = null, name = "bread")),
            steps = emptyList(),
            comments = emptyList(),
        )

        val markdown = bare.toMarkdown("2026-11-08 09:41")

        assertFalse(markdown.contains("**Servings:**"), markdown)
        assertFalse(markdown.contains("## Method"), markdown)
        assertTrue(markdown.contains("- bread"), markdown)
    }

    @Test
    fun aTitleFullOfMarkupCannotBreakTheDocument() {
        val markdown = recipe(title = "*Best* _noodles_ #1").toMarkdown("2026-11-08 09:41")

        assertEquals(1, markdown.lines().count { it.startsWith("# ") })
        assertTrue(markdown.contains("\\*Best\\*"), markdown)
        // A `#` in the middle of a line is literal in CommonMark, so only a *leading*
        // marker has to be escaped — the document still has exactly one heading.
        assertTrue(markdown.startsWith("# "), markdown)
    }

    @Test
    fun aTitleStartingWithABlockMarkerIsEscaped() {
        assertEquals("\\- dash", escapeMarkdownLine("- dash"))
        // The period is what must be escaped; `\1` would not stop a list from starting.
        assertEquals("1\\. numbered", escapeMarkdownLine("1. numbered"))
        assertEquals("plain", escapeMarkdownLine("plain"))
    }

    @Test
    fun multiLineInstructionsStayInsideTheirListItem() {
        val markdown = recipe(
            steps = listOf(Step(number = 1, instruction = "First line.\nSecond line.")),
        ).toMarkdown("2026-11-08 09:41")

        assertTrue(markdown.contains("1. First line.\n   Second line."), markdown)
    }
}
