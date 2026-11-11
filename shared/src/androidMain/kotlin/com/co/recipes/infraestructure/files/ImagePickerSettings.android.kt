package com.co.recipes.infraestructure.files

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/** Android's photo picker needs no special settings. */
internal actual fun imagePickerDialogSettings(): FileKitDialogSettings =
    FileKitDialogSettings.createDefault()
