package com.landonpatmore.yahoofantasybot.shared.playoffs

/** The parts of the league settings that shape the playoff race. */
data class PlayoffSettings(
    val numPlayoffTeams: Int,
    val playoffStartWeek: Int
) {
    /**
     * Yahoo fills the bracket out to a power of two with first-round byes for the top
     * seeds, so a 6-team field gives seeds 1 and 2 a bye, and a 4 or 8-team field none.
     */
    val numByes: Int
        get() {
            var bracket = 1
            while (bracket < numPlayoffTeams) bracket *= 2
            return bracket - numPlayoffTeams
        }
}

/** One team's line in the league standings. */
data class TeamRecord(
    val teamKey: String,
    val name: String,
    val seed: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val pointsFor: Double,
    /** Yahoo's own clinched flag, which can come in before our math catches up. */
    val clinchedByYahoo: Boolean
) {
    val gamesPlayed: Int get() = wins + losses + ties

    /** A tie is worth half a win, the same as in Yahoo's win percentage. */
    internal val score: Double get() = wins + ties / 2.0

    val record: String get() = if (ties > 0) "$wins-$losses-$ties" else "$wins-$losses"
}

/** The standings as Yahoo returns them, plus the league weeks needed to count games. */
data class LeagueStandings(
    val currentWeek: Int?,
    val startWeek: Int,
    val teams: List<TeamRecord>
)

enum class PlayoffStatus { CLINCHED_BYE, CLINCHED, IN, OUT, ELIMINATED }

data class PlayoffTeam(
    val team: TeamRecord,
    val status: PlayoffStatus,
    val gamesRemaining: Int,
    /** Games behind the last team in the field. Null for teams in the field. */
    val gamesBack: Double?
)

data class PlayoffPicture(
    val currentWeek: Int?,
    val settings: PlayoffSettings,
    /** Every team, best seed first. */
    val teams: List<PlayoffTeam>
) {
    /** How many playoff spots there really are, in case the field is bigger than the league. */
    val spots: Int get() = settings.numPlayoffTeams.coerceAtMost(teams.size)

    val gamesRemaining: Int get() = teams.maxOfOrNull { it.gamesRemaining } ?: 0

    companion object {
        /**
         * Works out where every team stands.
         *
         * Clinching and elimination are only called when they are certain on wins and
         * losses alone. A tie in the standings could go either way on the tiebreaker, so
         * it never counts in a team's favour. That keeps the bot from calling a team in, or
         * out, before it really is.
         */
        fun build(settings: PlayoffSettings, standings: LeagueStandings): PlayoffPicture {
            // One game a week from the first week up to the week before the playoffs.
            val totalGames = (settings.playoffStartWeek - standings.startWeek).coerceAtLeast(0)
            val ordered = standings.teams.sortedBy { it.seed }
            val spots = settings.numPlayoffTeams.coerceAtMost(ordered.size)
            val lastIn = ordered.getOrNull(spots - 1)

            fun remaining(team: TeamRecord) = (totalGames - team.gamesPlayed).coerceAtLeast(0)

            // In the top `places` whatever happens: fewer than `places` other teams can
            // still reach this team's current record, even if this team loses out.
            fun locksTop(team: TeamRecord, places: Int): Boolean {
                if (places <= 0) return false
                val canCatchUp = ordered.count {
                    it.teamKey != team.teamKey && it.score + remaining(it) >= team.score
                }
                return canCatchUp < places
            }

            // Out whatever happens: even winning every game left, at least `spots` teams
            // already have a better record than this team could finish with.
            fun eliminated(team: TeamRecord): Boolean {
                val best = team.score + remaining(team)
                return ordered.count { it.teamKey != team.teamKey && it.score > best } >= spots
            }

            val teams = ordered.mapIndexed { index, team ->
                val status = when {
                    locksTop(team, settings.numByes) -> PlayoffStatus.CLINCHED_BYE
                    team.clinchedByYahoo || locksTop(team, spots) -> PlayoffStatus.CLINCHED
                    index < spots -> PlayoffStatus.IN
                    eliminated(team) -> PlayoffStatus.ELIMINATED
                    else -> PlayoffStatus.OUT
                }
                val gamesBack = if (index < spots || lastIn == null) {
                    null
                } else {
                    ((lastIn.wins - team.wins) + (team.losses - lastIn.losses)) / 2.0
                }
                PlayoffTeam(team, status, remaining(team), gamesBack)
            }

            return PlayoffPicture(standings.currentWeek, settings, teams)
        }
    }
}
