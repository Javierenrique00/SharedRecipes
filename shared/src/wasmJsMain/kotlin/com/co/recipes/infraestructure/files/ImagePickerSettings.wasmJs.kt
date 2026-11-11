package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/** The browser picker is a filtered input; no dialog settings exist. */
internal actual fun imagePickerDialogSettings(): FileKitDialogSettings =
    FileKitDialogSettings.createDefault()
