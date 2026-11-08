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

    override suspend fun listRecipes(): Result<List<RecipeDto>> =
        Result.success(listOfNotNull(detail))

    override suspend fun getRecipe(id: String): Result<RecipeDto> =
        detail?.let { Result.success(it) } ?: Result.failure(NotImplementedError())

    override suspend fun createRecipe(params: CreateRecipeParams): Result<String> =
        Result.failure(NotImplementedError())

    override suspend fun updateRecipe(recipeId: String, params: CreateRecipeParams): Result<Unit> =
        Result.failure(NotImplementedError())

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
    var openedName: String = "recipes-backup-2026-11-08.json",
    var saveResult: com.co.recipes.infraestructure.files.SaveResult =
        com.co.recipes.infraestructure.files.SaveResult.Saved(openedName),
) : com.co.recipes.infraestructure.files.DocumentFileStore {

    var savedFileName: String? = null
        private set
    var savedContent: String? = null
        private set

    override suspend fun openBackupFile(): Result<com.co.recipes.infraestructure.files.PickedDocument?> =
        Result.success(
            fileToOpen?.let {
                com.co.recipes.infraestructure.files.PickedDocument(
                    name = openedName,
                    bytes = it.encodeToByteArray(),
                )
            },
        )

    override suspend fun saveBackupFile(
        fileName: String,
        json: String,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedContent = json
        return saveResult
    }

    override suspend fun saveMarkdownFile(
        fileName: String,
        markdown: String,
    ): com.co.recipes.infraestructure.files.SaveResult {
        savedFileName = fileName
        savedContent = markdown
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
        return saveResult
    }
}
