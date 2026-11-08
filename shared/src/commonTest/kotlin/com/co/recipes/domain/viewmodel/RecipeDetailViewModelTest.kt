package com.co.recipes.domain.viewmodel

import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.export.ExportFormat
import com.co.recipes.domain.export.ExportResult
import com.co.recipes.domain.export.RecipeExporter
import com.co.recipes.infraestructure.dataclass.IngredientDto
import com.co.recipes.infraestructure.dataclass.RecipeDto
import com.co.recipes.infraestructure.dataclass.StepDto
import com.co.recipes.testutil.FakeRecipesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records what the screen asked for; the document itself is covered elsewhere. */
private class RecordingExporter(
    private val result: ExportResult = ExportResult.Saved("pad-thai.md"),
) : RecipeExporter {

    val calls = mutableListOf<Pair<String, ExportFormat>>()

    override suspend fun export(recipe: Recipe, format: ExportFormat): ExportResult {
        calls += recipe.title to format
        return result
    }
}

/**
 * The export action is available to every signed-in user (not just admins), so all that is
 * pinned here is that the format the user picked is the one handed to the exporter, and that
 * the outcome reaches the UI.
 */
class RecipeDetailViewModelTest {

    private val dto = RecipeDto(
        id = "1",
        title = "Pad Thai",
        prepMinutes = 20,
        cookMinutes = 10,
        ingredients = listOf(IngredientDto(position = 0, quantity = 200.0, unit = "g", name = "noodles")),
        steps = listOf(StepDto(stepNumber = 1, instruction = "Soak")),
    )

    @Test
    fun theChosenFormatReachesTheExporter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val exporter = RecordingExporter()
            val viewModel = RecipeDetailViewModel(FakeRecipesApi(detail = dto), "1", exporter)
            viewModel.load()
            advanceUntilIdle()

            viewModel.export(ExportFormat.MARKDOWN)
            advanceUntilIdle()
            viewModel.export(ExportFormat.PDF)
            advanceUntilIdle()

            assertEquals(
                listOf("Pad Thai" to ExportFormat.MARKDOWN, "Pad Thai" to ExportFormat.PDF),
                exporter.calls,
            )
            assertEquals("Saved pad-thai.md", viewModel.exportMessage)
            assertTrue(!viewModel.isExporting)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aCancelledExportSaysNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = RecipeDetailViewModel(
                recipesApi = FakeRecipesApi(detail = dto),
                recipeId = "1",
                exporter = RecordingExporter(result = ExportResult.Cancelled),
            )
            viewModel.load()
            advanceUntilIdle()

            viewModel.export(ExportFormat.PDF)
            advanceUntilIdle()

            assertNull(viewModel.exportMessage)
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedExportIsReportedAsAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = RecipeDetailViewModel(
                recipesApi = FakeRecipesApi(detail = dto),
                recipeId = "1",
                exporter = RecordingExporter(result = ExportResult.Failed("no room left")),
            )
            viewModel.load()
            advanceUntilIdle()

            viewModel.export(ExportFormat.PDF)
            advanceUntilIdle()

            assertEquals("no room left", viewModel.errorMessage)
            assertNull(viewModel.exportMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
