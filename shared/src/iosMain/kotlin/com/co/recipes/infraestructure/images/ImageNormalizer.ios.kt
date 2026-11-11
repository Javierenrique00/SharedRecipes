package com.co.recipes.infraestructure.images

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap

/**
 * iOS decodes through Skia (Compose). The picker is asked for a *compatible* asset
 * representation (see `DocumentFiles.ios.kt`), so photos arrive as JPEG/PNG rather than
 * HEIC, which Skia cannot read.
 */
internal actual suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    bytes.decodeToImageBitmap()
