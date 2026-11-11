package com.co.recipes.infraestructure.images

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/** The browser decodes with Skia (Compose for Web). */
internal actual suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    bytes.decodeToImageBitmap()
