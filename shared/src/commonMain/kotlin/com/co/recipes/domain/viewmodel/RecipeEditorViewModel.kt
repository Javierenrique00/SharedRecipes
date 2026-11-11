package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.api.readableMessage
import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.IngredientParam
import com.co.recipes.infraestructure.dataclass.StepParam
import com.co.recipes.infraestructure.files.DeviceDocumentFiles
import com.co.recipes.infraestructure.files.DocumentFileStore
import com.co.recipes.infraestructure.files.PickedImage
import com.co.recipes.infraestructure.images.normalizeToJpeg
import com.co.recipes.infraestructure.storage.RecipeImageFolder
import com.co.recipes.infraestructure.storage.RecipeImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The recipe form. Two kinds of image live here:
 *
 *  * **cover photo** — a single optional picture, shown as the list thumbnail and at the top
 *    of the recipe;
 *  * **step photos** — one optional picture per method row, carried inside [StepRow].
 *
 * Photos are normalized to JPEG the moment they are picked (see `normalizeToJpeg`), so the
 * preview, the upload and the PDF all use the same bytes. Saving uploads first and writes
 * the database row afterwards: a row is never left pointing at an object that does not
 * exist. Replacements and removals are cleaned up only *after* a successful write, so a
 * failed save never loses the picture the recipe already had.
 */
