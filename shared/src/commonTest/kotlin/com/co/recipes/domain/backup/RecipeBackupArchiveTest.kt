package com.co.recipes.domain.backup

import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupStepDto
import com.co.recipes.infraestructure.zip.encodeZip
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The `.zip` half of the backup: a JSON manifest plus the photos it references, all in
 * memory so the same code runs on Android, iOS, desktop and the browser.
 */
class RecipeBackupArchiveTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    private val manifest = RecipeBackupCodec.encode(
        listOf(
            RecipeBackupDto(
                id = "11111111-1111-1111-1111-111111111111",
                title = "Pad Thai",
                imagePath = "recipes/a.jpg",
                steps = listOf(
                    RecipeBackupStepDto(1, "Soak the noodles.", "steps/b.jpg"),
                ),
            ),
        ),
        exportedAt = "2026-11-11T10:00:00Z",
    )

    @Test
    fun roundTripKeepsTheManifestAndThePhotos() {
        val archive = RecipeBackupArchive.encode(
            manifest,
            images = mapOf("recipes/a.jpg" to jpeg, "steps/b.jpg" to jpeg),
        )

        assertTrue(RecipeBackupArchive.isZip(archive), "the archive starts with PK\\x03\\x04")

        val decoded = RecipeBackupArchive.decode(archive).getOrThrow()
        val file = RecipeBackupCodec.decode(decoded.manifest).getOrThrow()

        assertEquals("Pad Thai", file.recipes.single().title)
        assertEquals(setOf("recipes/a.jpg", "steps/b.jpg"), decoded.images.keys)
        assertContentEquals(jpeg, decoded.images.getValue("recipes/a.jpg"))
    }

    @Test
    fun plainJsonIsNotMistakenForAnArchive() {
        assertFalse(RecipeBackupArchive.isZip(manifest.encodeToByteArray()))
    }

    @Test
    fun anArchiveWithoutTheManifestExplainsWhatIsMissing() {
        val archive = encodeZip(mapOf("images/recipes/a.jpg" to jpeg))

        val message = RecipeBackupArchive.decode(archive).exceptionOrNull()?.message.orEmpty()

        assertTrue(message.contains(RecipeBackupArchive.MANIFEST_ENTRY), message)
        assertTrue(message.contains("JSON"), message)
    }

    @Test
    fun anImagePathThatEscapesTheBucketIsRejected() {
        val archive = encodeZip(
            mapOf(
                RecipeBackupArchive.MANIFEST_ENTRY to manifest.encodeToByteArray(),
                "images/../../evil.jpg" to jpeg,
            ),
        )

        assertTrue(RecipeBackupArchive.decode(archive).isFailure)
    }

    @Test
    fun unrelatedEntriesFromAnOperatingSystemZipAreIgnored() {
        val archive = encodeZip(
            mapOf(
                ".DS_Store" to byteArrayOf(1, 2, 3),
                "__MACOSX/._recipes-backup.json" to byteArrayOf(4),
                RecipeBackupArchive.MANIFEST_ENTRY to manifest.encodeToByteArray(),
            ),
        )

        val decoded = RecipeBackupArchive.decode(archive).getOrThrow()

        assertTrue(decoded.images.isEmpty())
        assertEquals("Pad Thai", RecipeBackupCodec.decode(decoded.manifest).getOrThrow().recipes.single().title)
    }
}
