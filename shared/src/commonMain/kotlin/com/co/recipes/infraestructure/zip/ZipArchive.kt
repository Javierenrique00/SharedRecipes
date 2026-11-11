package com.co.recipes.infraestructure.zip

import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream

/**
 * Minimal in-memory zip writer shared by the backup and the Markdown export.
 *
 * Entries are written in the order given (callers sort when they care), and the archive is
 * produced entirely in memory — a recipe backup is a few MB at most, and the browser has no
 * other option. kmp-zip does the container format, so the same bytes are produced on
 * Android, iOS, desktop and wasm.
 */
fun encodeZip(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** ZIP local file header magic (`PK\x03\x04`). */
fun isZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() &&
        bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() &&
        bytes[3] == 0x04.toByte()
