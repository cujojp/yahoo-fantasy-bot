package com.landonpatmore.yahoofantasybot.shared.playoffs

/** A team's line in a test standings response. */
data class Row(
    val name: String,
    val wins: Int,
    val losses: Int,
    val ties: Int = 0,
    val clinched: Boolean = false,
    val pointsFor: Double = 1000.0
)

/** A standings response in Yahoo's shape, seeded in the order given. */
fun standingsXml(rows: List<Row>, currentWeek: Int = 14) = buildString {
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

/** A game in a test schedule. Teams are numbered by seed, from 1. */
data class Game(
    val week: Int,
    val home: Int,
    val away: Int,
    val status: String = "preevent",
    val homePoints: Double? = null,
    val awayPoints: Double? = null,
    val homeProjected: Double? = null,
    val awayProjected: Double? = null
)

/** A /teams/matchups response, with each game listed under both of its teams as Yahoo does. */
fun matchupsXml(teamCount: Int, games: List<Game>) = buildString {
    fun side(team: Int, points: Double?, projected: Double?, week: Int) = buildString {
        append("<team><team_key>461.l.1.t.$team</team_key><name>Team $team</name>")
        if (points != null) append("<team_points><coverage_type>week</coverage_type><week>$week</week><total>$points</total></team_points>")
        if (projected != null) append("<team_projected_points><coverage_type>week</coverage_type><week>$week</week><total>$projected</total></team_projected_points>")
        append("</team>")
    }

    append("""<?xml version="1.0" encoding="UTF-8"?><fantasy_content><league><teams count="$teamCount">""")
    (1..teamCount).forEach { team ->
        append("<team><team_key>461.l.1.t.$team</team_key><name>Team $team</name><matchups>")
        games.filter { it.home == team || it.away == team }.forEach { game ->
            append("<matchup><week>${game.week}</week><status>${game.status}</status><teams count=\"2\">")
            append(side(game.home, game.homePoints, game.homeProjected, game.week))
            append(side(game.away, game.awayPoints, game.awayProjected, game.week))
            append("</teams></matchup>")
        }
        append("</matchups></team>")
    }
    append("</teams></league></fantasy_content>")
}
