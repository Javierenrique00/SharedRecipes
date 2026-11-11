package com.co.recipes.infraestructure.pdf

/**
 * A minimal PDF 1.4 document: Helvetica text on A4 pages, written by hand.
 *
 * Object layout (fixed, so the cross-reference table can be written without a second pass):
 *
 * ```
 * 1  catalog      2  pages        3  font Helvetica    4  font Helvetica-Bold
 * 5+ page objects, then one content stream per page, then the info dictionary
 * ```
 *
 * Content streams are deliberately uncompressed: the files are a few KB, and a plain stream
 * means the text of a recipe can be grepped straight out of the PDF (which is also what
 * `RecipePdfTest` checks).
 */
class PdfDocumentBuilder(private val title: String) {

    private class Page(
        val content: StringBuilder = StringBuilder(),
        /** Indices into [images] drawn on this page; becomes the page's `/XObject` list. */
        val imageIndexes: MutableList<Int> = mutableListOf(),
    )

    private class EmbeddedImage(val jpeg: ByteArray, val width: Int, val height: Int)

    private val pages = mutableListOf<Page>()

    /** Every JPEG placed in the document, in insertion order (object `/Im1`, `/Im2`, …). */
    private val images = mutableListOf<EmbeddedImage>()

    private var cursorY: Float = PDF_A4_HEIGHT - MARGIN

    /** Starts a page. Called lazily by [ensurePage] so an empty document still has one. */
    fun newPage() {
        pages += Page()
        cursorY = PDF_A4_HEIGHT - MARGIN
    }

    fun ensurePage() {
        if (pages.isEmpty()) newPage()
    }

    /** Space left before the bottom margin, in points. */
    fun remainingHeight(): Float = cursorY - BOTTOM_MARGIN

    fun moveDown(points: Float) {
        cursorY -= points
    }

    /**
     * Draws one line of text at the cursor and moves down by [lineHeight].
     * [runs] allows mixed fonts on one line (a bold amount followed by an ingredient name).
     */
    fun line(runs: List<PdfTextRun>, x: Float = MARGIN, lineHeight: Float = 14f) {
        ensurePage()
        val page = pages.last()

        var currentX = x
        runs.forEach { run ->
            if (run.text.isEmpty()) return@forEach
            val font = if (run.bold) FONT_BOLD else FONT_REGULAR
            page.content
                .append("BT /")
                .append(font)
                .append(' ')
                .append(formatNumber(run.size))
                .append(" Tf ")
                .append(formatNumber(currentX))
                .append(' ')
                .append(formatNumber(cursorY))
                .append(" Td ")
                .append(gray(run.gray))
                .append(' ')
                .append('(')
                .append(escapePdfString(run.text))
                .append(") Tj ET\n")
            currentX += textWidth(run.text, run.size, run.bold)
        }

        cursorY -= lineHeight
    }

    /** A hairline rule across the text column. */
    fun rule() {
        ensurePage()
        pages.last().content
            .append(formatNumber(MARGIN))
            .append(' ')
            .append(formatNumber(cursorY))
            .append(" m ")
            .append(formatNumber(PDF_A4_WIDTH - MARGIN))
            .append(' ')
            .append(formatNumber(cursorY))
            .append(" l S\n")
        cursorY -= 6f
    }

    /**
     * Places a JPEG below the cursor, scaled to fit [maxWidth] × [maxHeight] and centred in
     * [maxWidth]. Starts a new page when it would not fit.
     *
     * The bytes are embedded verbatim: `/DCTDecode` tells the viewer to run its own JPEG
     * decoder, so the writer never needs to understand pixels. Returns false when the JPEG
     * is empty or its dimensions are unreadable.
     */
    fun image(jpeg: ByteArray, widthPx: Int, heightPx: Int, maxWidth: Float, maxHeight: Float): Boolean {
        if (jpeg.isEmpty() || widthPx <= 0 || heightPx <= 0) return false

        ensurePage()

        val aspect = widthPx.toFloat() / heightPx
        var width = maxWidth
        var height = width / aspect
        if (height > maxHeight) {
            height = maxHeight
            width = height * aspect
        }

        if (remainingHeight() < height + 6f) newPage()

        val index = images.size
        images += EmbeddedImage(jpeg, widthPx, heightPx)

        val page = pages.last()
        page.imageIndexes += index

        val x = MARGIN + (maxWidth - width) / 2f
        val y = cursorY - height
        page.content
            .append("q ")
            .append(formatNumber(width)).append(" 0 0 ")
            .append(formatNumber(height)).append(' ')
            .append(formatNumber(x)).append(' ')
            .append(formatNumber(y))
            .append(" cm /Im").append(index + 1).append(" Do Q\n")

        cursorY -= height
        return true
    }

    /**
     * Appends the per-page footer (label on the left, `Page n of m` on the right) and
     * serialises everything into PDF bytes.
     */
    fun build(footerLeft: String): ByteArray {
        ensurePage()

        pages.forEachIndexed { index, page ->
            val footerY = BOTTOM_MARGIN - 14f
            page.content
                .append("BT /").append(FONT_REGULAR).append(" 8 Tf ")
                .append(formatNumber(MARGIN)).append(' ').append(formatNumber(footerY))
                .append(" Td 0.4 g (").append(escapePdfString(footerLeft)).append(") Tj ET\n")

            val pageLabel = "Page ${index + 1} of ${pages.size}"
            val labelX = PDF_A4_WIDTH - MARGIN - textWidth(pageLabel, 8f, false)
            page.content
                .append("BT /").append(FONT_REGULAR).append(" 8 Tf ")
                .append(formatNumber(labelX)).append(' ').append(formatNumber(footerY))
                .append(" Td 0.4 g (").append(escapePdfString(pageLabel)).append(") Tj ET\n")
        }

        return serialise()
    }

