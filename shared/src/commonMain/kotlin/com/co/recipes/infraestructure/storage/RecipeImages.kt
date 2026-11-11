package com.co.recipes.infraestructure.storage

import com.co.recipes.infraestructure.SupabaseConfig
import kotlin.random.Random

/**
 * Where recipe photos live and how their URLs are built.
 *
 * The database stores only the *object path* (`recipes/<hex>.jpg`), never a full URL, so
 * pointing a build at another Supabase project does not require rewriting rows. The path
 * is what [publicUrl] turns into the URL Coil loads.
 *
 * Names are random and unique per upload: replacing a photo stores it under a new name, so
 * the URL changes and Coil's cache can never serve the old picture. That is also why the
 * old object is deleted after a successful save (see `RecipeEditorViewModel`).
 */
object RecipeImages {

    /** The bucket created by migration 10. Public read, admin write, JPEG only. */
    const val BUCKET = "recipe-images"

    /** Folders inside the bucket, so covers and step photos are easy to tell apart. */
    const val COVER_FOLDER = "recipes"
    const val STEP_FOLDER = "steps"

    /** Public URL for a stored object, or null when the recipe has no image. */
    fun publicUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }
            ?.let { "${SupabaseConfig.URL}/storage/v1/object/public/$BUCKET/$it" }

    fun newCoverPath(): String = "$COVER_FOLDER/${randomName()}.jpg"

    fun newStepPath(): String = "$STEP_FOLDER/${randomName()}.jpg"

    /** 16 random bytes as hex: unguessable, short, and safe in a URL and a zip entry. */
    private fun randomName(): String =
        Random.nextBytes(16).joinToString("") { byte ->
            byte.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
}
