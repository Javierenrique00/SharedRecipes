package com.co.recipes.infraestructure.files

import com.co.recipes.infraestructure.api.readableMessage
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException

/** A file the user picked, already read into memory (backups are small). */
class PickedDocument(
    val name: String,
    val bytes: ByteArray,
) {
    /** Throws [IllegalArgumentException] if the bytes are not valid UTF-8. */
    fun text(): String = bytes.decodeToString()
}

/** A photo the user picked, in whatever format the platform returned it. */
class PickedImage(val bytes: ByteArray)

sealed interface SaveResult {
    data class Saved(val name: String) : SaveResult

    /** The user dismissed the dialog; nothing to report. */
    data object Cancelled : SaveResult

    data class Failed(val message: String) : SaveResult
}

/**
 * Picking and saving documents, shared by the admin backup, the recipe export and the
 * recipe photos.
 *
 * Opening a file works the same way on every target (FileKit's picker: the Android
 * document picker, the iOS Files picker, an AWT dialog, or the browser's file input), so
 * [DeviceDocumentFiles] is one implementation. Saving does **not**: browsers have no save
 * dialog and no writable file system, so [saveDocument] stays an `expect` and the web build
 * downloads instead.
 *
 * An interface rather than a bare object so the admin view model can be tested with an
 * in-memory double instead of a real dialog — see `AdminRecipesViewModelTest`.
 */
interface DocumentFileStore {

    /** Returns `null` when the user cancels; a failure carries a readable message. */
    suspend fun openBackupFile(): Result<PickedDocument?>

    /** Opens the native photo picker (gallery on Android/iOS). Null when cancelled. */
    suspend fun pickRecipeImage(): Result<PickedImage?>

    suspend fun saveBackupFile(fileName: String, bytes: ByteArray): SaveResult

    suspend fun saveMarkdownFile(fileName: String, markdown: String): SaveResult

    suspend fun savePdfFile(fileName: String, bytes: ByteArray): SaveResult

    suspend fun saveZipFile(fileName: String, bytes: ByteArray): SaveResult
}

/** The real thing: FileKit dialogs on Android/iOS/desktop, a download on the web. */
object DeviceDocumentFiles : DocumentFileStore {

    const val MIME_JSON = "application/json"
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_PDF = "application/pdf"
    const val MIME_ZIP = "application/zip"

    override suspend fun openBackupFile(): Result<PickedDocument?> =
        openDocument(extensions = setOf("json", "zip"))

    override suspend fun pickRecipeImage(): Result<PickedImage?> = try {
        val file = FileKit.openFilePicker(
            type = FileKitType.Image,
            dialogSettings = imagePickerDialogSettings(),
        )
        Result.success(file?.let { picked -> PickedImage(picked.readBytes()) })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun saveBackupFile(fileName: String, bytes: ByteArray): SaveResult =
        saveDocument(fileName = fileName, mimeType = MIME_JSON, bytes = bytes)

    override suspend fun saveMarkdownFile(fileName: String, markdown: String): SaveResult =
        saveDocument(
            fileName = fileName,
            mimeType = MIME_MARKDOWN,
            bytes = markdown.encodeToByteArray(),
        )

    override suspend fun savePdfFile(fileName: String, bytes: ByteArray): SaveResult =
        saveDocument(fileName = fileName, mimeType = MIME_PDF, bytes = bytes)

    override suspend fun saveZipFile(fileName: String, bytes: ByteArray): SaveResult =
        saveDocument(fileName = fileName, mimeType = MIME_ZIP, bytes = bytes)

    private suspend fun openDocument(extensions: Set<String>): Result<PickedDocument?> = try {
        val file = FileKit.openFilePicker(type = FileKitType.File(extensions))
        Result.success(
            file?.let { picked ->
                PickedDocument(name = picked.name, bytes = picked.readBytes())
            },
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

/**
 * Photo picker settings.
 *
 * iOS defaults to the asset's *current* representation, which can be HEIC — a format the
 * image decoder behind `normalizeToJpeg` cannot read. The iOS actual asks for the
 * compatible representation so the picker transcodes the photo to JPEG.
 */
internal expect fun imagePickerDialogSettings(): FileKitDialogSettings

/**
 * Writes [bytes] where the user says.
 *
 * Android/iOS/desktop show a real save dialog; the browser downloads the file, because a
 * page cannot be granted write access to the file system.
 */
internal expect suspend fun saveDocument(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
): SaveResult

/** Turns the throwables FileKit raises into the message the UI shows. */
internal fun Throwable.toSaveFailure(): SaveResult.Failed =
    SaveResult.Failed(readableMessage())
