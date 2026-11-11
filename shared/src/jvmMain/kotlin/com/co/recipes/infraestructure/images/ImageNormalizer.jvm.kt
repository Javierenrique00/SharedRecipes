package com.co.recipes.infraestructure.images

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/** Desktop uses Skia through Compose, which honours the format's own metadata. */
internal actual suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    bytes.decodeToImageBitmap()
