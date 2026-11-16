package com.landonpatmore.yahoofantasybot.shared.playoffs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayoffPictureTest {

    private fun settingsXml(body: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <fantasy_content><league><league_key>461.l.1</league_key><end_week>17</end_week>
          <settings><draft_type>live</draft_type>$body</settings>
        </league></fantasy_content>
    """.trimIndent()

    private data class Row(
        val name: String,
        val wins: Int,
        val losses: Int,
        val ties: Int = 0,
        val clinched: Boolean = false,
        val pointsFor: Double = 1000.0
    )

    /** A standings response in Yahoo's shape, seeded in the order given. */
    private fun standingsXml(rows: List<Row>, currentWeek: Int = 14) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><fantasy_content><league>""")
        append("<current_week>$currentWeek</current_week><start_week>1</start_week>")
        append("<standings><teams count=\"${rows.size}\">")
        rows.forEachIndexed { i, row ->
            append("<team><team_key>461.l.1.t.${i + 1}</team_key><name>${row.name}</name>")
            append("<managers><manager><nickname>Manager ${i + 1}</nickname></manager></managers>")
            if (row.clinched) append("<clinched_playoffs>1</clinched_playoffs>")
            append("<team_standings><rank>${i + 1}</rank><playoff_seed>${i + 1}</playoff_seed>")
            append("<outcome_totals><wins>${row.wins}</wins><losses>${row.losses}</losses><ties>${row.ties}</ties></outcome_totals>")
            append("<points_for>${row.pointsFor}</points_for><points_against>900</points_against>")
            append("</team_standings></team>")
        }
        append("</teams></standings></league></fantasy_content>")
    }

    private val sixTeamPlayoff = PlayoffSettings(numPlayoffTeams = 6, playoffStartWeek = 15)

    /** 13 of 14 regular season games played, one week left. */
    private val oneWeekLeft = listOf(
        Row("Team A", 12, 1), Row("Team B", 10, 3), Row("Team C", 9, 4),
        Row("Team D", 8, 5), Row("Team E", 7, 6), Row("Team F", 7, 6),
        Row("Team G", 7, 6), Row("Team H", 6, 7), Row("Team I", 5, 8),
        Row("Team J", 5, 8), Row("Team K", 4, 9), Row("Team L", 2, 11)
    )

    private fun picture(rows: List<Row>, settings: PlayoffSettings = sixTeamPlayoff) =
        PlayoffPicture.build(settings, PlayoffXml.parseStandings(standingsXml(rows)))

    private fun PlayoffPicture.status(name: String) = teams.single { it.team.name == name }.status

    @Test
    fun `reads the playoff settings`() {
        val settings = PlayoffXml.parseSettings(settingsXml(
            "<uses_playoff>1</uses_playoff><playoff_start_week>15</playoff_start_week><num_playoff_teams>6</num_playoff_teams>"
        ))
        assertEquals(PlayoffSettings(6, 15), settings)
    }

    @Test
    fun `no settings when the league has no playoffs or the response is something else`() {
        assertNull(PlayoffXml.parseSettings(settingsXml(
            "<uses_playoff>0</uses_playoff><playoff_start_week>15</playoff_start_week><num_playoff_teams>6</num_playoff_teams>"
        )))
        assertNull(PlayoffXml.parseSettings(settingsXml("<uses_playoff>1</uses_playoff>")))
        assertNull(PlayoffXml.parseSettings("<error><description>Please provide valid credentials</description></error>"))
    }

    @Test
    fun `byes fill the bracket out to a power of two`() {
        assertEquals(2, PlayoffSettings(6, 15).numByes)
        assertEquals(0, PlayoffSettings(4, 15).numByes)
        assertEquals(0, PlayoffSettings(8, 15).numByes)
    }

    @Test
    fun `reads each team's own name, record and clinched flag`() {
        val standings = PlayoffXml.parseStandings(standingsXml(listOf(
            Row("Dead Corps", 6, 3, ties = 1, clinched = true, pointsFor = 1234.56),
            Row("Samuel Lamar Jackson", 5, 5)
        ), currentWeek = 11))

        assertEquals(11, standings.currentWeek)
        assertEquals(1, standings.startWeek)
        val first = standings.teams.first()
        assertEquals("Dead Corps", first.name)
        assertEquals("6-3-1", first.record)
        assertEquals(1, first.seed)
        assertEquals(1234.56, first.pointsFor)
        assertTrue(first.clinchedByYahoo)
        assertEquals("5-5", standings.teams[1].record)
        assertFalse(standings.teams[1].clinchedByYahoo)
    }

    @Test
    fun `clinches only what is certain on wins and losses`() {
        val picture = picture(oneWeekLeft)

        assertEquals(PlayoffStatus.CLINCHED_BYE, picture.status("Team A"))
        // Team C can still tie Team B at 10 wins, so B has clinched a spot but not the bye.
        assertEquals(PlayoffStatus.CLINCHED, picture.status("Team B"))
        assertEquals(PlayoffStatus.CLINCHED, picture.status("Team C"))
        // Six teams can still finish at 8 or better, so Team D is not safe yet.
        assertEquals(PlayoffStatus.IN, picture.status("Team D"))
        assertEquals(PlayoffStatus.IN, picture.status("Team F"))
    }

    @Test
    fun `eliminates only teams that cannot catch the field`() {
        val picture = picture(oneWeekLeft)

        // Team H can still reach 7-7 and tie for the last spot.
        assertEquals(PlayoffStatus.OUT, picture.status("Team H"))
        // Team I's best is 6-8, and seven teams already have 7 wins.
        assertEquals(PlayoffStatus.ELIMINATED, picture.status("Team I"))
        assertEquals(PlayoffStatus.ELIMINATED, picture.status("Team L"))
    }

    @Test
    fun `a tie is worth half a win`() {
        // Team G at 6-6-1 can reach 7.5, past Team C's 7, so C has not clinched.
        val picture = picture(listOf(
            Row("Team A", 13, 0), Row("Team B", 12, 1), Row("Team C", 7, 6),
            Row("Team D", 7, 6), Row("Team E", 7, 6), Row("Team F", 7, 6),
            Row("Team G", 6, 6, ties = 1), Row("Team H", 0, 13)
        ))
        assertEquals(PlayoffStatus.OUT, picture.status("Team G"))
        assertEquals(PlayoffStatus.IN, picture.status("Team C"))
        assertEquals(PlayoffStatus.ELIMINATED, picture.status("Team H"))
    }

    @Test
    fun `trusts Yahoo's clinched flag when it is ahead of the math`() {
        val rows = oneWeekLeft.toMutableList()
        rows[3] = rows[3].copy(clinched = true)
        assertEquals(PlayoffStatus.CLINCHED, picture(rows).status("Team D"))
    }

    @Test
    fun `formats the picture as one post with the cut line`() {
        val lines = PlayoffPictureMessage.format(picture(oneWeekLeft)).split("\\n")

        assertEquals(
            listOf(
                "Week 14 · Final week of the regular season · top 6 make it, top 2 get a bye",
                "",
                "1. **Team A** (12-1) ✅ clinched a bye",
                "2. **Team B** (10-3) ✅ clinched",
                "3. **Team C** (9-4) ✅ clinched",
                "4. **Team D** (8-5)",
                "5. **Team E** (7-6)",
                "6. **Team F** (7-6) · last spot in",
                "━━ cut line ━━",
                "7. **Team G** (7-6) · tied for the last spot, out on tiebreaker",
                "8. **Team H** (6-7) · 1 GB",
                "",
                "❌ Eliminated: Team I, Team J, Team K, Team L",
                "",
                "🔥 Bubble watch: Team G and Team H are within a game of the last spot."
            ),
            lines
        )
    }

    @Test
    fun `early in the season nobody is clinched or out and the bye spots are marked`() {
        val rows = listOf(
            Row("Team A", 4, 0), Row("Team B", 3, 1), Row("Team C", 3, 1),
            Row("Team D", 2, 2), Row("Team E", 2, 2), Row("Team F", 2, 2),
            Row("Team G", 1, 3), Row("Team H", 0, 4)
        )
        val message = PlayoffPictureMessage.format(picture(rows))

        assertTrue(message.startsWith("Week 14 · 10 weeks left"))
        assertTrue(message.contains("1. **Team A** (4-0) · bye spot"))
        assertTrue(message.contains("8. **Team H** (0-4) · 2 GB"))
        assertFalse(message.contains("✅"))
        assertFalse(message.contains("Eliminated"))
    }

    @Test
    fun `once the regular season is over there is no cut line or bubble for teams already out`() {
        val rows = listOf(
            Row("Team A", 12, 2), Row("Team B", 10, 4), Row("Team C", 9, 5),
            Row("Team D", 8, 6), Row("Team E", 3, 11)
        )
        val message = PlayoffPictureMessage.format(picture(rows, PlayoffSettings(4, 15)))

        assertTrue(message.startsWith("Week 14 · Regular season over · top 4 make it"))
        assertFalse(message.contains("cut line"))
        assertFalse(message.contains("Bubble"))
        assertTrue(message.contains("❌ Eliminated: Team E"))
    }
}
