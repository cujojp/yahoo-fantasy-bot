package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class NewsBriefTest {

    private val today = LocalDate.parse("2026-09-14")

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
    fun `renders a numbered facts block with the weekday and the age`() {
        val block = NewsBrief(listOf(espn, yahoo)).toFactsBlock(today)
        assertEquals(
            "1. [ESPN, Wed Sep 9, 5 days ago] Alvin Kamara: Questionable (limited Wednesday with a knee issue)\n" +
                    "2. [Yahoo, date unknown] Jakobi Meyers: Listed Questionable - thumb",
            block
        )
    }

    @Test
    fun `names today and yesterday rather than counting them`() {
        assertEquals("today", NewsFact.describeAge(0))
        assertEquals("yesterday", NewsFact.describeAge(1))
        assertEquals("2 days ago", NewsFact.describeAge(2))
    }

    @Test
    fun `dates a fact by the league clock, not UTC`() {
        // 8pm ET on Sunday night is already Monday in UTC. A Sunday game belongs to Sunday.
        val sundayNight = NewsFact(
            playerName = "Chris Rodriguez Jr.",
            text = "Active (rushed six times for 23 yards)",
            source = "ESPN",
            published = Instant.parse("2026-09-14T01:30:00Z")
        )
        assertEquals(LocalDate.parse("2026-09-13"), sundayNight.publishedOn())
        assertTrue(sundayNight.toPromptLine(today).startsWith("[ESPN, Sun Sep 13, yesterday]"))
    }

    @Test
    fun `an empty brief renders as none so the prompt can branch on it`() {
        val brief = NewsBrief(emptyList())
        assertTrue(brief.isEmpty)
        assertEquals("(none)", brief.toFactsBlock(today))
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
