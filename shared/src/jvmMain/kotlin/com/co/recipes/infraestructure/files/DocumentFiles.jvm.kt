package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException

/**
 * Save dialog via FileKit (the Android "Create document" screen, the iOS Files picker, or
 * an AWT save dialog). The web target has no equivalent and downloads instead — see the
 * wasmJs actual.
 *
 * `suggestedName` must not carry the extension: FileKit appends `defaultExtension` itself.
 */
internal actual suspend fun saveDocument(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
): SaveResult = try {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")

    val target = FileKit.openFileSaver(
        suggestedName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName),
        defaultExtension = extension.takeIf { it.isNotEmpty() },
        allowedExtensions = setOfNotNull(extension.takeIf { it.isNotEmpty() }),
    )

    if (target == null) {
        SaveResult.Cancelled
    } else {
        target.write(bytes)
        SaveResult.Saved(fileName)
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    error.toSaveFailure()
}
