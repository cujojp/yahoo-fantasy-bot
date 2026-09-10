package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class EspnNewsClientTest {

    @Test
    fun `parses the article timestamp format`() {
        assertEquals(
            Instant.parse("2026-09-09T23:19:43Z"),
            EspnNewsClient.parseInstant("2026-09-09T23:19:43Z")
        )
    }

    @Test
    fun `parses the injury timestamp format, which omits seconds`() {
        assertEquals(
            Instant.parse("2026-09-09T21:13:00Z"),
            EspnNewsClient.parseInstant("2026-09-09T21:13Z")
        )
    }

    @Test
    fun `returns null rather than guessing at an unparseable date`() {
        assertNull(EspnNewsClient.parseInstant(""))
        assertNull(EspnNewsClient.parseInstant(null))
        assertNull(EspnNewsClient.parseInstant("last Tuesday"))
    }
}