    /**
     * Writes the objects, recording the byte offset of each one, then the cross-reference
     * table and the trailer. The xref offsets count *bytes*, so the file is assembled as a
     * byte array as it goes (WinAnsi characters are single bytes; a UTF-8 string builder
     * would desynchronise every offset).
     */
    private fun serialise(): ByteArray {
        val out = PdfByteSink()
        val offsets = mutableListOf<Int>()

        fun startObject(number: Int) {
            offsets += out.size
            out.append(number).append(" 0 obj\n")
        }

        out.append("%PDF-1.4\n")

        // 1: catalog
        startObject(1)
        out.append("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // 2: page tree
        startObject(2)
        out.append("<< /Type /Pages /Count ").append(pages.size).append(" /Kids [")
        pages.indices.forEach { index -> out.append(' ').append(pageObjectNumber(index)).append(" 0 R") }
        out.append(" ] >>\nendobj\n")

        // 3 and 4: the two base-14 fonts, WinAnsi encoded so Latin-1 text prints correctly.
        startObject(3)
        out.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")

        startObject(4)
        out.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        pages.forEachIndexed { index, page ->
            startObject(pageObjectNumber(index))
            out.append("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ")
                .append(formatNumber(PDF_A4_WIDTH)).append(' ')
                .append(formatNumber(PDF_A4_HEIGHT))
                .append("] /Resources << /Font << /")
                .append(FONT_REGULAR).append(' ').append(3).append(" 0 R /")
                .append(FONT_BOLD).append(' ').append(4).append(" 0 R >>")

            if (page.imageIndexes.isNotEmpty()) {
                out.append(" /XObject <<")
                page.imageIndexes.forEach { imageIndex ->
                    out.append(" /Im").append(imageIndex + 1).append(' ')
                        .append(imageObjectNumber(imageIndex)).append(" 0 R")
                }
                out.append(" >>")
            }

            out.append(" >> /Contents ")
                .append(contentObjectNumber(index)).append(" 0 R >>\nendobj\n")

            val content = page.content.toString()
            startObject(contentObjectNumber(index))
            out.append("<< /Length ").append(content.length).append(" >>\nstream\n")
            out.append(content)
            out.append("endstream\nendobj\n")
        }

        // JPEG XObjects. The bytes are copied verbatim: /DCTDecode hands the actual pixel
        // decoding to the PDF viewer, which is what keeps this writer free of an image codec.
        images.forEachIndexed { index, image ->
            startObject(imageObjectNumber(index))
            out.append("<< /Type /XObject /Subtype /Image /Width ").append(image.width)
                .append(" /Height ").append(image.height)
                .append(" /ColorSpace /DeviceRGB /BitsPerComponent 8")
                .append(" /Filter /DCTDecode /Length ").append(image.jpeg.size)
                .append(" >>\nstream\n")
            out.append(image.jpeg)
            out.append("\nendstream\nendobj\n")
        }

        val infoObjectNumber = imageObjectNumber(images.size)
        startObject(infoObjectNumber)
        out.append("<< /Title (").append(escapePdfString(title))
            .append(") /Producer (Shared Recipes) >>\nendobj\n")

        val xrefOffset = out.size
        out.append("xref\n0 ").append(infoObjectNumber + 1).append("\n")
        out.append("0000000000 65535 f \n")
        offsets.forEach { offset -> out.append(paddedOffset(offset)).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ").append(infoObjectNumber + 1)
            .append(" /Root 1 0 R /Info ").append(infoObjectNumber).append(" 0 R >>\n")
        out.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")

        return out.toByteArray()
    }

    private fun pageObjectNumber(index: Int): Int = 5 + index * 2

    private fun contentObjectNumber(index: Int): Int = 6 + index * 2

    /** Images come after every page/content pair, then the info dictionary. */
    private fun imageObjectNumber(index: Int): Int = 5 + pages.size * 2 + index
}

/** One styled piece of a text line. */
data class PdfTextRun(
    val text: String,
    val size: Float,
    val bold: Boolean = false,
    /** 0 = black, 1 = white; 0.4 is the grey used for meta text. */
    val gray: Float = 0f,
)

internal const val MARGIN: Float = 48f
private const val BOTTOM_MARGIN: Float = 56f
private const val FONT_REGULAR = "F1"
private const val FONT_BOLD = "F2"

/** `12.5` -> `12.5`, `48` -> `48`: PDF has no use for trailing zeros, and they cost bytes. */
internal fun formatNumber(value: Float): String {
    val rounded = kotlin.math.round(value * 100f) / 100f
    val asInt = rounded.toInt()
    return if (rounded == asInt.toFloat()) asInt.toString() else rounded.toString()
}

private fun gray(value: Float): String =
    if (value <= 0f) "0 g" else "${formatNumber(value)} g"

private fun paddedOffset(offset: Int): String = offset.toString().padStart(10, '0')

/** Growable byte buffer: `StringBuilder` would encode non-ASCII characters as two bytes. */
private class PdfByteSink {
    private val bytes = mutableListOf<Byte>()

    val size: Int get() = bytes.size

    fun append(text: String): PdfByteSink {
        text.forEach { character -> bytes += character.code.toByte() }
        return this
    }

    fun append(value: Int): PdfByteSink = append(value.toString())

    /** Raw bytes for embedded streams (JPEG data must not be re-encoded as characters). */
    fun append(data: ByteArray): PdfByteSink {
        data.forEach { bytes += it }
        return this
    }

    fun append(character: Char): PdfByteSink = append(character.toString())

    fun toByteArray(): ByteArray = bytes.toByteArray()
}
