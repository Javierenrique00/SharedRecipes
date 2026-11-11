package com.co.recipes.infraestructure.storage

import com.co.recipes.infraestructure.api.apiCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Which folder a new object goes into. Purely cosmetic: the DB stores the full path. */
enum class RecipeImageFolder(internal val folder: String) {
    COVER(RecipeImages.COVER_FOLDER),
    STEP(RecipeImages.STEP_FOLDER),
}

/** One object found in the bucket during a maintenance scan. */
data class StoredImage(
    /** Full object path inside the bucket, e.g. `recipes/4b1e….jpg`. */
    val path: String,
    val sizeBytes: Long,
    /** When Storage says the object was created; null when it does not report it. */
    val createdAtEpochMillis: Long?,
)

/**
 * Uploading, downloading and deleting recipe photos.
 *
 * An interface so the view models can be tested with an in-memory double instead of a
 * network — the same reason [com.co.recipes.infraestructure.files.DocumentFileStore] is one.
 */
interface RecipeImageStore {

    /** Uploads [bytes] as a JPEG and returns the new object path. */
    suspend fun upload(bytes: ByteArray, folder: RecipeImageFolder): Result<String>

    /**
     * Writes [bytes] to an exact existing path, overwriting it. Used by a backup restore,
     * where the database rows already reference the object name recorded in the JSON.
     */
    suspend fun uploadTo(path: String, bytes: ByteArray): Result<Unit>

    /** Reads an object back (used by the exports and by the backup archive). */
    suspend fun download(path: String): Result<ByteArray>

    /** Best-effort cleanup of replaced or removed photos. */
    suspend fun remove(paths: Collection<String>): Result<Unit>

    /**
     * Every object the app owns, i.e. inside `recipes/` and `steps/`.
     *
     * Used by the admin "orphaned photos" sweep. Anything outside those two folders is
     * deliberately ignored, so a stray file someone drops in the bucket is never touched.
     */
    suspend fun listAll(): Result<List<StoredImage>>
}

/** The real thing, backed by the `recipe-images` bucket. */
class SupabaseRecipeImageStore(private val client: SupabaseClient) : RecipeImageStore {

    private val bucket get() = client.storage.from(RecipeImages.BUCKET)

    override suspend fun upload(bytes: ByteArray, folder: RecipeImageFolder): Result<String> =
        apiCall {
            val path = when (folder) {
                RecipeImageFolder.COVER -> RecipeImages.newCoverPath()
                RecipeImageFolder.STEP -> RecipeImages.newStepPath()
            }
            bucket.upload(path, bytes) {
                // Paths are unique, so an upload never overwrites an existing object.
                upsert = false
                contentType = ContentType.Image.JPEG
            }
            path
        }

    override suspend fun uploadTo(path: String, bytes: ByteArray): Result<Unit> = apiCall {
        bucket.upload(path, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        Unit
    }

    override suspend fun download(path: String): Result<ByteArray> = apiCall {
        // The bucket is public for reads (migration 10), so no auth header is needed.
        bucket.downloadPublic(path)
    }

    override suspend fun remove(paths: Collection<String>): Result<Unit> = apiCall {
        // Batched: a sweep can find hundreds of objects and the storage API takes a list
        // per request. A partial failure leaves the rest for the next run.
        paths.toList().chunked(DELETE_BATCH_SIZE).forEach { batch ->
            bucket.delete(batch)
        }
        Unit
    }

    override suspend fun listAll(): Result<List<StoredImage>> = apiCall {
        val found = mutableListOf<StoredImage>()

        MAINTENANCE_FOLDERS.forEach { folder ->
            var position = 0
            while (true) {
                val page = bucket.list(prefix = folder, filter = {
                    limit = LIST_PAGE_SIZE
                    this.offset = position
                })

                page.forEach { entry ->
                    // Folder placeholders only carry a name; ours are flat, so skip them.
                    if (entry.id == null) return@forEach

                    val path = if (entry.name.startsWith("$folder/")) {
                        entry.name
                    } else {
                        // The Storage API returns names relative to the prefix.
                        "$folder/${entry.name}"
                    }

                    found += StoredImage(
                        path = path,
                        sizeBytes = entry.metadata?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
                        createdAtEpochMillis = entry.createdAt?.toEpochMilliseconds(),
                    )
                }

                if (page.size < LIST_PAGE_SIZE) break
                position += page.size
                check(position <= MAX_LISTED_OBJECTS) {
                    "The bucket holds more than $MAX_LISTED_OBJECTS photos; refusing to scan."
                }
            }
        }

        found
    }

    private companion object {
        const val LIST_PAGE_SIZE = 100
        const val MAX_LISTED_OBJECTS = 10_000
        const val DELETE_BATCH_SIZE = 100

        /** The only folders the app writes to (see RecipeImages). */
        val MAINTENANCE_FOLDERS = listOf(RecipeImages.COVER_FOLDER, RecipeImages.STEP_FOLDER)
    }
}
