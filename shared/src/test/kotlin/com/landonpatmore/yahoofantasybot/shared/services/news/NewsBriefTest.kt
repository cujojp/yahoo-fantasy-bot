package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NewsBriefTest {

    private val espn = NewsFact(
        playerName = "Alvin Kamara",
        text = "Questionable (limited Wednesday with a knee issue)",
        source = "ESPN",
        published = Instant.parse("2026-09-09T21:13:00Z")
    )

    private val yahoo = NewsFact(
        playerName = "Jakobi Meyers",
        text = "Listed Questionable - thumb",
        source = "Yahoo",
        published = null
    )

    @Test
    fun `renders a numbered facts block with source and date`() {
        val block = NewsBrief(listOf(espn, yahoo)).toFactsBlock()
        assertEquals(
            "1. [ESPN, 2026-09-09] Alvin Kamara: Questionable (limited Wednesday with a knee issue)\n" +
                    "2. [Yahoo] Jakobi Meyers: Listed Questionable - thumb",
            block
        )
    }

    @Test
    fun `an empty brief renders as none so the prompt can branch on it`() {
        val brief = NewsBrief(emptyList())
        assertTrue(brief.isEmpty)
        assertEquals("(none)", brief.toFactsBlock())
    }

    @Test
    fun `attribution names every source and the most recent date`() {
        assertEquals("via ESPN and Yahoo, Sep 9", NewsBrief(listOf(espn, yahoo)).attribution())
    }

    @Test
    fun `an ungrounded post gets no attribution`() {
        assertNull(NewsBrief(emptyList()).attribution())
    }
}
