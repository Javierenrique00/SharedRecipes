package com.co.recipes.infraestructure.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The PDF writer never decodes pixels: it reads the SOF marker to learn the aspect ratio and
 * embeds the JPEG bytes verbatim. That makes this small parser part of the export contract.
 */
class JpegInfoTest {

    @Test
    fun readsTheDimensionsFromTheSofSegment() {
        assertEquals(JpegSize(400, 300), jpegSize(jpegWith(width = 400, height = 300)))
    }

    @Test
    fun aPortraitPhotoReportsItsRealShape() {
        assertEquals(JpegSize(300, 900), jpegSize(jpegWith(width = 300, height = 900)))
    }

    @Test
    fun somethingThatIsNotAJpegHasNoSize() {
        assertNull(jpegSize("not an image".encodeToByteArray()))
        assertNull(jpegSize(ByteArray(0)))
    }

    /** SOI, a baseline SOF0 carrying the dimensions, and EOI. */
    private fun jpegWith(width: Int, height: Int): ByteArray = bytes(
        0xFF, 0xD8,
        0xFF, 0xC0, 0x00, 0x11, 0x08,
        height shr 8, height and 0xFF,
        width shr 8, width and 0xFF,
        0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
        0xFF, 0xD9,
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
