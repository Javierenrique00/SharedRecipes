package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.domain.backup.RecipeBackupCodec
import com.co.recipes.domain.export.currentExportDate
import com.co.recipes.domain.export.currentExportInstant
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.api.readableMessage
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.files.DeviceDocumentFiles
import com.co.recipes.infraestructure.files.DocumentFileStore
import com.co.recipes.infraestructure.files.SaveResult
import kotlinx.coroutines.launch

/**
 * Recipe administration: backup, restore and clear.
 *
 * Everything goes through the four RPCs in migration 9 — nothing is re-implemented on the
 * client, so the rules stay in Postgres and a non-admin who reaches this screen gets
 * rejected server side.
 *
 * The whole collection arrives in one `exportRecipes()` call: the same payload feeds the
 * "export to file" action and the list of soft-deleted recipes, which keeps the screen
 * consistent after every mutation (each one reloads).
 */
class AdminRecipesViewModel(
    private val recipesApi: RecipesApi,
    private val dateProvider: () -> String = ::currentExportDate,
    private val timestampProvider: () -> String = ::currentExportInstant,
    private val files: DocumentFileStore = DeviceDocumentFiles,
) : ViewModel() {

    /** All recipes, soft-deleted ones included. */
    var recipes by mutableStateOf<List<RecipeBackupDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** A dialog or a network call is in flight; the screen disables its buttons. */
    var isBusy by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The outcome of the last action, shown as a snackbar. */
    var statusMessage by mutableStateOf<String?>(null)
        private set

    /** A file the admin picked, waiting for confirmation. */
    var importPreview by mutableStateOf<ImportPreview?>(null)
        private set

    val activeRecipes: List<RecipeBackupDto> get() = recipes.filterNot { it.isDeleted }

    val deletedRecipes: List<RecipeBackupDto> get() = recipes.filter { it.isDeleted }

    val hasDeleted: Boolean get() = recipes.any { it.isDeleted }

    /**
     * What the file contains, plus which rows the admin ticked.
     *
     * Selection is by *index*, not by id: a hand-written or older backup can carry recipes
     * without an id, and two of them may share a title.
     */
    data class ImportPreview(
        val fileName: String,
        val recipes: List<RecipeBackupDto>,
        val selectedIndexes: Set<Int>,
        val existingIds: Set<String>,
    ) {
        val selectedCount: Int get() = selectedIndexes.size

        val selectedRecipes: List<RecipeBackupDto>
            get() = recipes.filterIndexed { index, _ -> index in selectedIndexes }

        /** True when importing would update/restore an existing recipe rather than add one. */
        fun isExisting(index: Int): Boolean {
            val id = recipes.getOrNull(index)?.id ?: return false
            return id in existingIds
        }
    }

    /** Soft (recoverable) or hard (cascading, irreversible) clear. */
    enum class ClearMode { SOFT, HARD }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            recipesApi.exportRecipes()
                .onSuccess { recipes = it }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }

    /** Writes every recipe (soft-deleted included) to a JSON file the admin chooses. */
    fun exportAll() {
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.exportRecipes()
                .onSuccess { all ->
                    recipes = all
                    val json = RecipeBackupCodec.encode(all, timestampProvider())
                    val fileName = RecipeBackupCodec.suggestFileName(dateProvider())

                    when (val saved = files.saveBackupFile(fileName, json)) {
                        is SaveResult.Saved ->
                            statusMessage = "${all.size} recipes backed up to ${saved.name}"

                        SaveResult.Cancelled -> Unit
                        is SaveResult.Failed -> errorMessage = saved.message
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }
            isBusy = false
        }
    }

    /** Opens the file picker and parses the backup into [importPreview] for confirmation. */
    fun pickBackupFile() {
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            val picked = files.openBackupFile()
                .onFailure { errorMessage = it.readableMessage() }
                .getOrNull()

            if (picked == null) {
                // Cancelled, or failed and already reported above: nothing to preview.
                isBusy = false
                return@launch
            }

            RecipeBackupCodec.decode(picked.text())
                .onSuccess { file ->
                    if (file.recipes.isEmpty()) {
                        errorMessage = "“${picked.name}” does not contain any recipes."
                    } else {
                        importPreview = ImportPreview(
                            fileName = picked.name,
                            recipes = file.recipes,
                            selectedIndexes = file.recipes.indices.toSet(),
                            existingIds = recipes.mapNotNull { it.id }.toSet(),
                        )
                    }
                }
                .onFailure { errorMessage = it.message ?: "That backup could not be read." }

            isBusy = false
        }
    }

    fun toggleImportRecipe(index: Int) {
        val preview = importPreview ?: return
        val selected = preview.selectedIndexes.toMutableSet()
        if (!selected.add(index)) selected.remove(index)
        importPreview = preview.copy(selectedIndexes = selected)
    }

    fun selectAllImportRecipes() {
        val preview = importPreview ?: return
        importPreview = preview.copy(selectedIndexes = preview.recipes.indices.toSet())
    }

    fun clearImportSelection() {
        val preview = importPreview ?: return
        importPreview = preview.copy(selectedIndexes = emptySet())
    }

    fun cancelImport() {
        importPreview = null
    }

    /** Restores the ticked recipes from the picked file. */
    fun restoreImported() {
        val preview = importPreview ?: return
        val chosen = preview.selectedRecipes
        if (chosen.isEmpty() || isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.importRecipes(chosen)
                .onSuccess { summary ->
                    importPreview = null
                    statusMessage = "Restored ${chosen.size} recipes (${summary.describe()})"
                    load()
                }
                .onFailure { errorMessage = it.readableMessage() }
            isBusy = false
        }
    }

    /** Clears `deleted_at` on soft-deleted recipes ("restore the ones I want"). */
    fun restoreDeleted(recipes: List<RecipeBackupDto>) {
        val ids = recipes.mapNotNull { it.id }
        if (ids.isEmpty() || isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.restoreRecipes(ids)
                .onSuccess { count ->
                    statusMessage = "$count recipes restored"
                    load()
                }
                .onFailure { errorMessage = it.readableMessage() }
            isBusy = false
        }
    }

    fun restoreAllDeleted() {
        restoreDeleted(deletedRecipes)
    }

    /**
     * Empties the collection. [ClearMode.SOFT] keeps everything restorable, which is the
     * safe default; [ClearMode.HARD] cannot be undone and the screen asks for a typed
     * confirmation before calling this.
     */
    fun clear(mode: ClearMode) {
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.clearRecipes(hard = mode == ClearMode.HARD)
                .onSuccess { count ->
                    statusMessage = if (mode == ClearMode.HARD) {
                        "$count recipes deleted permanently"
                    } else {
                        "$count recipes cleared — they can still be restored"
                    }
                    load()
                }
                .onFailure { errorMessage = it.readableMessage() }
            isBusy = false
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun clearStatus() {
        statusMessage = null
    }
}


