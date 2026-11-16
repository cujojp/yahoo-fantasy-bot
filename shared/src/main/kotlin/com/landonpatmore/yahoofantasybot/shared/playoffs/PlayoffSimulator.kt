package com.landonpatmore.yahoofantasybot.shared.playoffs

import kotlin.math.max
import kotlin.math.sqrt

/** One side of a matchup. Points are null until the week has started. */
data class MatchupSide(
    val teamKey: String,
    val points: Double?,
    val projectedPoints: Double?
)

/** A head-to-head game, played or still to come. */
data class ScheduledMatchup(
    val week: Int,
    /** Yahoo's status: preevent, midevent or postevent. */
    val status: String,
    val home: MatchupSide,
    val away: MatchupSide
) {
    val isFinal: Boolean get() = status == "postevent"
}

data class TeamOdds(val playoffs: Double, val bye: Double)

/** The game whose result moves the playoff race the most. */
data class GameOfTheWeek(
    val week: Int,
    val home: String,
    val away: String,
    /** How much winning instead of losing adds to each team's playoff odds, 0 to 1. */
    val homeSwing: Double,
    val awaySwing: Double
)

data class PlayoffOdds(
    val simulations: Int,
    val teams: Map<String, TeamOdds>,
    val gameOfTheWeek: GameOfTheWeek?
)

/**
 * Plays out the rest of the regular season many times over and counts how often each
 * team makes the field.
 *
 * Each team's score in a game is drawn from a normal curve around its average this
 * season, pulled a little toward the league average so two big weeks don't make a team
 * a lock. The spread is the league's week-to-week swing around each team's own average.
 * For a week already under way, Yahoo's live projection replaces the average.
 *
 * Records are ranked on wins, with a tie worth half, then points for. Divisions are not
 * modelled, so a league that seeds division winners first will see some error.
 */
object PlayoffSimulator {
    const val DEFAULT_SIMULATIONS = 10_000

    /** How many league-average weeks a team's own average is blended with. */
    private const val SHRINK_WEEKS = 2.0

    /** Used when there are too few games to measure the league's spread. */
    private const val FALLBACK_SPREAD = 25.0

    /** A game is only the game of the week if both results show up this often. */
    private const val MIN_OUTCOME_SHARE = 0.02

