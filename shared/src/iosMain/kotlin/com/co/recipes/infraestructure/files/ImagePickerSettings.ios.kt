package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.dialogs.FileKitAssetRepresentationMode
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/**
 * Ask PHPicker for the *compatible* representation: the default can hand back HEIC, which
 * the image decoder behind `normalizeToJpeg` cannot read. With this, iOS transcodes the
 * photo to JPEG on its way to the app.
 */
internal actual fun imagePickerDialogSettings(): FileKitDialogSettings =
    FileKitDialogSettings(assetRepresentationMode = FileKitAssetRepresentationMode.Compatible)
