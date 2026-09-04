package org.chaosorderx.donetick.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DueDateParserTest {

    @Test
    fun `parses whole-second UTC value (the live nextDueDate format)`() {
        assertEquals(
            Instant.parse("2026-09-06T00:00:00Z").toEpochMilli(),
            DueDateParser.toEpochMillis("2026-09-06T00:00:00Z"),
        )
    }

    @Test
    fun `parses nanosecond fractional seconds`() {
        assertEquals(
            Instant.parse("2025-06-24T04:00:01.938729751Z").toEpochMilli(),
            DueDateParser.toEpochMillis("2025-06-24T04:00:01.938729751Z"),
        )
    }

    @Test
    fun `parses a numeric offset`() {
        assertEquals(
            DueDateParser.toEpochMillis("2026-09-05T22:00:00Z"),
            DueDateParser.toEpochMillis("2026-09-06T00:00:00+02:00"),
        )
    }

    @Test
    fun `parses a zone-less value as UTC`() {
        assertEquals(
            Instant.parse("2026-09-06T00:00:00Z").toEpochMilli(),
            DueDateParser.toEpochMillis("2026-09-06T00:00:00"),
        )
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(
            Instant.parse("2026-09-06T00:00:00Z").toEpochMilli(),
            DueDateParser.toEpochMillis("  2026-09-06T00:00:00Z  "),
        )
    }

    @Test
    fun `returns null for null, blank, and garbage`() {
        assertNull(DueDateParser.toEpochMillis(null))
        assertNull(DueDateParser.toEpochMillis(""))
        assertNull(DueDateParser.toEpochMillis("   "))
        assertNull(DueDateParser.toEpochMillis("invalid-date-format"))
        assertNull(DueDateParser.toEpochMillis("2026-13-45T99:99:99Z"))
    }
}
