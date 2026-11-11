package com.co.recipes.testutil

import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.ImportSummary
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeDto

/**
 * Test double for the recipe API.
 *
 * Only the admin calls are recorded: the rest of the interface exists because the screen
 * under test does not use it, and a `NotImplementedError` makes an accidental call loud
 * instead of silent.
 */
class FakeRecipesApi(
    var backup: List<RecipeBackupDto> = emptyList(),
    var importSummary: ImportSummary = ImportSummary(),
    var restoreCount: Int = 0,
    var clearCount: Int = 0,
    private val detail: RecipeDto? = null,
    private val failWith: Throwable? = null,
) : RecipesApi {

    var exported = 0
        private set
    var imported: List<RecipeBackupDto> = emptyList()
        private set
    var restoredIds: List<String> = emptyList()
        private set
    var clearCalls: List<Boolean> = emptyList()
        private set

    /** How many recipes `purgeDeletedRecipes` reports as removed. */
    var purgeCount: Int = 0

    /** Paths `adminRecipeImagePaths` reports; the orphan scan compares against these. */
    var imagePaths: List<String> = emptyList()

    var purgeCalls = 0
        private set
    var imagePathCalls = 0
        private set

    /** What `createRecipe` returns; tests that save set it to a success. */
    var createResult: Result<String> = Result.failure(NotImplementedError())

    var updateResult: Result<Unit> = Result.failure(NotImplementedError())

    var createdParams: CreateRecipeParams? = null
        private set
    var updatedParams: CreateRecipeParams? = null
        private set

    override suspend fun exportRecipes(): Result<List<RecipeBackupDto>> {
        exported++
        return failWith?.let { Result.failure(it) } ?: Result.success(backup)
    }

    override suspend fun importRecipes(recipes: List<RecipeBackupDto>): Result<ImportSummary> {
        imported = recipes
        return failWith?.let { Result.failure(it) } ?: Result.success(importSummary)
    }

    override suspend fun restoreRecipes(ids: List<String>): Result<Int> {
        restoredIds = ids
        return failWith?.let { Result.failure(it) } ?: Result.success(restoreCount)
    }

    override suspend fun clearRecipes(hard: Boolean): Result<Int> {
        clearCalls = clearCalls + hard
        return failWith?.let { Result.failure(it) } ?: Result.success(clearCount)
    }

    override suspend fun purgeDeletedRecipes(): Result<Int> {
        purgeCalls++
        return failWith?.let { Result.failure(it) } ?: Result.success(purgeCount)
    }

    override suspend fun adminRecipeImagePaths(): Result<List<String>> {
        imagePathCalls++
        return failWith?.let { Result.failure(it) } ?: Result.success(imagePaths)
    }

    override suspend fun listRecipes(): Result<List<RecipeDto>> =
        Result.success(listOfNotNull(detail))

    override suspend fun getRecipe(id: String): Result<RecipeDto> =
        detail?.let { Result.success(it) } ?: Result.failure(NotImplementedError())

    override suspend fun createRecipe(params: CreateRecipeParams): Result<String> {
        createdParams = params
        return createResult
    }

    override suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit> {
        updatedParams = params
        return updateResult
    }

    override suspend fun deleteRecipe(id: String): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun restoreRecipe(id: String): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun addComment(recipeId: String, authorId: String, body: String): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun deleteComment(commentId: String): Result<Unit> =
        Result.failure(NotImplementedError())
}

