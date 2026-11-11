package com.co.recipes.domain.viewmodel

import com.co.recipes.infraestructure.dataclass.RecipeDto
import com.co.recipes.infraestructure.dataclass.StepDto
import com.co.recipes.infraestructure.files.PickedImage
import com.co.recipes.testutil.FakeDocumentFiles
import com.co.recipes.testutil.FakeRecipeImageStore
import com.co.recipes.testutil.FakeRecipesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The editor's photo contract:
 *
 *  * uploads happen **before** the row is written, so a recipe never references a missing
 *    object;
 *  * a failed upload leaves the form untouched and the recipe unwritten;
 *  * photos the admin did not touch survive an edit (the RPC replaces steps wholesale);
 *  * replaced/removed photos are deleted only after a successful save.
 */
class RecipeEditorViewModelTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    /** Identity "normalizer": the real one needs an image codec, which this test does not. */
    private val noNormalization: suspend (ByteArray) -> ByteArray = { it }

    private fun existingRecipe() = RecipeDto(
        id = "r1",
        title = "Old title",
        imagePath = "recipes/old.jpg",
        steps = listOf(
            StepDto(stepNumber = 1, instruction = "Keep this", imagePath = "steps/old.jpg"),
        ),
    )

    @Test
    fun savingUploadsPhotosBeforeWritingTheRecipe() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi().apply { createResult = Result.success("new-id") }
            val images = FakeRecipeImageStore()
            val viewModel = RecipeEditorViewModel(
                recipesApi = api,
                images = images,
                files = FakeDocumentFiles(imageToPick = PickedImage(jpeg)),
                normalizeImage = noNormalization,
            )

            viewModel.title = "Pad Thai"
            viewModel.pickCoverImage()
            advanceUntilIdle()

            val stepKey = viewModel.steps.single().key
            viewModel.updateStepRow(stepKey, "Stir the noodles")
            viewModel.pickStepImage(stepKey)
            advanceUntilIdle()

            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf("cover/test-0.jpg", "step/test-1.jpg"), images.uploadedPaths)

            val params = assertNotNull(api.createdParams)
            assertEquals("Pad Thai", params.title)
            assertEquals("cover/test-0.jpg", params.imagePath)
            assertEquals("step/test-1.jpg", params.steps.single().imagePath)
            assertEquals("new-id", viewModel.savedRecipeId)
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedUploadLeavesTheRecipeUnwrittenAndThePickInPlace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi().apply { createResult = Result.success("new-id") }
            val images = FakeRecipeImageStore().apply {
                failUploadsWith = IllegalStateException("network down")
            }
            val viewModel = RecipeEditorViewModel(
                recipesApi = api,
                images = images,
                files = FakeDocumentFiles(imageToPick = PickedImage(jpeg)),
                normalizeImage = noNormalization,
            )

            viewModel.title = "Pad Thai"
            viewModel.pickCoverImage()
            advanceUntilIdle()
            viewModel.save()
            advanceUntilIdle()

            assertNull(api.createdParams, "the recipe must not be written without its photo")
            assertNull(viewModel.savedRecipeId)
            assertEquals("network down", viewModel.errorMessage)
            assertNotNull(viewModel.coverImagePick, "the pick stays so Save can be retried")
            assertTrue(images.uploadedPaths.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun editingKeepsPhotosThatWereNotTouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi(detail = existingRecipe()).apply {
                updateResult = Result.success(Unit)
            }
            val images = FakeRecipeImageStore()
            val viewModel = RecipeEditorViewModel(
                recipesApi = api,
                images = images,
                files = FakeDocumentFiles(),
                normalizeImage = noNormalization,
                recipeId = "r1",
            )
            advanceUntilIdle() // loadExisting()

            viewModel.save()
            advanceUntilIdle()

            val params = assertNotNull(api.updatedParams)
            assertEquals("recipes/old.jpg", params.imagePath)
            assertEquals("steps/old.jpg", params.steps.single().imagePath)
            assertTrue(images.uploadedPaths.isEmpty(), "nothing was replaced, so nothing is uploaded")
            assertTrue(images.removedPaths.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aRemovedPhotoIsDeletedOnlyAfterASuccessfulSave() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi(detail = existingRecipe()).apply {
                updateResult = Result.success(Unit)
            }
            val images = FakeRecipeImageStore()
            val viewModel = RecipeEditorViewModel(
                recipesApi = api,
                images = images,
                files = FakeDocumentFiles(),
                normalizeImage = noNormalization,
                recipeId = "r1",
            )
            advanceUntilIdle()

            viewModel.removeCoverImage()
            viewModel.save()
            advanceUntilIdle()

            assertNull(api.updatedParams?.imagePath)
            assertEquals(listOf("recipes/old.jpg"), images.removedPaths)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
