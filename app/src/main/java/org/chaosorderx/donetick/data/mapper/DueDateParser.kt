package org.chaosorderx.donetick.data.mapper

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Parses Donetick's RFC3339 date strings to epoch milliseconds.
 *
 * Live data uses whole-second UTC values (`2026-09-06T00:00:00Z`), but the server can also
 * emit a numeric offset and/or fractional seconds. `java.time` (via core library desugaring,
 * see `app/build.gradle.kts`) handles all of these; the previous `SimpleDateFormat` patterns
 * did not, and were duplicated across three call sites.
 */
object DueDateParser {

    /** @return epoch millis, or `null` when [value] is blank or cannot be parsed. */
    fun toEpochMillis(value: String?): Long? {
        val s = value?.trim().orEmpty()
        if (s.isEmpty()) return null
        return parseWith { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            ?: parseWith { Instant.parse(s).toEpochMilli() }
            ?: parseWith { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
    }

    private inline fun parseWith(block: () -> Long): Long? =
        try {
            block()
        } catch (e: DateTimeParseException) {
            null
        }
}
