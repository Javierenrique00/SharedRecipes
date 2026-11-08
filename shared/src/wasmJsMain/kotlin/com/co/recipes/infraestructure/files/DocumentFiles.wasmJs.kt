package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.CancellationException

/**
 * The browser has no save dialog and no writable file system, so the file is handed to the
 * download manager instead — the user still chooses where it lands, one step later, in the
 * browser's own UI. This is the only target where saving is not a real "save as" dialog.
 */
internal actual suspend fun saveDocument(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
): SaveResult = try {
    FileKit.download(bytes, fileName = fileName)
    SaveResult.Saved(fileName)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    error.toSaveFailure()
}
