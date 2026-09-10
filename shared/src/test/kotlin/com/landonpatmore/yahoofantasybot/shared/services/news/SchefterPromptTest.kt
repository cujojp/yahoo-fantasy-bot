package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SchefterPromptTest {

    private val brief = NewsBrief(
        facts = listOf(
            NewsFact("Alvin Kamara", "Questionable (knee)", "ESPN", Instant.parse("2026-09-09T21:13:00Z"))
        ),
        week = 1,
        seasonType = "regular"
    )

    @Test
    fun `the system prompt forbids inventing facts and week numbers`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("Never invent or estimate"))
        assertTrue(system.contains("Never mention a week number unless one is given"))
        assertTrue(system.contains("If FACTS says (none)"))
    }

    @Test
    fun `the user prompt carries the transaction, the week and the facts`() {
        val user = SchefterPrompt.user("ADD/DROP", "Team added X and dropped Y", brief)
        assertTrue(user.contains("Detail: Team added X and dropped Y"))
        assertTrue(user.contains("Current NFL week: 1"))
        assertTrue(user.contains("1. [ESPN, 2026-09-09] Alvin Kamara: Questionable (knee)"))
    }

    @Test
    fun `an empty brief tells the model there are no facts`() {
        val user = SchefterPrompt.user("ADD", "Team added X", NewsBrief(emptyList()))
        assertTrue(user.contains("FACTS\n(none)"))
    }

    @Test
    fun `evidence covers the transaction, the week and every fact`() {
        val evidence = SchefterPrompt.evidence("Team added X and dropped Y", brief)
        assertTrue(TweetFactChecker.findUnsupportedClaims("Week 1 move.", evidence).isEmpty())
        assertTrue(TweetFactChecker.findUnsupportedClaims("Week 22 move.", evidence).isNotEmpty())
    }
}
