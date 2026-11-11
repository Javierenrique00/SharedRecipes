package com.co.recipes.infraestructure.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.decodeToImageBitmap
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.dialogs.compose.util.encodeToByteArray
import kotlin.math.roundToInt

/**
 * Re-encodes a picked photo as a JPEG no larger than [maxDimension] on its longest side.
 *
 * Normalizing on the way in is what keeps the rest of the feature simple:
 *
 *  * storage holds one format (JPEG), so the bucket can restrict `allowed_mime_types`;
 *  * the hand-written PDF can embed the bytes verbatim with `/DCTDecode` — no image
 *    decoder, no Skia on Android, nothing that can drift;
 *  * backups and Markdown exports stay small.
 *
 * Photos are never upscaled. Format support comes from the platform decoder
 * ([decodeImageBitmap]); anything it cannot read surfaces as a readable error.
 */
internal suspend fun normalizeToJpeg(
    bytes: ByteArray,
    maxDimension: Int = MaxImageDimension,
    quality: Int = ImageQuality,
): ByteArray {
    val source = decodeImageBitmap(bytes)
    val largest = maxOf(source.width, source.height)

    val bitmap = if (largest <= maxDimension || largest <= 0) {
        source
    } else {
        val scale = maxDimension.toFloat() / largest
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)

        val target = ImageBitmap(width, height)
        val canvas = Canvas(target)
        canvas.scale(width.toFloat() / source.width, height.toFloat() / source.height)
        canvas.drawImage(source, Offset.Zero, Paint())
        target
    }

    return bitmap.encodeToByteArray(ImageFormat.JPEG, quality)
}

internal const val MaxImageDimension = 1600
internal const val ImageQuality = 85

/**
 * Decodes [bytes] into an [ImageBitmap], applying the EXIF orientation where the platform
 * decoder would otherwise ignore it (Android's `BitmapFactory` does).
 */
internal expect suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap
