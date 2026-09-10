package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TweetFactCheckerTest {

    private val evidence = """
        My Nix in a Box added Jaxson Dart (NYG, QB) and dropped Alvin Kamara (NO, RB)
        week 1
        Questionable (Kamara is dealing with a knee issue and was limited Wednesday.)
    """.trimIndent()

    @Test
    fun `passes a post that only states what the evidence says`() {
        val tweet = "My Nix in a Box adds Jaxson Dart and cuts Alvin Kamara, who is questionable with a knee issue."
        assertEquals(emptyList<String>(), TweetFactChecker.findUnsupportedClaims(tweet, evidence))
    }

    @Test
    fun `flags a week the evidence does not support`() {
        val tweet = "Week 22 shakeup: My Nix in a Box adds Jaxson Dart."
        val problems = TweetFactChecker.findUnsupportedClaims(tweet, evidence)
        assertEquals(1, problems.size)
        assertTrue(problems.first().contains("week 22"))
    }

    @Test
    fun `allows the week we actually supplied`() {
        val tweet = "Week 1 move: My Nix in a Box adds Jaxson Dart."
        assertEquals(emptyList<String>(), TweetFactChecker.findUnsupportedClaims(tweet, evidence))
    }

    @Test
    fun `flags invented statistics`() {
        val tweet = "Kamara goes after 1,200 yards and 14 touchdowns last season."
        val problems = TweetFactChecker.findUnsupportedClaims(tweet, evidence)
        assertEquals(2, problems.size)
        assertTrue(problems.any { it.contains("1,200 yards") })
        assertTrue(problems.any { it.contains("14 touchdowns") })
    }

    @Test
    fun `allows statistics that appear in the evidence`() {
        val tweet = "Dart threw for 210 yards in the opener."
        val supported = "Jaxson Dart threw for 210 yards in Sunday's opener."
        assertEquals(emptyList<String>(), TweetFactChecker.findUnsupportedClaims(tweet, supported))
    }

    @Test
    fun `matches numbers across thousands separators`() {
        val tweet = "Kamara is 89 yards from 1000 yards."
        val supported = "Kamara sits 89 yards short of 1,000 yards on the season."
        assertEquals(emptyList<String>(), TweetFactChecker.findUnsupportedClaims(tweet, supported))
    }

    @Test
    fun `flags invented quotes`() {
        val tweet = """Source: "He is the future of this offense," per team brass."""
        val problems = TweetFactChecker.findUnsupportedClaims(tweet, evidence)
        assertEquals(1, problems.size)
        assertTrue(problems.first().startsWith("quotes"))
    }

    @Test
    fun `flags invented rankings and percentages`() {
        val tweet = "Dart, the No. 25 QB, is rostered in 68% of leagues."
        val problems = TweetFactChecker.findUnsupportedClaims(tweet, evidence)
        assertEquals(2, problems.size)
        assertTrue(problems.any { it.contains("ranking") })
        assertTrue(problems.any { it.contains("percentage") })
    }

    @Test
    fun `ignores plain prose with no numeric claims`() {
        val tweet = "Roster move: My Nix in a Box adds Jaxson Dart and drops Alvin Kamara."
        assertEquals(emptyList<String>(), TweetFactChecker.findUnsupportedClaims(tweet, evidence))
    }
}
