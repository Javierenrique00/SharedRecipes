package com.co.recipes.domain.export

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** `2026-11-08` — the date part of a backup file name. */
fun currentExportDate(): String =
    Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

/** `2026-11-08 09:41` — the stamp printed in a document footer. */
fun currentExportTimestamp(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour.toString().padStart(2, '0')
    val minute = now.minute.toString().padStart(2, '0')
    return "${now.date} $hour:$minute"
}

/** ISO-8601 instant recorded inside a backup file (`exportedAt`). */
fun currentExportInstant(): String = Clock.System.now().toString()