/** In-memory stand-in for the file dialogs. */
class FakeDocumentFiles(
    var fileToOpen: String? = null,
    /** A `.zip` backup, when the test wants the archive path instead of plain JSON. */
    var zipToOpen: ByteArray? = null,
    var openedName: String = "recipes-backup-2026-11-08.json",
    var saveResult: com.co.recipes.infraestructure.files.SaveResult =
        com.co.recipes.infraestructure.files.SaveResult.Saved(openedName),
    /** The photo the picker returns; null means the user cancelled. */
    var imageToPick: com.co.recipes.infraestructure.files.PickedImage? = null,
) : com.co.recipes.infraestructure.files.DocumentFileStore {

    var savedFileName: String? = null
        private set
    var savedBytes: ByteArray? = null
        private set

    /** Convenience for the JSON/Markdown tests. */
    val savedContent: String? get() = savedBytes?.decodeToString()

    var savedZipSize: Int = 0
        private set

    override suspend fun openBackupFile(): Result<com.co.recipes.infraestructure.files.PickedDocument?> =
        Result.success(
            when {
                zipToOpen != null -> com.co.recipes.infraestructure.files.PickedDocument(
                    name = openedName,
                    bytes = zipToOpen!!,
                )

                fileToOpen != null -> com.co.recipes.infraestructure.files.PickedDocument(
                    name = openedName,
                    bytes = fileToOpen!!.encodeToByteArray(),
                )

                else -> null
            },
        )

    override suspend fun pickRecipeImage(): Result<com.co.recipes.infraestructure.files.PickedImage?> =
        Result.success(imageToPick)

    override suspend fun saveBackupFile(
        fileName: String,
        bytes: ByteArray,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedBytes = bytes
        if (nameLooksZipped(fileName)) savedZipSize = bytes.size
        return saveResult
    }

    override suspend fun saveMarkdownFile(
        fileName: String,
        markdown: String,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedBytes = markdown.encodeToByteArray()
        return saveResult
    }

    var savedPdfSize: Int = 0
        private set

    override suspend fun savePdfFile(
        fileName: String,
        bytes: ByteArray,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedPdfSize = bytes.size
        savedBytes = bytes
        return saveResult
    }

    override suspend fun saveZipFile(
        fileName: String,
        bytes: ByteArray,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedBytes = bytes
        savedZipSize = bytes.size
        return saveResult
    }

    private fun nameLooksZipped(fileName: String): Boolean =
        fileName.endsWith(".zip", ignoreCase = true)
}

/**
 * In-memory photo store.
 *
 * Records uploads (bytes + generated paths), keeps a path -> bytes index so downloads and
 * `uploadTo` restores work like the real bucket, and pins what a test expects to be deleted.
 */
class FakeRecipeImageStore(
    /** Seed contents, keyed by storage path, for export/backup tests. */
    initial: Map<String, ByteArray> = emptyMap(),
) : com.co.recipes.infraestructure.storage.RecipeImageStore {

    private val contents = initial.toMutableMap()
    private var counter = 0

    /**
     * Objects the fake bucket holds. Seeded from [initial] with a very old creation date so
     * the orphan sweep's grace window never hides them; tests that exercise the window add
     * their own [com.co.recipes.infraestructure.storage.StoredImage] entries here.
     */
    val stored: MutableList<com.co.recipes.infraestructure.storage.StoredImage> =
        initial.map { (path, bytes) ->
            com.co.recipes.infraestructure.storage.StoredImage(
                path = path,
                sizeBytes = bytes.size.toLong(),
                createdAtEpochMillis = 0L,
            )
        }.toMutableList()

    val uploadedBytes = mutableListOf<ByteArray>()
    val uploadedPaths = mutableListOf<String>()
    var restoredPaths = mutableListOf<String>()
        private set
    var removedPaths = mutableListOf<String>()
        private set

    /** Set to make the next upload fail, to test the "nothing was written" path. */
    var failUploadsWith: Throwable? = null

    override suspend fun upload(
        bytes: ByteArray,
        folder: com.co.recipes.infraestructure.storage.RecipeImageFolder,
    ): Result<String> {
        failUploadsWith?.let { return Result.failure(it) }

        val path = "${folder.name.lowercase()}/test-${counter++}.jpg"
        uploadedBytes += bytes
        uploadedPaths += path
        contents[path] = bytes
        stored += com.co.recipes.infraestructure.storage.StoredImage(
            path = path,
            sizeBytes = bytes.size.toLong(),
            createdAtEpochMillis = 0L,
        )
        return Result.success(path)
    }

    override suspend fun uploadTo(path: String, bytes: ByteArray): Result<Unit> {
        contents[path] = bytes
        stored.removeAll { it.path == path }
        stored += com.co.recipes.infraestructure.storage.StoredImage(
            path = path,
            sizeBytes = bytes.size.toLong(),
            createdAtEpochMillis = 0L,
        )
        restoredPaths += path
        return Result.success(Unit)
    }

    override suspend fun download(path: String): Result<ByteArray> =
        contents[path]?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("No object at $path"))

    override suspend fun listAll(): Result<List<com.co.recipes.infraestructure.storage.StoredImage>> =
        Result.success(stored.toList())

    override suspend fun remove(paths: Collection<String>): Result<Unit> {
        removedPaths += paths
        paths.forEach { contents.remove(it) }
        stored.removeAll { it.path in paths }
        return Result.success(Unit)
    }
}

