package com.co.recipes.domain.viewmodel

import com.co.recipes.domain.backup.RecipeBackupCodec
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.files.SaveResult
import com.co.recipes.testutil.FakeDocumentFiles
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
 * The admin console moves real data, so its state machine is pinned here: which recipes are
 * offered for restore, what a backup file contains, and what a clear actually asks the
 * server to do (soft unless the admin explicitly chose otherwise).
 */
class AdminRecipesViewModelTest {

    private val active = RecipeBackupDto(
        id = "11111111-1111-1111-1111-111111111111",
        title = "Pad Thai",
        createdAt = "2026-11-01T10:00:00Z",
    )

    private val deleted = RecipeBackupDto(
        id = "22222222-2222-2222-2222-222222222222",
        title = "Pho",
        createdAt = "2026-10-01T10:00:00Z",
        deletedAt = "2026-11-07T20:00:00Z",
    )

    private fun backend(vararg recipes: RecipeBackupDto) = FakeRecipesApi(backup = recipes.toList())

    @Test
    fun loadingSplitsActiveFromDeleted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AdminRecipesViewModel(backend(active, deleted))
            viewModel.load()
            advanceUntilIdle()

            assertEquals(2, viewModel.recipes.size)
            assertEquals(listOf("Pad Thai"), viewModel.activeRecipes.map { it.title })
            assertEquals(listOf("Pho"), viewModel.deletedRecipes.map { it.title })
            assertTrue(viewModel.hasDeleted)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exportingWritesEverythingIncludingDeletedRecipes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = backend(active, deleted)
            val files = FakeDocumentFiles()
            val viewModel = AdminRecipesViewModel(
                recipesApi = api,
                dateProvider = { "2026-11-08" },
                timestampProvider = { "2026-11-08T09:41:12Z" },
                files = files,
            )

            viewModel.exportAll()
            advanceUntilIdle()

            assertEquals("recipes-backup-2026-11-08.json", files.savedFileName)

            val file = RecipeBackupCodec.decode(files.savedContent.orEmpty()).getOrThrow()
            assertEquals(2, file.recipes.size)
            assertEquals("2026-11-08T09:41:12Z", file.exportedAt)
            assertTrue(file.recipes.any { it.isDeleted }, "a backup must keep deleted recipes")
            assertNotNull(viewModel.statusMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pickingABackupPreviewsItAllSelectedAndFlagsExistingRecipes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val incoming = RecipeBackupDto(
                id = "33333333-3333-3333-3333-333333333333",
                title = "Ramen",
            )
            val backupText = RecipeBackupCodec.encode(
                listOf(active, incoming),
                exportedAt = "2026-11-08T09:41:12Z",
            )

            val viewModel = AdminRecipesViewModel(
                recipesApi = backend(active),
                files = FakeDocumentFiles(fileToOpen = backupText, openedName = "my-backup.json"),
            )
            viewModel.load()
            advanceUntilIdle()

            viewModel.pickBackupFile()
            advanceUntilIdle()

            val preview = viewModel.importPreview
            assertNotNull(preview)
            assertEquals("my-backup.json", preview.fileName)
            assertEquals(2, preview.selectedCount, "everything is preselected")
            assertTrue(preview.isExisting(0), "Pad Thai already exists, so it would be updated")
            assertTrue(!preview.isExisting(1), "Ramen is new")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun onlyTheTickedRecipesAreRestored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val incoming = RecipeBackupDto(title = "Ramen")
            val backupText = RecipeBackupCodec.encode(listOf(active, incoming), exportedAt = "now")
            val api = backend(active)
            val viewModel = AdminRecipesViewModel(
                recipesApi = api,
                files = FakeDocumentFiles(fileToOpen = backupText),
            )
            viewModel.load()
            advanceUntilIdle()

            viewModel.pickBackupFile()
            advanceUntilIdle()
            viewModel.toggleImportRecipe(0) // drop Pad Thai
            viewModel.restoreImported()
            advanceUntilIdle()

            assertEquals(listOf("Ramen"), api.imported.map { it.title })
            assertNull(viewModel.importPreview, "the preview closes once it has been restored")
            assertTrue(viewModel.statusMessage.orEmpty().contains("Restored 1"), viewModel.statusMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restoringDeletedRecipesAsksForTheirIds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi(backup = listOf(active, deleted), restoreCount = 1)
            val viewModel = AdminRecipesViewModel(recipesApi = api)
            viewModel.load()
            advanceUntilIdle()

            viewModel.restoreDeleted(viewModel.deletedRecipes)
            advanceUntilIdle()

            assertEquals(listOf("22222222-2222-2222-2222-222222222222"), api.restoredIds)
            assertEquals("1 recipes restored", viewModel.statusMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearingIsSoftUnlessAskedOtherwise() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi(backup = listOf(active), clearCount = 1)
            val viewModel = AdminRecipesViewModel(recipesApi = api)

            viewModel.clear(AdminRecipesViewModel.ClearMode.SOFT)
            advanceUntilIdle()
            viewModel.clear(AdminRecipesViewModel.ClearMode.HARD)
            advanceUntilIdle()

            assertEquals(listOf(false, true), api.clearCalls)
            assertTrue(
                viewModel.statusMessage.orEmpty().contains("permanently"),
                viewModel.statusMessage,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFileThatIsNotABackupIsReportedAndLeavesNothingToRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AdminRecipesViewModel(
                recipesApi = backend(active),
                files = FakeDocumentFiles(fileToOpen = "<html>not json</html>"),
            )

            viewModel.pickBackupFile()
            advanceUntilIdle()

            assertNull(viewModel.importPreview)
            assertTrue(
                viewModel.errorMessage.orEmpty().contains("not a Shared Recipes backup"),
                viewModel.errorMessage,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellingTheFilePickerQuietlyDoesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AdminRecipesViewModel(
                recipesApi = backend(active),
                files = FakeDocumentFiles(fileToOpen = null),
            )

            viewModel.pickBackupFile()
            advanceUntilIdle()

            assertNull(viewModel.importPreview)
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedSaveKeepsTheErrorAndReportsNoSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AdminRecipesViewModel(
                recipesApi = backend(active),
                files = FakeDocumentFiles(saveResult = SaveResult.Failed("disk full")),
            )

            viewModel.exportAll()
            advanceUntilIdle()

            assertEquals("disk full", viewModel.errorMessage)
            assertNull(viewModel.statusMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importSummaryIsReadableInTheStatusLine() {
        val summary = ImportSummary(inserted = 2, updated = 1, restored = 3, skipped = 4)
        assertEquals("2 added, 1 updated, 3 restored, 4 skipped", summary.describe())
        assertEquals(6, summary.total)
    }
}
