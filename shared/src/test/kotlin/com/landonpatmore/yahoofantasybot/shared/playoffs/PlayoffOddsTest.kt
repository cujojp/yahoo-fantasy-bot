package com.landonpatmore.yahoofantasybot.shared.playoffs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayoffOddsTest {

    private val settings = PlayoffSettings(numPlayoffTeams = 6, playoffStartWeek = 15)

    private val settingsXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <fantasy_content><league><settings><uses_playoff>1</uses_playoff>
        <playoff_start_week>15</playoff_start_week><num_playoff_teams>6</num_playoff_teams>
        </settings></league></fantasy_content>
    """.trimIndent()

    /**
     * 12 of 14 games played. A has a bye locked up, B a spot, and H is out. E, F and G are
     * level at 6-6 behind D, and E plays F next week.
     */
    private val rows = listOf(
        Row("Team A", 11, 1), Row("Team B", 10, 2), Row("Team C", 8, 4), Row("Team D", 7, 5),
        Row("Team E", 6, 6), Row("Team F", 6, 6), Row("Team G", 6, 6), Row("Team H", 1, 11)
    )

    /** Three played weeks to model scoring from, A best and H worst. */
    private val played: List<Game> = run {
        val averages = listOf(130.0, 125.0, 115.0, 110.0, 105.0, 105.0, 100.0, 80.0)
        val weeks = listOf(
            1 to listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8),
            2 to listOf(1 to 3, 2 to 4, 5 to 7, 6 to 8),
            3 to listOf(1 to 4, 2 to 3, 5 to 8, 6 to 7)
        )
        val swing = mapOf(1 to 10.0, 2 to -10.0, 3 to 0.0)
        fun points(team: Int, week: Int) =
            averages[team - 1] + swing.getValue(week) * if (team % 2 == 0) -1 else 1
        weeks.flatMap { (week, pairs) ->
            pairs.map { (home, away) ->
                Game(week, home, away, "postevent", points(home, week), points(away, week))
            }
        }
    }

    private val toPlay = listOf(
        Game(13, 1, 8), Game(13, 2, 3), Game(13, 4, 7), Game(13, 5, 6),
        Game(14, 1, 7), Game(14, 2, 8), Game(14, 3, 5), Game(14, 4, 6)
    )

    private val standings = PlayoffXml.parseStandings(standingsXml(rows, currentWeek = 13))

    private fun simulate(games: List<Game> = played + toPlay, seed: Long = 7L) =
        PlayoffSimulator.simulate(settings, standings, PlayoffXml.parseMatchups(matchupsXml(8, games)), seed = seed)!!

    private fun PlayoffOdds.of(name: String) =
        teams.getValue(standings.teams.single { it.name == name }.teamKey)

    @Test
    fun `reads each game once, with its scores`() {
        val matchups = PlayoffXml.parseMatchups(matchupsXml(8, played + toPlay))

        assertEquals(20, matchups.size)
        val opener = matchups.first { it.week == 1 && it.home.teamKey == "461.l.1.t.1" }
        assertTrue(opener.isFinal)
        assertEquals(140.0, opener.home.points)
        assertEquals(115.0, opener.away.points)
        assertNull(matchups.first { it.week == 14 }.home.points)
    }

    @Test
    fun `every simulated season fills the field exactly`() {
        val odds = simulate()

        assertEquals(6.0, odds.teams.values.sumOf { it.playoffs }, 1e-9)
        assertEquals(2.0, odds.teams.values.sumOf { it.bye }, 1e-9)
    }

    @Test
    fun `agrees with the math on what is already settled`() {
        val odds = simulate()

        assertEquals(1.0, odds.of("Team A").playoffs)
        assertEquals(1.0, odds.of("Team A").bye)
        assertEquals(1.0, odds.of("Team B").playoffs)
        assertEquals(0.0, odds.of("Team H").playoffs)
    }

    @Test
    fun `better records and better scoring mean better odds`() {
        val odds = simulate()

        assertTrue(odds.of("Team C").playoffs > odds.of("Team D").playoffs)
        assertTrue(odds.of("Team D").playoffs > odds.of("Team E").playoffs)
        // Same record, but E has scored more than G.
        assertTrue(odds.of("Team E").playoffs > odds.of("Team G").playoffs)
    }

    @Test
    fun `the same seed gives the same odds`() {
        assertEquals(simulate(seed = 42L), simulate(seed = 42L))
    }

    @Test
    fun `a week under way uses Yahoo's live projection`() {
        val live = toPlay.map {
            if (it.week == 13 && it.home == 5) it.copy(status = "midevent", homeProjected = 160.0, awayProjected = 60.0) else it
        }

        assertTrue(simulate(played + live).of("Team E").playoffs > simulate().of("Team E").playoffs + 0.1)
    }

    @Test
    fun `the game of the week is the head-to-head on the bubble`() {
        val game = simulate().gameOfTheWeek
        assertNotNull(game)
        game!!

        assertEquals(13, game.week)
        assertEquals("461.l.1.t.5", game.home)
        assertEquals("461.l.1.t.6", game.away)
        assertTrue(game.homeSwing > 0.1 && game.awaySwing > 0.1)
    }

    @Test
    fun `formats the odds with what is certain shown as certain`() {
        val picture = PlayoffPicture.build(settings, standings)
        val message = PlayoffOddsMessage.format(picture, simulate())
        val lines = message.split("\\n")

        assertEquals("Week 13 · 2 weeks left · 10,000 simulated seasons", lines[0])
        assertTrue("1. **Team A** (11-1) · ✅ clinched a bye" in lines)
        assertTrue(lines.any { it.startsWith("2. **Team B** (10-2) · ✅ clinched") })
        assertTrue("━━ cut line ━━" in lines)
        assertTrue(lines.none { it.contains("0 points") })
        assertTrue("❌ Eliminated: Team H" in lines)
        assertTrue(lines.any { it.startsWith("📈 Game of the week: **Team E** vs **Team F**. Winning adds ") })
    }

    @Test
    fun `never rounds a team that is still alive to a certainty`() {
        val picture = PlayoffPicture.build(settings, standings)
        val keys = standings.teams.associate { it.name to it.teamKey }
        val odds = PlayoffOdds(
            simulations = 1000,
            teams = mapOf(
                keys.getValue("Team D") to TeamOdds(0.999, 0.0),
                keys.getValue("Team G") to TeamOdds(0.001, 0.0),
                keys.getValue("Team E") to TeamOdds(0.42, 0.031)
            ),
            gameOfTheWeek = null
        )
        val lines = PlayoffOddsMessage.format(picture, odds).split("\\n")

        assertTrue("4. **Team D** (7-5) · >99% in" in lines)
        assertTrue("5. **Team E** (6-6) · 42% in · 3% bye" in lines)
        assertTrue("7. **Team G** (6-6) · <1% in" in lines)
    }

    @Test
    fun `posts nothing when there is nothing to predict`() {
        val noGamesPlayed = PlayoffPosts.odds(settingsXml, standingsXml(rows, 13), matchupsXml(8, toPlay))
        assertTrue(noGamesPlayed is PlayoffPosts.Result.Skip)

        val seasonOver = rows.map { it.copy(losses = it.losses + 2) }
        val over = PlayoffPosts.odds(settingsXml, standingsXml(seasonOver, 15), matchupsXml(8, played))
        assertEquals(
            PlayoffPosts.Result.Skip("The regular season is over, so there are no odds left to run"),
            over
        )

        val posted = PlayoffPosts.odds(settingsXml, standingsXml(rows, 13), matchupsXml(8, played + toPlay))
        assertTrue(posted is PlayoffPosts.Result.Post)
    }
}
