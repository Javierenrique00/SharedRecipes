package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/** The desktop picker is a plain file dialog. */
internal actual fun imagePickerDialogSettings(): FileKitDialogSettings =
    FileKitDialogSettings.createDefault()
