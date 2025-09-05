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
import org.jsoup.select.Elements
import kotlin.math.abs

fun Observable<Document>.convertToMatchUpObject(): Observable<Pair<Team, Team>> =
    flatMapIterable { doc ->
        println("[MatchUpDataTransformer] Processing document for matchups")
        
        // Get current week from league data
        val currentWeek = doc.select("league > current_week").text().toIntOrNull() ?: 1
        println("[MatchUpDataTransformer] Current week from league data: $currentWeek")
        
        // Get all teams
        val teams = doc.select("league > teams > team")
        println("[MatchUpDataTransformer] Found ${teams.size} teams in league")
        
        // Collect all unique matchups for current week
        val uniqueMatchups = mutableSetOf<String>() // Set to track unique matchup IDs
        val allCurrentWeekMatchups = mutableListOf<Element>()
        
        teams.forEach { team ->
            val teamId = team.select("team_id").text()
            val matchups = team.select("matchups > matchup")
            
            // Filter for current week matchups only
            val currentWeekMatchupsForTeam = matchups.filter { matchup ->
                val week = matchup.select("week").first()?.text()?.toIntOrNull() ?: 0
                week == currentWeek
            }
            
            currentWeekMatchupsForTeam.forEach { matchup ->
                // Create a unique key for this matchup based on the two team IDs
                val matchupTeams = matchup.select("teams > team")
                if (matchupTeams.size >= 2) {
                    val teamsList = matchupTeams.toList()
                    val team1Id = teamsList[0].select("team_id").text()
                    val team2Id = teamsList[1].select("team_id").text()
                    val matchupKey = listOf(team1Id, team2Id).sorted().joinToString("-")
                    
                    if (uniqueMatchups.add(matchupKey)) {
                        // This is a new unique matchup
                        allCurrentWeekMatchups.add(matchup)
                        println("[MatchUpDataTransformer] Added unique matchup: $team1Id vs $team2Id")
                    }
                }
            }
        }
        
        println("[MatchUpDataTransformer] Found ${allCurrentWeekMatchups.size} unique current week matchups")
        allCurrentWeekMatchups
    }.filter { matchup ->
        val teams = matchup.select("teams > team")
        println("[MatchUpDataTransformer] Processing matchup with ${teams.size} teams")
        teams.size >= 2
    }.map { matchup ->
        try {
            val teams = matchup.select("teams > team")
            println("[MatchUpDataTransformer] Mapping matchup - found ${teams.size} teams")
            
            if (teams.size < 2) {
                println("[MatchUpDataTransformer] ERROR: Not enough teams! Expected 2, got ${teams.size}")
                throw IllegalStateException("Not enough teams in matchup")
            }
            
            println("[MatchUpDataTransformer] Teams class: ${teams.javaClass.name}")
            println("[MatchUpDataTransformer] About to access first team...")
            val teamsList = teams.toList()
            println("[MatchUpDataTransformer] Converted to list, size: ${teamsList.size}")
            
            if (teamsList.size < 2) {
                println("[MatchUpDataTransformer] ERROR: List conversion failed! Size: ${teamsList.size}")
                throw IllegalStateException("List conversion resulted in insufficient teams")
            }
            
            val teamOneElement = teamsList[0]
            println("[MatchUpDataTransformer] Successfully got first team element, calling generateTeamData...")
            val teamOne = generateTeamData(teamOneElement)
            
            val teamTwoElement = teamsList[1]
            println("[MatchUpDataTransformer] Successfully got second team element, calling generateTeamData...")
            val teamTwo = generateTeamData(teamTwoElement)
            
            println("[MatchUpDataTransformer] Successfully created pair: ${teamOne.name} vs ${teamTwo.name}")
            Pair(teamOne, teamTwo)
        } catch (e: Exception) {
            println("[MatchUpDataTransformer] Error mapping matchup: ${e.message}")
            println("[MatchUpDataTransformer] Exception type: ${e.javaClass.name}")
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
        println("[MatchUpDataTransformer] Team HTML: ${team.html().take(200)}...")
        
        val id = team.select("team_id").text().toIntOrNull() ?: 0
        println("[MatchUpDataTransformer] Extracted team_id: $id")
        
        val name = team.select("name").text()
        println("[MatchUpDataTransformer] Extracted name: '$name'")
        
        val waiverPriority = team.select("waiver_priority").text().toIntOrNull()
        println("[MatchUpDataTransformer] Extracted waiver_priority: $waiverPriority")
        
        val faabBalance = team.select("faab_balance").text().toIntOrNull()
        println("[MatchUpDataTransformer] Extracted faab_balance: $faabBalance")
        
        val numberOfMoves = team.select("number_of_moves").text().toIntOrNull() ?: 0
        println("[MatchUpDataTransformer] Extracted number_of_moves: $numberOfMoves")
        
        val numberOfTrades = team.select("number_of_trades").text().toIntOrNull() ?: 0
        println("[MatchUpDataTransformer] Extracted number_of_trades: $numberOfTrades")
        
        val winProbabilityText = team.select("win_probability").text()
        println("[MatchUpDataTransformer] Win probability text: '$winProbabilityText'")
        val winProbability = winProbabilityText.toDoubleOrNull()?.times(100) ?: 0.0
        println("[MatchUpDataTransformer] Calculated win_probability: $winProbability")
        
        val pointsText = team.select("team_points").select("total").text()
        println("[MatchUpDataTransformer] Points text: '$pointsText'")
        val points = pointsText.toDoubleOrNull() ?: 0.0
        println("[MatchUpDataTransformer] Calculated points: $points")
        
        val projectedPointsText = team.select("team_projected_points").select("total").text()
        println("[MatchUpDataTransformer] Projected points text: '$projectedPointsText'")
        val projectedPoints = projectedPointsText.toDoubleOrNull() ?: 0.0
        println("[MatchUpDataTransformer] Calculated projected_points: $projectedPoints")
        
        println("[MatchUpDataTransformer] Team data summary - name: $name, id: $id, winProb: $winProbability, projPoints: $projectedPoints")
        
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
        println("[MatchUpDataTransformer] Exception type: ${e.javaClass.name}")
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
