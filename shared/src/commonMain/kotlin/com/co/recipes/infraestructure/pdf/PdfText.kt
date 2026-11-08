package com.co.recipes.infraestructure.pdf

/**
 * Typography primitives for the hand-written PDF writer: character encoding, escaping and
 * the width metrics used to wrap lines.
 *
 * Why hand-written: the only Kotlin Multiplatform PDF library tried (KmPDF 1.2.0) is built
 * against an older Skiko and dies with `NoSuchMethodError` on Compose 1.12. Writing the
 * bytes here costs ~300 lines, produces selectable text, and behaves identically on Android,
 * iOS, desktop and the browser — no platform renderer involved at all.
 *
 * Fonts are the base-14 Helvetica pair, which every PDF reader ships, so nothing is
 * embedded and the files stay a few KB. That limits the text to the WinAnsi (Latin-1)
 * repertoire; characters outside it are replaced by '?' by [toWinAnsi].
 *
 * Code points are written as hex numbers on purpose: this file needs no backslash escapes,
 * which keeps Kotlin's string escaping and the PDF format's own escapes from colliding.
 */

/** A4 in PDF points (1/72 inch). */
const val PDF_A4_WIDTH: Float = 595f
const val PDF_A4_HEIGHT: Float = 842f

/** Helvetica widths for ASCII 32..126, in 1/1000 of the font size (AFM values). */
private val HelveticaWidths = intArrayOf(
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
    1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
    333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
    556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
)

/** Width of one character in points, for wrapping. Bold is approximated by a factor. */
fun charWidth(character: Char, size: Float, bold: Boolean): Float {
    val code = character.code
    val perMille = if (code in 32..126) HelveticaWidths[code - 32] else 556
    val factor = if (bold) 1.06f else 1f
    return perMille * size * factor / 1000f
}

fun textWidth(text: String, size: Float, bold: Boolean): Float =
    text.sumOf { charWidth(it, size, bold).toDouble() }.toFloat()

/**
 * Encodes a character as a WinAnsi byte.
 *
 * Latin-1 covers Spanish, French, German and Portuguese, which is what recipe text needs.
 * The typographic characters that are absent from Latin-1 but present in WinAnsi (curly
 * quotes, dashes, ellipsis, the euro sign) are mapped explicitly so pasted text does not
 * turn into '?'. Anything else (emoji, CJK) is replaced.
 */
fun toWinAnsi(character: Char): Char = when (character.code) {
    0x20AC -> 0x80.toChar() // euro
    0x201A -> 0x82.toChar()
    0x0192 -> 0x83.toChar()
    0x201E -> 0x84.toChar()
    0x2026 -> 0x85.toChar() // ellipsis
    0x2020 -> 0x86.toChar()
    0x2021 -> 0x87.toChar()
    0x02C6 -> 0x88.toChar()
    0x2030 -> 0x89.toChar()
    0x0160 -> 0x8A.toChar()
    0x2039 -> 0x8B.toChar()
    0x0152 -> 0x8C.toChar()
    0x017D -> 0x8E.toChar()
    0x2018 -> 0x91.toChar() // curly quotes
    0x2019 -> 0x92.toChar()
    0x201C -> 0x93.toChar()
    0x201D -> 0x94.toChar()
    0x2022 -> 0x95.toChar() // bullet
    0x2013 -> 0x96.toChar() // en dash
    0x2014 -> 0x97.toChar() // em dash
    0x02DC -> 0x98.toChar()
    0x2122 -> 0x99.toChar()
    0x0161 -> 0x9A.toChar()
    0x203A -> 0x9B.toChar()
    0x0153 -> 0x9C.toChar()
    0x017E -> 0x9E.toChar()
    0x0178 -> 0x9F.toChar()
    0x00A0 -> 0x20.toChar() // non-breaking space
    else -> if (character.code in 32..255) character else '?'
}

private const val BACKSLASH = 92
private const val OPEN_PAREN = 40
private const val CLOSE_PAREN = 41

/**
 * Escapes a string for a PDF literal string: only the backslash and the parentheses are
 * reserved, and anything outside WinAnsi was already replaced by [toWinAnsi].
 */
fun escapePdfString(text: String): String = buildString(text.length + 8) {
    text.forEach { character ->
        val code = toWinAnsi(character).code
        when (code) {
            BACKSLASH, OPEN_PAREN, CLOSE_PAREN -> {
                append(BACKSLASH.toChar())
                append(code.toChar())
            }

            else -> append(code.toChar())
        }
    }
}
