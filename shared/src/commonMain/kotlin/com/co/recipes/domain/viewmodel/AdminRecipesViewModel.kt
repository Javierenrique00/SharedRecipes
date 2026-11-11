package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.domain.backup.RecipeBackupArchive
import com.co.recipes.domain.backup.RecipeBackupCodec
import com.co.recipes.domain.export.currentExportDate
import com.co.recipes.domain.export.currentExportInstant
import com.co.recipes.domain.maintenance.OrphanReport
import com.co.recipes.domain.maintenance.findOrphanedImages
import com.co.recipes.domain.maintenance.stillOrphaned
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.api.readableMessage
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto
import com.co.recipes.infraestructure.dataclass.RecipeBackupFile
import com.co.recipes.infraestructure.files.DeviceDocumentFiles
import com.co.recipes.infraestructure.files.DocumentFileStore
import com.co.recipes.infraestructure.files.PickedDocument
import com.co.recipes.infraestructure.files.SaveResult
import com.co.recipes.infraestructure.storage.RecipeImageStore
import kotlin.time.Clock
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
    private val images: RecipeImageStore,
    private val dateProvider: () -> String = ::currentExportDate,
    private val timestampProvider: () -> String = ::currentExportInstant,
    /** Current epoch millis; injectable so the orphan sweep's grace window is testable. */
    private val nowProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
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

    /** Result of the last orphan scan; null = not scanned yet. */
    var orphanScan by mutableStateOf<OrphanReport?>(null)
        private set

    var isScanningOrphans by mutableStateOf(false)
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

    /**
     * Photos belonging to the file currently in [importPreview], keyed by storage path.
     * Kept out of the preview state because it holds megabytes of bytes.
     */
    private var pendingImages: Map<String, ByteArray> = emptyMap()

    /** Writes every recipe (soft-deleted included) to a file the admin chooses. */
    fun exportAll() {
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.exportRecipes()
                .onSuccess { all ->
                    recipes = all
                    val json = RecipeBackupCodec.encode(all, timestampProvider())

                    // Download every photo the collection references. A missing object is
                    // skipped: the backup is still worth writing without it.
                    val images = downloadImages(all.flatMap { it.imagePaths() }.distinct())
                    val fileName = RecipeBackupCodec.suggestFileName(
                        date = dateProvider(),
                        zipped = images.isNotEmpty(),
                    )
                    val bytes = if (images.isEmpty()) {
                        json.encodeToByteArray()
                    } else {
                        RecipeBackupArchive.encode(json, images)
                    }

                    when (val saved = files.saveBackupFile(fileName, bytes)) {
                        is SaveResult.Saved ->
                            statusMessage = "${all.size} recipes" +
                                (if (images.isEmpty()) "" else " and ${images.size} photos") +
                                " backed up to ${saved.name}"

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
            // A new file replaces whatever the previous one carried.
            pendingImages = emptyMap()
            val picked = files.openBackupFile()
                .onFailure { errorMessage = it.readableMessage() }
                .getOrNull()

            if (picked == null) {
                // Cancelled, or failed and already reported above: nothing to preview.
                isBusy = false
                return@launch
            }

            readBackup(picked)
                .onSuccess { (file, images) ->
                    pendingImages = images
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
        pendingImages = emptyMap()
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

                    // Photos are restored after the rows so the recipe ids the archive
                    // refers to already exist (and so a failed upload is only a warning).
                    val photoCount = restoreImages(chosen.flatMap { it.imagePaths() }.distinct())
                    pendingImages = emptyMap()

                    statusMessage = "Restored ${chosen.size} recipes (${summary.describe()})" +
                        (if (photoCount > 0) ", $photoCount photos" else "")
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
            // Snapshot the paths first: after a hard clear the rows (and their paths) are gone,
            // so this is the only chance to delete the objects.
            val paths = if (mode == ClearMode.HARD) recipes.flatMap { it.imagePaths() } else emptyList()

            recipesApi.clearRecipes(hard = mode == ClearMode.HARD)
                .onSuccess { count ->
                    if (paths.isNotEmpty()) images.remove(paths)

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

    // -----------------------------------------------------------------------
    // Maintenance: empty the trash, sweep orphaned photos
    // -----------------------------------------------------------------------

    /**
     * Compares the photo bucket with the paths the database still references.
     *
     * Nothing is deleted here: the result is shown in the console and deleting needs a
     * second, explicit confirmation.
     */
    fun scanOrphanImages() {
        if (isBusy || isScanningOrphans) return

        viewModelScope.launch {
            isScanningOrphans = true
            errorMessage = null
            recipesApi.adminRecipeImagePaths()
                .onSuccess { referenced ->
                    images.listAll()
                        .onSuccess { stored ->
                            orphanScan = findOrphanedImages(
                                stored = stored,
                                referencedPaths = referenced,
                                nowMillis = nowProvider(),
                            )
                        }
                        .onFailure { errorMessage = it.readableMessage() }
                }
                .onFailure { errorMessage = it.readableMessage() }
            isScanningOrphans = false
        }
    }

    /**
     * Deletes the scanned objects after re-checking the references, so a recipe created
     * between the scan and the confirmation never loses its photo.
     */
    fun deleteOrphanImages() {
        val scan = orphanScan ?: return
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            recipesApi.adminRecipeImagePaths()
                .onSuccess { referenced ->
                    val remaining = scan.stillOrphaned(referenced)
                    orphanScan = null

                    if (remaining.isEmpty) {
                        statusMessage = "Nothing to clean — every photo is still in use."
                    } else {
                        images.remove(remaining.paths)
                        statusMessage = "${remaining.count} orphaned photos deleted"
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }
            isBusy = false
        }
    }

    /**
     * Permanently removes the soft-deleted recipes (the trash) and the photos they used.
     * Narrower than [clear]: recipes that are still visible are never touched.
     */
    fun purgeDeletedRecipes() {
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            // Snapshot before the rows disappear: afterwards their paths are gone.
            val photoPaths = deletedRecipes.flatMap { it.imagePaths() }

            recipesApi.purgeDeletedRecipes()
                .onSuccess { count ->
                    if (photoPaths.isNotEmpty()) images.remove(photoPaths)
                    orphanScan = null
                    statusMessage = "$count deleted recipes removed permanently"
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

    /** Reads a `.zip` backup (JSON + photos) or a plain `.json` one (photos absent). */
    private fun readBackup(
        picked: PickedDocument,
    ): Result<Pair<RecipeBackupFile, Map<String, ByteArray>>> =
        if (RecipeBackupArchive.isZip(picked.bytes)) {
            RecipeBackupArchive.decode(picked.bytes).mapCatching { content ->
                RecipeBackupCodec.decode(content.manifest).getOrThrow() to content.images
            }
        } else {
            runCatching { picked.text() }.mapCatching { text ->
                RecipeBackupCodec.decode(text).getOrThrow() to emptyMap()
            }
        }

    /** Path -> bytes for every photo that could be read; unreadable ones are skipped. */
    private suspend fun downloadImages(paths: List<String>): Map<String, ByteArray> {
        val contents = mutableMapOf<String, ByteArray>()
        paths.forEach { path ->
            images.download(path).onSuccess { contents[path] = it }
        }
        return contents
    }

    /** Re-uploads the photos that came with the backup to their recorded paths. */
    private suspend fun restoreImages(paths: List<String>): Int {
        var restored = 0
        paths.forEach { path ->
            val bytes = pendingImages[path] ?: return@forEach
            images.uploadTo(path, bytes).onSuccess { restored++ }
        }
        return restored
    }
}


