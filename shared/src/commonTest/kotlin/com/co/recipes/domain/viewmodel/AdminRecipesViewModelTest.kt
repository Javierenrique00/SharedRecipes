package com.co.recipes.domain.viewmodel

import com.co.recipes.domain.backup.RecipeBackupArchive
import com.co.recipes.domain.backup.RecipeBackupCodec
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupStepDto
import com.co.recipes.infraestructure.files.SaveResult
import com.co.recipes.infraestructure.storage.StoredImage
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
            val viewModel = AdminRecipesViewModel(backend(active, deleted), FakeRecipeImageStore())
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
                images = FakeRecipeImageStore(),
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
                images = FakeRecipeImageStore(),
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
                images = FakeRecipeImageStore(),
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
            val viewModel = AdminRecipesViewModel(recipesApi = api, images = FakeRecipeImageStore())
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
            val viewModel = AdminRecipesViewModel(recipesApi = api, images = FakeRecipeImageStore())

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
                images = FakeRecipeImageStore(),
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
                images = FakeRecipeImageStore(),
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
                images = FakeRecipeImageStore(),
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

    @Test
    fun exportingWithPhotosWritesAZipThatCarriesThem() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val photo = byteArrayOf(1, 2, 3)
            val withPhotos = active.copy(
                imagePath = "recipes/a.jpg",
                steps = listOf(RecipeBackupStepDto(1, "Stir.", "steps/b.jpg")),
            )
            val files = FakeDocumentFiles()
            val images = FakeRecipeImageStore(
                mapOf("recipes/a.jpg" to photo, "steps/b.jpg" to photo),
            )
            val viewModel = AdminRecipesViewModel(
                recipesApi = FakeRecipesApi(backup = listOf(withPhotos)),
                images = images,
                dateProvider = { "2026-11-11" },
                timestampProvider = { "2026-11-11T10:00:00Z" },
                files = files,
            )

            viewModel.exportAll()
            advanceUntilIdle()

            assertEquals("recipes-backup-2026-11-11.zip", files.savedFileName)
            val content = RecipeBackupArchive.decode(files.savedBytes!!).getOrThrow()
            assertEquals(setOf("recipes/a.jpg", "steps/b.jpg"), content.images.keys)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restoringAZipReuploadsItsPhotosToTheRecordedPaths() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val photo = byteArrayOf(9, 9)
            val incoming = RecipeBackupDto(
                id = "66666666-6666-6666-6666-666666666666",
                title = "Ramen",
                imagePath = "recipes/r.jpg",
            )
            val archive = RecipeBackupArchive.encode(
                manifestJson = RecipeBackupCodec.encode(
                    listOf(incoming),
                    exportedAt = "2026-11-11T10:00:00Z",
                ),
                images = mapOf("recipes/r.jpg" to photo),
            )

            val images = FakeRecipeImageStore()
            val viewModel = AdminRecipesViewModel(
                recipesApi = FakeRecipesApi(backup = emptyList()),
                images = images,
                files = FakeDocumentFiles(zipToOpen = archive, openedName = "backup.zip"),
            )

            viewModel.pickBackupFile()
            advanceUntilIdle()
            assertNotNull(viewModel.importPreview, "a zip backup must be readable")

            viewModel.restoreImported()
            advanceUntilIdle()

            assertEquals(listOf("recipes/r.jpg"), images.restoredPaths)
            assertTrue(
                viewModel.statusMessage.orEmpty().contains("1 photos"),
                viewModel.statusMessage,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun scanningFindsPhotosNoRecipeReferences() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val images = FakeRecipeImageStore(
                mapOf(
                    "recipes/used.jpg" to byteArrayOf(1),
                    "recipes/orphan.jpg" to byteArrayOf(1, 2),
                    "steps/orphan.jpg" to byteArrayOf(1, 2, 3),
                ),
            )
            val api = FakeRecipesApi().apply { imagePaths = listOf("recipes/used.jpg") }
            val viewModel = AdminRecipesViewModel(
                recipesApi = api,
                images = images,
                nowProvider = { 10_000_000L },
            )

            viewModel.scanOrphanImages()
            advanceUntilIdle()

            val scan = assertNotNull(viewModel.orphanScan)
            assertEquals(setOf("recipes/orphan.jpg", "steps/orphan.jpg"), scan.paths.toSet())
            assertEquals(5, scan.totalBytes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletingOrphansRechecksTheReferencesFirst() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val images = FakeRecipeImageStore(
                mapOf(
                    "recipes/a.jpg" to byteArrayOf(1),
                    "recipes/b.jpg" to byteArrayOf(2),
                ),
            )
            val api = FakeRecipesApi().apply { imagePaths = emptyList() }
            val viewModel = AdminRecipesViewModel(
                recipesApi = api,
                images = images,
                nowProvider = { 10_000_000L },
            )

            viewModel.scanOrphanImages()
            advanceUntilIdle()
            assertEquals(2, viewModel.orphanScan?.count)

            // A recipe started using a.jpg after the scan: the delete must spare it.
            api.imagePaths = listOf("recipes/a.jpg")
            viewModel.deleteOrphanImages()
            advanceUntilIdle()

            assertEquals(listOf("recipes/b.jpg"), images.removedPaths)
            assertNull(viewModel.orphanScan, "the scan is cleared once it has been applied")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFreshUploadIsNotSwept() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val now = 5_000_000L
            val images = FakeRecipeImageStore()
            images.stored += StoredImage("recipes/fresh.jpg", 10, now - 1_000)
            images.stored += StoredImage("recipes/old.jpg", 10, now - 2 * 60 * 60 * 1000)

            val viewModel = AdminRecipesViewModel(
                recipesApi = FakeRecipesApi(),
                images = images,
                nowProvider = { now },
            )

            viewModel.scanOrphanImages()
            advanceUntilIdle()

            assertEquals(listOf("recipes/old.jpg"), viewModel.orphanScan?.paths)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun purgingTheTrashDeletesItsPhotos() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = FakeRecipesApi(
                backup = listOf(active, deleted.copy(imagePath = "recipes/dead.jpg")),
            ).apply { purgeCount = 1 }
            val images = FakeRecipeImageStore(mapOf("recipes/dead.jpg" to byteArrayOf(1)))
            val viewModel = AdminRecipesViewModel(recipesApi = api, images = images)

            viewModel.load()
            advanceUntilIdle()
            viewModel.purgeDeletedRecipes()
            advanceUntilIdle()

            assertEquals(1, api.purgeCalls)
            assertEquals(listOf("recipes/dead.jpg"), images.removedPaths)
            assertTrue(
                viewModel.statusMessage.orEmpty().contains("1 deleted recipes"),
                viewModel.statusMessage,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
