/*
 * MIT License
 *
 * Copyright (c) 2025 Kaleb White
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.landonpatmore.yahoofantasybot.bot.transformers

import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import com.landonpatmore.yahoofantasybot.bot.utils.bold
import com.landonpatmore.yahoofantasybot.bot.utils.toPercentage
import io.reactivex.rxjava3.core.Observable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.abs

fun Observable<Document>.convertToMatchUpObject(): Observable<Pair<Team, Team>> =
    flatMapIterable { doc ->
        println("[MatchUpDataTransformer] Processing document for matchups")
        
        // For teams/matchups endpoint, we need to get matchups from the team structure
        // Each team has all matchups for the season, so we just need to get them from one team
        val firstTeam = doc.select("league > teams > team").firstOrNull()
        if (firstTeam == null) {
            println("[MatchUpDataTransformer] No teams found in document")
            return@flatMapIterable emptyList<Element>()
        }
        
        val matchups = firstTeam.select("matchups > matchup")
        println("[MatchUpDataTransformer] Found ${matchups.size} matchups from first team")
        
        // Debug: Log first few matchups structure
        matchups.take(3).forEach { matchup ->
            val week = matchup.select("week").first()?.text() ?: "0"
            val teamsCount = matchup.select("teams > team").size
            println("[MatchUpDataTransformer] Sample matchup - week: $week, teams: $teamsCount")
        }
        
        // Get current week from league data
        val currentWeek = doc.select("league > current_week").text().toIntOrNull() ?: 1
        println("[MatchUpDataTransformer] Current week from league data: $currentWeek")
        
        // Filter for current week matchups only
        val currentWeekMatchups = matchups.filter { matchup ->
            val week = matchup.select("week").first()?.text()?.toIntOrNull() ?: 0
            week == currentWeek
        }
        
        println("[MatchUpDataTransformer] Found ${currentWeekMatchups.size} current week matchups")
        currentWeekMatchups
    }.filter { matchup ->
        val teams = matchup.select("teams > team")
        println("[MatchUpDataTransformer] Processing matchup with ${teams.size} teams")
        teams.size >= 2
    }.map { matchup ->
        try {
            val teams = matchup.select("teams > team")
            println("[MatchUpDataTransformer] Mapping matchup - found ${teams.size} teams")
            val teamOne = generateTeamData(teams[0])
            val teamTwo = generateTeamData(teams[1])
            println("[MatchUpDataTransformer] Successfully created pair: ${teamOne.name} vs ${teamTwo.name}")
            Pair(teamOne, teamTwo)
        } catch (e: Exception) {
            println("[MatchUpDataTransformer] Error mapping matchup: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

fun Observable<Pair<Team, Team>>.convertToMatchUpMessage(): Observable<Message> =
    map {
        println("[MatchUpDataTransformer] Creating matchup message for ${it.first.name} vs ${it.second.name}")
        val teamDataBuilder = StringBuilder()
        teamDataBuilder.append("${it.first.name.bold()} vs. ${it.second.name.bold()}\\n")
        teamDataBuilder.append(
            "${it.first.projectedPoints.bold()} (${it.first.winProbability.toPercentage()}) " +
                    "- ${it.second.projectedPoints.bold()} (${it.second.winProbability.toPercentage()})"
        )

        val message = Message.MatchUp(teamDataBuilder.toString())
        println("[MatchUpDataTransformer] Created message: ${message.message}")
        message
    }

fun Observable<Pair<Team, Team>>.convertToScoreUpdateMessage(closeScoreUpdate: Boolean = false): Observable<Message> =
    filter {
        if (closeScoreUpdate) {
            abs(it.first.winProbability - it.second.winProbability) < 40.0
        } else {
            true
        }
    }.map {
        val message = "${it.first.name.bold()} vs. ${it.second.name.bold()}\\n" +
                "${it.first.points.bold()} (${it.first.projectedPoints})" +
                " - " +
                "${it.second.points.bold()} (${it.second.projectedPoints})"

        if (closeScoreUpdate) {
            Message.CloseScore(message)
        } else {
            Message.Score(message)
        }
    }

private fun generateTeamData(team: Element): Team {
    try {
        println("[MatchUpDataTransformer] Generating team data from element: ${team.tagName()}")
        
        val id = team.select("team_id").text().toIntOrNull() ?: 0
        val name = team.select("name").text()
        val waiverPriority = team.select("waiver_priority").text().toIntOrNull()
        val faabBalance = team.select("faab_balance").text().toIntOrNull()
        val numberOfMoves = team.select("number_of_moves").text().toIntOrNull() ?: 0
        val numberOfTrades = team.select("number_of_trades").text().toIntOrNull() ?: 0
        val winProbability = team.select("win_probability").text().toDoubleOrNull()?.times(100) ?: 0.0
        val points = team.select("team_points").select("total").text().toDoubleOrNull() ?: 0.0
        val projectedPoints = team.select("team_projected_points").select("total").text().toDoubleOrNull() ?: 0.0
        
        println("[MatchUpDataTransformer] Team data - name: $name, id: $id, winProb: $winProbability, projPoints: $projectedPoints")
        
        if (name.isEmpty()) {
            println("[MatchUpDataTransformer] WARNING: Team name is empty")
        }
        
        return Team(
            name,
            id,
            waiverPriority,
            faabBalance,
            numberOfMoves,
            numberOfTrades,
            winProbability,
            points,
            projectedPoints
        )
    } catch (e: Exception) {
        println("[MatchUpDataTransformer] Error generating team data: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

data class Team(
    val name: String,
    val id: Int,
    val waiverPriority: Int?,
    val faabBalance: Int?,
    val numberOfMoves: Int,
    val numberOfTrades: Int,
    val winProbability: Double,
    val points: Double,
    val projectedPoints: Double
)
