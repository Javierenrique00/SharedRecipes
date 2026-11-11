package com.co.recipes.infraestructure.pdf

/** Pixel dimensions carried by a JPEG's SOF marker. */
internal data class JpegSize(val width: Int, val height: Int)

/**
 * Reads the width and height out of a JPEG byte stream without decoding it.
 *
 * The hand-written PDF embeds the JPEG bytes verbatim (`/DCTDecode`), but it still has to
 * know the aspect ratio to lay the picture out — and the SOF segment is where that lives.
 * Returns null for anything that is not a baseline/progressive JPEG (the app only uploads
 * JPEGs, so a null here means the file is corrupt).
 */
internal fun jpegSize(bytes: ByteArray): JpegSize? {
    if (bytes.size < 4) return null
    if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null

    var offset = 2
    while (offset + 9 < bytes.size) {
        if (bytes[offset] != 0xFF.toByte()) {
            offset++
            continue
        }

        val marker = bytes[offset + 1].toInt() and 0xFF
        when {
            // Standalone markers: no length field follows.
            marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7 || marker == 0xFF -> {
                offset += if (marker == 0xFF) 1 else 2
            }

            marker == 0xD9 || marker == 0xDA -> return null // end of image / start of scan

            // SOF0..SOF15 except DHT (C4), JPG (C8) and DAC (CC) carry the dimensions.
            marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
                val height = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 6].toInt() and 0xFF)
                val width = ((bytes[offset + 7].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 8].toInt() and 0xFF)
                return if (width > 0 && height > 0) JpegSize(width, height) else null
            }

            else -> {
                val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)
                if (length < 2) return null
                offset += 2 + length
            }
        }
    }
    return null
}
