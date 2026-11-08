package com.co.recipes.domain.backup

import com.co.recipes.infraestructure.dataclass.RecipeBackupCommentDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupIngredientDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupStepDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The backup file is the only thing between an admin and a lost collection, so the round
 * trip is pinned here: what is encoded must decode back unchanged, and a file from a newer
 * build must be refused instead of half-restored.
 */
class RecipeBackupCodecTest {

    private val padThai = RecipeBackupDto(
        id = "11111111-1111-1111-1111-111111111111",
        title = "Pad Thai",
        description = "Rice noodles with tamarind.",
        youtubeUrl = "https://www.youtube.com/watch?v=BWk1C0dMj8o",
        servings = 2,
        prepMinutes = 20,
        cookMinutes = 10,
        createdAt = "2026-11-01T10:00:00Z",
        ingredients = listOf(
            RecipeBackupIngredientDto(position = 0, quantity = 200.0, unit = "g", name = "rice noodles"),
            RecipeBackupIngredientDto(position = 1, quantity = 2.0, name = "eggs"),
        ),
        steps = listOf(
            RecipeBackupStepDto(stepNumber = 1, instruction = "Soak the noodles."),
            RecipeBackupStepDto(stepNumber = 2, instruction = "Stir-fry everything."),
        ),
        comments = listOf(
            RecipeBackupCommentDto(
                id = "22222222-2222-2222-2222-222222222222",
                authorId = "33333333-3333-3333-3333-333333333333",
                body = "Best with extra lime.",
                createdAt = "2026-11-02T09:00:00Z",
            ),
        ),
    )

    @Test
    fun roundTripKeepsEveryField() {
        val encoded = RecipeBackupCodec.encode(listOf(padThai), exportedAt = "2026-11-08T09:41:12Z")
        val decoded = RecipeBackupCodec.decode(encoded).getOrThrow()

        assertEquals(1, decoded.formatVersion)
        assertEquals("2026-11-08T09:41:12Z", decoded.exportedAt)
        assertEquals(listOf(padThai), decoded.recipes)
    }

    @Test
    fun theFileIsReadableAndCarriesTheColumnNames() {
        val encoded = RecipeBackupCodec.encode(listOf(padThai), exportedAt = "2026-11-08T09:41:12Z")

        // Pretty printed (it is meant to be opened in an editor) and with the table's own
        // field names, which is what lets admin_import_recipes read it without mapping.
        assertTrue(encoded.contains("\n"), "the file should be pretty printed")
        assertTrue(encoded.contains("\"formatVersion\": 1"), encoded)
        assertTrue(encoded.contains("\"title\": \"Pad Thai\""), encoded)
        assertTrue(encoded.contains("\"youtube_url\""), encoded)
        assertTrue(encoded.contains("\"prep_minutes\": 20"), encoded)
        assertTrue(encoded.contains("\"step_number\": 2"), encoded)
    }

    @Test
    fun encodedRecipesAreOrderedNewestFirst() {
        val older = padThai.copy(
            id = "44444444-4444-4444-4444-444444444444",
            title = "Older",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val newer = padThai.copy(
            id = "55555555-5555-5555-5555-555555555555",
            title = "Newer",
            createdAt = "2026-06-01T00:00:00Z",
        )

        val decoded = RecipeBackupCodec.decode(
            RecipeBackupCodec.encode(listOf(older, newer), exportedAt = "2026-11-08T00:00:00Z"),
        ).getOrThrow()

        assertEquals(listOf("Newer", "Older"), decoded.recipes.map { it.title })
    }

    @Test
    fun deletedRecipesSurviveTheRoundTrip() {
        val deleted = padThai.copy(deletedAt = "2026-11-07T20:00:00Z")

        val decoded = RecipeBackupCodec.decode(
            RecipeBackupCodec.encode(listOf(deleted), exportedAt = "2026-11-08T00:00:00Z"),
        ).getOrThrow()

        assertTrue(decoded.recipes.single().isDeleted)
        assertEquals("2026-11-07T20:00:00Z", decoded.recipes.single().deletedAt)
    }

    @Test
    fun aFutureFormatVersionIsRejectedWithAReadableMessage() {
        val encoded = RecipeBackupCodec.encode(listOf(padThai), exportedAt = "2026-11-08T00:00:00Z")
        val fromTheFuture = encoded.replace("\"formatVersion\": 1", "\"formatVersion\": 99")

        val message = RecipeBackupCodec.decode(fromTheFuture).exceptionOrNull()?.message

        assertNotNull(message)
        assertTrue(message.contains("newer version"), message)
    }

    @Test
    fun unknownKeysAreIgnoredSoAdditiveFieldsDoNotBreakOldBuilds() {
        val encoded = RecipeBackupCodec.encode(listOf(padThai), exportedAt = "2026-11-08T00:00:00Z")
        val withExtraField = encoded.replace(
            "\"title\": \"Pad Thai\",",
            "\"title\": \"Pad Thai\", \"future_field\": \"ignored\",",
        )

        val decoded = RecipeBackupCodec.decode(withExtraField).getOrThrow()
        assertEquals("Pad Thai", decoded.recipes.single().title)
    }

    @Test
    fun somethingThatIsNotABackupFailsWithAnExplanation() {
        val message = RecipeBackupCodec.decode("{\"hello\":\"world\"}").exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("not a Shared Recipes backup"), message)
    }

    @Test
    fun suggestedFileNameCarriesTheDate() {
        assertEquals("recipes-backup-2026-11-08.json", RecipeBackupCodec.suggestFileName("2026-11-08"))
        assertTrue(RecipeBackupCodec.isBackupFileName("RECIPES-BACKUP.JSON"))
    }
}
