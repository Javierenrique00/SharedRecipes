package com.co.recipes.domain.maintenance

import com.co.recipes.infraestructure.storage.StoredImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sweep is the only code in the app that deletes storage objects it did not create
 * moments earlier, so the rules are pinned here: only unreferenced objects, only inside the
 * app's folders, and never a photo young enough to belong to an in-flight save.
 */
class OrphanedImagesTest {

    private val now = 10_000_000L
    private val hour = 60L * 60L * 1000L

    private fun stored(path: String, ageMillis: Long = hour * 2) =
        StoredImage(path = path, sizeBytes = 100, createdAtEpochMillis = now - ageMillis)

    @Test
    fun onlyObjectsNoRecipeReferencesAreOrphaned() {
        val report = findOrphanedImages(
            stored = listOf(
                stored("recipes/used.jpg"),
                stored("steps/used-step.jpg"),
                stored("recipes/orphan.jpg"),
            ),
            referencedPaths = listOf("recipes/used.jpg", "steps/used-step.jpg"),
            nowMillis = now,
        )

        assertEquals(listOf("recipes/orphan.jpg"), report.paths)
        assertEquals(100, report.totalBytes)
    }

    @Test
    fun aPhotoYoungerThanTheGraceWindowIsNotSwept() {
        val report = findOrphanedImages(
            stored = listOf(
                stored("recipes/just-uploaded.jpg", ageMillis = 5_000),
                stored("recipes/old.jpg", ageMillis = hour * 3),
            ),
            referencedPaths = emptyList(),
            nowMillis = now,
        )

        assertEquals(listOf("recipes/old.jpg"), report.paths)
        assertTrue(ORPHAN_GRACE_MILLIS >= hour, "the window must cover an in-flight save")
    }

    @Test
    fun anObjectWithoutACreationTimeIsTreatedAsOld() {
        val report = findOrphanedImages(
            stored = listOf(StoredImage("recipes/unknown.jpg", 10, createdAtEpochMillis = null)),
            referencedPaths = emptyList(),
            nowMillis = now,
        )

        assertEquals(1, report.count)
    }

    @Test
    fun nothingStoredMeansNothingToClean() {
        val report = findOrphanedImages(emptyList(), emptyList(), now)
        assertTrue(report.isEmpty)
        assertEquals(0, report.totalBytes)
    }

    @Test
    fun thePreDeleteRecheckSparesReferencesCreatedAfterTheScan() {
        val scan = findOrphanedImages(
            stored = listOf(stored("recipes/a.jpg"), stored("recipes/b.jpg")),
            referencedPaths = emptyList(),
            nowMillis = now,
        )
        assertEquals(2, scan.count)

        val remaining = scan.stillOrphaned(listOf("recipes/a.jpg"))

        assertEquals(listOf("recipes/b.jpg"), remaining.paths)
    }
}
