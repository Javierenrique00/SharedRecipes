package com.co.recipes.domain.backup

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Interop proof for the archive: the file the app writes must also open in any other zip
 * tool, not just in [RecipeBackupArchive]. `java.util.zip` is the strictest cheap check we
 * have on the JVM (it validates the central directory and the entry headers).
 */
class RecipeBackupArchiveJvmTest {

    @Test
    fun javaUtilZipReadsTheArchiveTheAppWrites() {
        val archive = RecipeBackupArchive.encode(
            manifestJson = """{"formatVersion":2,"exportedAt":"now","recipes":[]}""",
            images = mapOf(
                "recipes/a.jpg" to byteArrayOf(1, 2, 3),
                "steps/b.jpg" to byteArrayOf(4, 5, 6),
            ),
        )

        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                // Read it to EOF so the entry's CRC is verified, not just skipped.
                zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertEquals(
            listOf(
                RecipeBackupArchive.MANIFEST_ENTRY,
                "images/recipes/a.jpg",
                "images/steps/b.jpg",
            ),
            names,
        )
    }
}
