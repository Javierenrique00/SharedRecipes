package com.co.recipes.domain.maintenance

import com.co.recipes.infraestructure.storage.StoredImage

/**
 * How long a photo must exist before a sweep may delete it.
 *
 * Uploads are written *before* the recipe row that references them, so for a few seconds a
 * brand-new object legitimately has no reference. Skipping fresh objects keeps a
 * maintenance run from deleting an upload that is still in flight on another device.
 * A photo orphaned by an edit simply shows up on the next scan.
 */
const val ORPHAN_GRACE_MILLIS: Long = 60L * 60L * 1000L

/** What a maintenance scan found in the photo bucket. */
data class OrphanReport(val images: List<StoredImage>) {

    val count: Int get() = images.size

    val totalBytes: Long get() = images.sumOf { it.sizeBytes }

    val paths: List<String> get() = images.map { it.path }

    val isEmpty: Boolean get() = images.isEmpty()
}

/**
 * Objects nothing in the database points at.
 *
 * @param stored everything the bucket holds (the app's two folders only).
 * @param referencedPaths every `image_path` a recipe or step still uses, **soft-deleted
 *   recipes included** — those can be restored, so their photos are not garbage.
 * @param nowMillis current time, injected so the grace window is testable.
 */
fun findOrphanedImages(
    stored: List<StoredImage>,
    referencedPaths: Collection<String>,
    nowMillis: Long,
    graceMillis: Long = ORPHAN_GRACE_MILLIS,
): OrphanReport {
    val referenced = referencedPaths.toSet()
    return OrphanReport(
        stored.filter { image ->
            image.path !in referenced && image.isOlderThan(nowMillis, graceMillis)
        },
    )
}

/**
 * Re-checks a scan against a fresh reference list, immediately before deleting.
 *
 * The admin may have restored or created a recipe between the scan and the confirmation;
 * this closes that window without re-listing the bucket.
 */
fun OrphanReport.stillOrphaned(referencedPaths: Collection<String>): OrphanReport {
    val referenced = referencedPaths.toSet()
    return OrphanReport(images.filterNot { it.path in referenced })
}

private fun StoredImage.isOlderThan(nowMillis: Long, graceMillis: Long): Boolean {
    // No creation time reported: treat it as old. The reference check is the real
    // protection; the grace window only exists for uploads that are seconds old.
    val created = createdAtEpochMillis ?: return true
    return nowMillis - created >= graceMillis
}
