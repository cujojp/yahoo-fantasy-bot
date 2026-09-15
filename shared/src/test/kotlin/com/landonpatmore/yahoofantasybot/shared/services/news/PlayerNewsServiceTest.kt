package com.landonpatmore.yahoofantasybot.shared.services.news

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PlayerNewsServiceTest {

    private val today = LocalDate.parse("2026-09-14")

    private val dart = PlayerInfo("Jaxson Dart", "NYG", "QB")
    private val kamara = PlayerInfo("Alvin Kamara", "NO", "RB")

    private fun fact(name: String, text: String, date: String?) = NewsFact(
        playerName = name,
        text = text,
        source = "ESPN",
        published = date?.let { Instant.parse("${it}T16:00:00Z") }
    )

    private class FakeEspn(
        private val injuryRows: Map<String, NewsFact> = emptyMap(),
        private val headlineRows: Map<String, List<NewsFact>> = emptyMap()
    ) : EspnNewsClient() {
        override fun injuries() = injuryRows
        override fun headlines() = headlineRows
    }

    private class SilentSleeper : SleeperClient(playerMapEnabled = false) {
        override fun state(): SleeperClient.SeasonState? = null
        override fun playerByYahooId(yahooId: String?): SleeperClient.SleeperPlayer? = null
    }

    private fun service(espn: EspnNewsClient) =
        PlayerNewsService(yahooNewsService = null, espn = espn, sleeper = SilentSleeper())

    @Test
    fun `drops a fact that is older than the window`() {
        val espn = FakeEspn(
            injuryRows = mapOf(
                PlayerNames.normalize("Alvin Kamara") to
                        fact("Alvin Kamara", "Active (did not play in the preseason)", "2026-08-20")
            )
        )

        val brief = service(espn).brief(listOf(kamara), today)

        assertTrue(brief.isEmpty, "a 25-day-old camp note is not news on a September move")
    }

    @Test
    fun `keeps a fact from the last game week`() {
        val espn = FakeEspn(
            injuryRows = mapOf(
                PlayerNames.normalize("Alvin Kamara") to
                        fact("Alvin Kamara", "Questionable (knee)", "2026-09-09")
            )
        )

        val brief = service(espn).brief(listOf(kamara), today)

        assertEquals(1, brief.facts.size)
    }

    @Test
    fun `leads with the newest fact, not with the first player in the transaction`() {
        val espn = FakeEspn(
            injuryRows = mapOf(
                PlayerNames.normalize("Jaxson Dart") to
                        fact("Jaxson Dart", "Active (took first-team reps)", "2026-09-08"),
                PlayerNames.normalize("Alvin Kamara") to
                        fact("Alvin Kamara", "Questionable (knee)", "2026-09-13")
            )
        )

        // Dart is the added player and so comes first in the transaction, but Kamara is the
        // one something actually happened to this week.
        val brief = service(espn).brief(listOf(dart, kamara), today)

        assertEquals("Alvin Kamara", brief.facts.first().playerName)
    }

    @Test
    fun `keeps an undated status reading, which cannot go stale`() {
        val espn = FakeEspn(
            injuryRows = mapOf(
                PlayerNames.normalize("Alvin Kamara") to
                        NewsFact("Alvin Kamara", "Listed Questionable - knee", "Yahoo", null)
            )
        )

        val brief = service(espn).brief(listOf(kamara), today)

        assertEquals(1, brief.facts.size)
    }

    @Test
    fun `sorts an undated fact behind a dated one`() {
        val espn = FakeEspn(
            injuryRows = mapOf(
                PlayerNames.normalize("Jaxson Dart") to
                        NewsFact("Jaxson Dart", "Listed Active", "Yahoo", null),
                PlayerNames.normalize("Alvin Kamara") to
                        fact("Alvin Kamara", "Questionable (knee)", "2026-09-13")
            )
        )

        val brief = service(espn).brief(listOf(dart, kamara), today)

        assertEquals("Alvin Kamara", brief.facts.first().playerName)
    }
}