    /** Null when no games have been played yet, so there is no scoring to model. */
    fun simulate(
        settings: PlayoffSettings,
        standings: LeagueStandings,
        matchups: List<ScheduledMatchup>,
        simulations: Int = DEFAULT_SIMULATIONS,
        seed: Long = defaultSeed(standings)
    ): PlayoffOdds? {
        val teams = standings.teams
        val index = teams.withIndex().associate { (i, team) -> team.teamKey to i }
        val known = matchups.filter { it.home.teamKey in index && it.away.teamKey in index }

        val scores = Array(teams.size) { mutableListOf<Double>() }
        known.filter { it.isFinal }.forEach { game ->
            listOf(game.home, game.away).forEach { side ->
                side.points?.let { scores[index.getValue(side.teamKey)] += it }
            }
        }
        val allScores = scores.flatMap { it }
        if (allScores.isEmpty()) return null

        val leagueAverage = allScores.average()
        val averages = DoubleArray(teams.size) { i ->
            val own = scores[i]
            (own.sum() + SHRINK_WEEKS * leagueAverage) / (own.size + SHRINK_WEEKS)
        }
        val spread = spread(scores)

        val remaining = known.filter {
            !it.isFinal && it.week >= standings.startWeek && it.week < settings.playoffStartWeek
        }
        val nextWeek = remaining.minOfOrNull { it.week }
        val nextGames = remaining.filter { it.week == nextWeek }

        val spots = settings.numPlayoffTeams.coerceAtMost(teams.size)
        val byes = settings.numByes.coerceAtMost(spots)
        val random = java.util.Random(seed)

        val madePlayoffs = IntArray(teams.size)
        val gotBye = IntArray(teams.size)
        // For each game next week: how often home won, and how often each side made the
        // field with a win and with a loss.
        val homeWins = IntArray(nextGames.size)
        val homeInWithWin = IntArray(nextGames.size)
        val homeInWithLoss = IntArray(nextGames.size)
        val awayInWithWin = IntArray(nextGames.size)
        val awayInWithLoss = IntArray(nextGames.size)

        val baseRecord = DoubleArray(teams.size) { teams[it].score }
        val basePointsFor = DoubleArray(teams.size) { teams[it].pointsFor }

        repeat(simulations) {
            val record = baseRecord.copyOf()
            val pointsFor = basePointsFor.copyOf()
            val homeWon = BooleanArray(nextGames.size)

            remaining.forEach { game ->
                val home = index.getValue(game.home.teamKey)
                val away = index.getValue(game.away.teamKey)
                val homePoints = draw(random, game, game.home, averages[home], spread)
                val awayPoints = draw(random, game, game.away, averages[away], spread)
                pointsFor[home] += homePoints
                pointsFor[away] += awayPoints
                if (homePoints >= awayPoints) record[home] += 1.0 else record[away] += 1.0

                val next = nextGames.indexOf(game)
                if (next >= 0) homeWon[next] = homePoints >= awayPoints
            }

            val finish = teams.indices.sortedWith(
                compareByDescending<Int> { record[it] }.thenByDescending { pointsFor[it] }
            )
            val inField = BooleanArray(teams.size)
            finish.take(spots).forEach { inField[it] = true; madePlayoffs[it]++ }
            finish.take(byes).forEach { gotBye[it]++ }

            nextGames.forEachIndexed { g, game ->
                val home = inField[index.getValue(game.home.teamKey)]
                val away = inField[index.getValue(game.away.teamKey)]
                if (homeWon[g]) {
                    homeWins[g]++
                    if (home) homeInWithWin[g]++
                    if (away) awayInWithLoss[g]++
                } else {
                    if (home) homeInWithLoss[g]++
                    if (away) awayInWithWin[g]++
                }
            }
        }

        val odds = teams.indices.associate { i ->
            teams[i].teamKey to TeamOdds(
                playoffs = madePlayoffs[i].toDouble() / simulations,
                bye = gotBye[i].toDouble() / simulations
            )
        }

        val minOutcomes = (simulations * MIN_OUTCOME_SHARE).toInt().coerceAtLeast(1)
        val gameOfTheWeek = nextGames.indices
            .filter { homeWins[it] >= minOutcomes && simulations - homeWins[it] >= minOutcomes }
            .map { g ->
                val wins = homeWins[g].toDouble()
                val losses = (simulations - homeWins[g]).toDouble()
                GameOfTheWeek(
                    week = nextGames[g].week,
                    home = nextGames[g].home.teamKey,
                    away = nextGames[g].away.teamKey,
                    homeSwing = homeInWithWin[g] / wins - homeInWithLoss[g] / losses,
                    awaySwing = awayInWithWin[g] / losses - awayInWithLoss[g] / wins
                )
            }
            .maxByOrNull { it.homeSwing + it.awaySwing }
            ?.takeIf { it.homeSwing + it.awaySwing > 0.0 }

        return PlayoffOdds(simulations, odds, gameOfTheWeek)
    }

    /**
     * The league's week-to-week swing: how far scores land from each team's own average,
     * pooled across the league so one team's short history doesn't set its own spread.
     */
    private fun spread(scores: Array<MutableList<Double>>): Double {
        var squares = 0.0
        var degrees = 0
        scores.filter { it.size > 1 }.forEach { own ->
            val average = own.average()
            own.forEach { squares += (it - average) * (it - average) }
            degrees += own.size - 1
        }
        return if (degrees < 4) FALLBACK_SPREAD else sqrt(squares / degrees)
    }

    private fun draw(
        random: java.util.Random,
        game: ScheduledMatchup,
        side: MatchupSide,
        average: Double,
        spread: Double
    ): Double {
        val center = side.projectedPoints?.takeIf { game.status == "midevent" && it > 0.0 } ?: average
        return max(0.0, center + spread * random.nextGaussian())
    }

    /** The same standings always give the same odds, so a re-sent post doesn't change. */
    private fun defaultSeed(standings: LeagueStandings): Long =
        standings.teams.joinToString { "${it.teamKey}:${it.record}:${it.pointsFor}" }.hashCode().toLong()
}