class RecipeEditorViewModel(
    private val recipesApi: RecipesApi,
    private val images: RecipeImageStore,
    private val files: DocumentFileStore = DeviceDocumentFiles,
    /**
     * Converts a picked photo to the JPEG that gets uploaded. Injectable so tests do not
     * need a real image codec (and so a different size/quality is one line away).
     */
    private val normalizeImage: suspend (ByteArray) -> ByteArray = { normalizeToJpeg(it) },
    private val recipeId: String? = null,
) : ViewModel() {

    data class IngredientRow(
        val key: Int,
        val quantity: String = "",
        val unit: String = "",
        val name: String = "",
    ) {
        /**
         * Stable key for the editor's single `LazyColumn`, which renders ingredient
         * *and* step rows. Both lists start at [key] 0, and Compose requires keys to be
         * unique across the whole lazy list, so the type prefix is mandatory — without
         * it the editor crashes with `Key "0" was already used`.
         */
        val lazyKey: String get() = "ingredient-$key"
    }

    data class StepRow(
        val key: Int,
        val instruction: String = "",
        /** Path of the photo already stored (or just uploaded), if any. */
        val imagePath: String? = null,
        /** A photo picked in this session and not uploaded yet. */
        val newImage: PickedImage? = null,
    ) {
        /** See [IngredientRow.lazyKey]: the prefix keeps both lists' keys distinct. */
        val lazyKey: String get() = "step-$key"

        val hasImage: Boolean get() = newImage != null || imagePath != null
    }

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var youtubeUrl by mutableStateOf("")
    var servings by mutableStateOf("")
    var prepMinutes by mutableStateOf("")
    var cookMinutes by mutableStateOf("")

    var coverImagePath by mutableStateOf<String?>(null)
        private set

    var coverImagePick by mutableStateOf<PickedImage?>(null)
        private set

    var ingredients by mutableStateOf(listOf(IngredientRow(key = 0)))
        private set

    var steps by mutableStateOf(listOf(StepRow(key = 0)))
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    /** True while the photo picker or the JPEG conversion is running. */
    var isPickingImage by mutableStateOf(false)
        private set

    /** "Uploading step photo 2 of 3…", shown under the save button. */
    var uploadStatus by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Set once a save succeeds, so the screen can navigate away. */
    var savedRecipeId by mutableStateOf<String?>(null)
        private set

    val isEditing: Boolean get() = recipeId != null

    private var nextKey = 1

    /** What the database had when the form opened, used to delete replaced photos. */
    private var originalCoverPath: String? = null
    private var originalStepPaths: List<String> = emptyList()

    init {
        if (recipeId != null) loadExisting()
    }

    fun clearError() {
        errorMessage = null
    }

    // -----------------------------------------------------------------------
    // Cover photo
    // -----------------------------------------------------------------------

    fun pickCoverImage() {
        if (isPickingImage) return
        viewModelScope.launch {
            isPickingImage = true
            pickPhoto()
                .onSuccess { picked ->
                    // Null = the user cancelled, which is not an error and changes nothing.
                    if (picked != null) coverImagePick = picked
                }
                .onFailure { errorMessage = it.readableMessage() }
            isPickingImage = false
        }
    }

    fun removeCoverImage() {
        coverImagePick = null
        coverImagePath = null
    }

    // -----------------------------------------------------------------------
    // Ingredient and step rows
    // -----------------------------------------------------------------------

    fun addIngredientRow() {
        ingredients = ingredients + IngredientRow(key = nextKey++)
    }

    fun updateIngredientRow(key: Int, row: IngredientRow) {
        ingredients = ingredients.map { if (it.key == key) row else it }
    }

    fun removeIngredientRow(key: Int) {
        if (ingredients.size > 1) ingredients = ingredients.filterNot { it.key == key }
    }

    fun addStepRow() {
        steps = steps + StepRow(key = nextKey++)
    }

    fun updateStepRow(key: Int, instruction: String) {
        steps = steps.map { if (it.key == key) it.copy(instruction = instruction) else it }
    }

    fun removeStepRow(key: Int) {
        if (steps.size > 1) steps = steps.filterNot { it.key == key }
    }

    fun pickStepImage(key: Int) {
        if (isPickingImage) return
        viewModelScope.launch {
            isPickingImage = true
            pickPhoto()
                .onSuccess { picked ->
                    if (picked != null) {
                        steps = steps.map {
                            if (it.key == key) it.copy(newImage = picked) else it
                        }
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }
            isPickingImage = false
        }
    }

    fun removeStepImage(key: Int) {
        steps = steps.map {
            if (it.key == key) it.copy(newImage = null, imagePath = null) else it
        }
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    fun save() {
        if (isSaving) return

        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            errorMessage = "A title is required."
            return
        }

        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            try {
                // Upload first: if this fails, the recipe keeps whatever it had before
                // instead of being saved with a path to a missing object.
                uploadPendingImages()

                val params = CreateRecipeParams(
                    title = trimmedTitle,
                    description = description.trim().ifBlank { null },
                    youtubeUrl = youtubeUrl.trim().ifBlank { null },
                    imagePath = coverImagePath,
                    servings = servings.toIntOrNull(),
                    prepMinutes = prepMinutes.toIntOrNull(),
                    cookMinutes = cookMinutes.toIntOrNull(),
                    ingredients = ingredients
                        .filter { it.name.isNotBlank() }
                        .mapIndexed { index, row ->
                            IngredientParam(
                                position = index,
                                quantity = row.quantity.replace(',', '.').toDoubleOrNull(),
                                unit = row.unit.trim().ifBlank { null },
                                name = row.name.trim(),
                            )
                        },
                    steps = steps
                        .filter { it.instruction.isNotBlank() }
                        .mapIndexed { index, row ->
                            StepParam(
                                stepNumber = index + 1,
                                instruction = row.instruction.trim(),
                                imagePath = row.imagePath,
                            )
                        },
                )

                val result = if (recipeId == null) {
                    recipesApi.createRecipe(params)
                } else {
                    recipesApi.updateRecipe(recipeId, params).map { recipeId }
                }

                result
                    .onSuccess { savedId ->
                        // Delete the photos this edit replaced *before* navigating away:
                        // the view model is cleared on popBackStack, which would cancel a
                        // fire-and-forget cleanup. Failures are ignored (leftover objects
                        // are harmless; a half-done save is not).
                        deleteReplacedImages()
                        savedRecipeId = savedId
                    }
                    .onFailure { errorMessage = it.readableMessage() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                errorMessage = error.readableMessage()
            } finally {
                isSaving = false
                uploadStatus = null
            }
        }
    }

    /**
     * Uploads the photos picked in this session, one at a time, moving each pick into its
     * row as it succeeds. A retry therefore only uploads what is still pending.
     */
    private suspend fun uploadPendingImages() {
        coverImagePick?.let { picked ->
            uploadStatus = "Uploading cover photo…"
            coverImagePath = images.upload(picked.bytes, RecipeImageFolder.COVER).getOrThrow()
            coverImagePick = null
        }

        val pending = steps.count { it.newImage != null }
        var done = 0
        steps.toList().forEach { row ->
            val picked = row.newImage ?: return@forEach
            done++
            uploadStatus = "Uploading step photo $done of $pending…"
            val path = images.upload(picked.bytes, RecipeImageFolder.STEP).getOrThrow()
            steps = steps.map {
                if (it.key == row.key) it.copy(imagePath = path, newImage = null) else it
            }
        }
    }

    /** Best effort: leftovers in the bucket are harmless, a failed save is not. */
    private suspend fun deleteReplacedImages() {
        val kept = setOfNotNull(coverImagePath, *steps.mapNotNull { it.imagePath }.toTypedArray())
        val removed = (listOfNotNull(originalCoverPath) + originalStepPaths)
            .filterNot { it in kept }
        if (removed.isEmpty()) return

        images.remove(removed)
    }

    /** Opens the picker and converts the result to JPEG, so preview == upload == PDF. */
    private suspend fun pickPhoto(): Result<PickedImage?> =
        files.pickRecipeImage().mapCatching { picked ->
            picked?.let {
                val jpeg = runCatching { normalizeImage(it.bytes) }.getOrElse {
                    throw IllegalArgumentException(
                        "That photo could not be processed. Choose a JPEG or PNG image.",
                    )
                }
                PickedImage(jpeg)
            }
        }

    private fun loadExisting() {
        val id = recipeId ?: return
        viewModelScope.launch {
            isLoading = true
            recipesApi.getRecipe(id)
                .onSuccess { dto ->
                    title = dto.title
                    description = dto.description.orEmpty()
                    youtubeUrl = dto.youtubeUrl.orEmpty()
                    servings = dto.servings?.toString().orEmpty()
                    prepMinutes = dto.prepMinutes?.toString().orEmpty()
                    cookMinutes = dto.cookMinutes?.toString().orEmpty()

                    coverImagePath = dto.imagePath
                    originalCoverPath = dto.imagePath

                    val loadedIngredients = dto.ingredients
                        .sortedBy { it.position ?: 0 }
                        .map { ingredient ->
                            IngredientRow(
                                key = nextKey++,
                                quantity = ingredient.quantity?.toString().orEmpty(),
                                unit = ingredient.unit.orEmpty(),
                                name = ingredient.name,
                            )
                        }
                    if (loadedIngredients.isNotEmpty()) ingredients = loadedIngredients

                    val loadedSteps = dto.steps
                        .sortedBy { it.stepNumber }
                        .map { step ->
                            StepRow(
                                key = nextKey++,
                                instruction = step.instruction,
                                imagePath = step.imagePath,
                            )
                        }
                    if (loadedSteps.isNotEmpty()) {
                        steps = loadedSteps
                        originalStepPaths = loadedSteps.mapNotNull { it.imagePath }
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }
}
