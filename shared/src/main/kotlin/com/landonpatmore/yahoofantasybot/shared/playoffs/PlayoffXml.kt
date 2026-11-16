package com.landonpatmore.yahoofantasybot.shared.playoffs

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Reads the Yahoo league settings and standings responses.
 *
 * Takes the raw XML rather than a parsed document because the bot and the backend each
 * have their own jsoup, and the bot's is a much older version.
 */
object PlayoffXml {

    /** Null when the league has no playoffs, or the response is not a settings document. */
    fun parseSettings(xml: String): PlayoffSettings? {
        val settings = Jsoup.parse(xml, "", Parser.xmlParser())
            .select("league > settings").first() ?: return null

        if (settings.childText("uses_playoff") == "0") return null

        val numPlayoffTeams = settings.childText("num_playoff_teams")?.toIntOrNull() ?: return null
        val playoffStartWeek = settings.childText("playoff_start_week")?.toIntOrNull() ?: return null
        if (numPlayoffTeams <= 0) return null

        return PlayoffSettings(numPlayoffTeams, playoffStartWeek)
    }

    fun parseStandings(xml: String): LeagueStandings {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val league = doc.select("league").first()

        val teams = doc.select("league > standings > teams > team").mapIndexed { index, team ->
            val standings = team.children().firstOrNull { it.tagName() == "team_standings" }
            val totals = standings?.children()?.firstOrNull { it.tagName() == "outcome_totals" }
            val clinched = team.select("clinched_playoffs").first()?.text().orEmpty()

            TeamRecord(
                teamKey = team.childText("team_key").orEmpty(),
                // Read the team's own <name>. The managers and logos sit beneath the team
                // too, and a loose select is what once glued every team name together.
                name = team.childText("name").orEmpty(),
                // playoff_seed is the one that matters in a league with divisions. Fall back
                // to the overall rank, then to Yahoo's order.
                seed = standings?.childText("playoff_seed")?.toIntOrNull()
                    ?: standings?.childText("rank")?.toIntOrNull()
                    ?: (index + 1),
                wins = totals?.childText("wins")?.toIntOrNull() ?: 0,
                losses = totals?.childText("losses")?.toIntOrNull() ?: 0,
                ties = totals?.childText("ties")?.toIntOrNull() ?: 0,
                pointsFor = standings?.childText("points_for")?.toDoubleOrNull() ?: 0.0,
                clinchedByYahoo = clinched.isNotEmpty() && clinched != "0"
            )
        }

        return LeagueStandings(
            currentWeek = league?.childText("current_week")?.toIntOrNull(),
            startWeek = league?.childText("start_week")?.toIntOrNull() ?: 1,
            teams = teams
        )
    }

    private fun Element.childText(tag: String): String? =
        children().firstOrNull { it.tagName() == tag }?.text()
}
