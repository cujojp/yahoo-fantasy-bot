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

package com.landonpatmore.yahoofantasybot.backend.routes

import com.google.gson.Gson
import com.landonpatmore.yahoofantasybot.backend.utils.BackendOAuthManager
import com.landonpatmore.yahoofantasybot.backend.utils.DataRetriever
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory
import com.mashape.unirest.http.Unirest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

fun Route.manualAlertRoutes(db: Db) {
    route("/manual") {
        postManualMatchup(db)
        postManualStandings(db)
        postManualScore(db)
        postManualCloseScore(db)
    }
}

private fun Route.postManualMatchup(db: Db) {
    post("/matchup") {
        try {
            println("[ManualAlert] Triggering manual matchup alert")
            
            // Get team data from Yahoo API
            val oauthManager = BackendOAuthManager(db)
            val dataRetriever = DataRetriever(oauthManager)
            val teamsData = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.TeamsData)
            
            // Parse matchup data
            val matchupData = parseMatchupData(teamsData)
            
            if (matchupData == null) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse matchup data"))
                return@post
            }
            
            // Format message
            val message = formatMatchupMessage(matchupData)
            
            // Send to all configured messaging services
            val results = sendToMessagingServices(db, message, "Matchup")
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Matchup alert sent",
                "data" to message,
                "results" to results
            ))
        } catch (e: Exception) {
            println("[ManualAlert] Error sending matchup alert: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}

private fun Route.postManualStandings(db: Db) {
    post("/standings") {
        try {
            println("[ManualAlert] Triggering manual standings alert")
            
            // Get standings data from Yahoo API
            val oauthManager = BackendOAuthManager(db)
            val dataRetriever = DataRetriever(oauthManager)
            val standingsData = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.Standings)
            
            // Parse standings data
            val standings = parseStandingsData(standingsData)
            
            if (standings.isEmpty()) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse standings data"))
                return@post
            }
            
            // Format message
            val message = formatStandingsMessage(standings)
            
            // Send to all configured messaging services
            val results = sendToMessagingServices(db, message, "Standings")
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Standings alert sent",
                "data" to message,
                "results" to results
            ))
        } catch (e: Exception) {
            println("[ManualAlert] Error sending standings alert: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}

private fun Route.postManualScore(db: Db) {
    post("/score") {
        try {
            println("[ManualAlert] Triggering manual score alert")
            
            // Get team data from Yahoo API
            val oauthManager = BackendOAuthManager(db)
            val dataRetriever = DataRetriever(oauthManager)
            val teamsData = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.TeamsData)
            
            // Parse score data
            val scoreData = parseScoreData(teamsData)
            
            if (scoreData == null) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse score data"))
                return@post
            }
            
            // Format message
            val message = formatScoreMessage(scoreData, false)
            
            // Send to all configured messaging services
            val results = sendToMessagingServices(db, message, "Score")
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Score alert sent",
                "data" to message,
                "results" to results
            ))
        } catch (e: Exception) {
            println("[ManualAlert] Error sending score alert: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}

private fun Route.postManualCloseScore(db: Db) {
    post("/closescore") {
        try {
            println("[ManualAlert] Triggering manual close score alert")
            
            // Get team data from Yahoo API
            val oauthManager = BackendOAuthManager(db)
            val dataRetriever = DataRetriever(oauthManager)
            val teamsData = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.TeamsData)
            
            // Parse score data
            val scoreData = parseScoreData(teamsData)
            
            if (scoreData == null) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse score data"))
                return@post
            }
            
            // Check if it's actually a close score
            val winProbDiff = abs(scoreData.first.winProbability - scoreData.second.winProbability)
            if (winProbDiff >= 40.0) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Not a close score (win probability difference: $winProbDiff%)",
                    "data" to formatScoreMessage(scoreData, true),
                    "results" to emptyMap<String, String>()
                ))
                return@post
            }
            
            // Format message
            val message = formatScoreMessage(scoreData, true)
            
            // Send to all configured messaging services
            val results = sendToMessagingServices(db, message, "CloseScore")
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Close score alert sent",
                "data" to message,
                "results" to results
            ))
        } catch (e: Exception) {
            println("[ManualAlert] Error sending close score alert: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}

// Helper data classes
data class Team(
    val name: String,
    val points: String,
    val projectedPoints: String,
    val winProbability: Double
)

data class StandingTeam(
    val rank: String,
    val name: String,
    val record: String,
    val pointsFor: String,
    val pointsAgainst: String
)

// Helper functions
private fun parseMatchupData(teamsData: String): Pair<Team, Team>? {
    try {
        val doc = Jsoup.parse(teamsData)
        val teams = doc.select("team")
        
        if (teams.size < 2) return null
        
        val teamOne = teams[0]
        val teamTwo = teams[1]
        
        return Pair(
            Team(
                name = teamOne.select("name").text(),
                points = teamOne.select("team_points > total").text(),
                projectedPoints = teamOne.select("team_projected_points > total").text(),
                winProbability = teamOne.select("win_probability").text().toDoubleOrNull() ?: 0.0
            ),
            Team(
                name = teamTwo.select("name").text(),
                points = teamTwo.select("team_points > total").text(),
                projectedPoints = teamTwo.select("team_projected_points > total").text(),
                winProbability = teamTwo.select("win_probability").text().toDoubleOrNull() ?: 0.0
            )
        )
    } catch (e: Exception) {
        println("[ManualAlert] Error parsing matchup data: ${e.message}")
        return null
    }
}

private fun parseScoreData(teamsData: String): Pair<Team, Team>? {
    // Same as parseMatchupData since they use the same data
    return parseMatchupData(teamsData)
}

private fun parseStandingsData(standingsData: String): List<StandingTeam> {
    try {
        val doc = Jsoup.parse(standingsData)
        val teams = doc.select("team")
        
        return teams.map { team: org.jsoup.nodes.Element ->
            StandingTeam(
                rank = team.select("team_standings > rank").text(),
                name = team.select("name").text(),
                record = "${team.select("team_standings > outcome_totals > wins").text()}-${team.select("team_standings > outcome_totals > losses").text()}-${team.select("team_standings > outcome_totals > ties").text()}",
                pointsFor = team.select("team_standings > points_for").text(),
                pointsAgainst = team.select("team_standings > points_against").text()
            )
        }.sortedBy { it.rank.toIntOrNull() ?: 999 }
    } catch (e: Exception) {
        println("[ManualAlert] Error parsing standings data: ${e.message}")
        return emptyList()
    }
}

private fun formatMatchupMessage(matchup: Pair<Team, Team>): String {
    val (teamOne, teamTwo) = matchup
    val teamDataBuilder = StringBuilder()
    teamDataBuilder.append("**${teamOne.name}** vs. **${teamTwo.name}**\\n")
    teamDataBuilder.append(
        "**${teamOne.projectedPoints}** (${teamOne.winProbability.toInt()}%) " +
                "- **${teamTwo.projectedPoints}** (${teamTwo.winProbability.toInt()}%)"
    )
    return teamDataBuilder.toString()
}

private fun formatScoreMessage(score: Pair<Team, Team>, isCloseScore: Boolean): String {
    val (teamOne, teamTwo) = score
    return "**${teamOne.name}** vs. **${teamTwo.name}**\\n" +
            "**${teamOne.points}** (${teamOne.projectedPoints}) - **${teamTwo.points}** (${teamTwo.projectedPoints})"
}

private fun formatStandingsMessage(standings: List<StandingTeam>): String {
    val messages = standings.map { team ->
        "${team.rank}. **${team.name}**\\n" +
        "Record: **${team.record}**\\n" +
        "PF: **${team.pointsFor}** | PA: **${team.pointsAgainst}**"
    }
    return messages.joinToString("\\n\\n")
}

private fun sendToMessagingServices(db: Db, message: String, alertType: String): Map<String, String> {
    val messagingServices = db.getMessagingServices()
    val results = mutableMapOf<String, String>()
    
    messagingServices.forEach { service ->
        val serviceName = when (service.service) {
            0 -> "Discord"
            1 -> "Slack"
            2 -> "GroupMe"
            else -> "Unknown"
        }
        
        if (service.url.isBlank()) {
            results[serviceName] = "Error: Empty webhook URL"
            return@forEach
        }
        
        try {
            val response = when (service.service) {
                0 -> { // Discord
                    Unirest.post(service.url)
                        .header("Content-Type", "application/json")
                        .body("""{"content": "${message.replace("\"", "\\\"").replace("\n", "\\n")}"}""")
                        .asJson()
                }
                1 -> { // Slack
                    Unirest.post(service.url)
                        .header("Content-Type", "application/json")
                        .body("""{"text": "${message.replace("\"", "\\\"").replace("\n", "\\n")}"}""")
                        .asJson()
                }
                2 -> { // GroupMe
                    Unirest.post("https://api.groupme.com/v3/bots/post")
                        .header("Content-Type", "application/json")
                        .body("""{"bot_id": "${service.url}", "text": "${message.replace("\"", "\\\"").replace("\n", "\\n")}"}""")
                        .asJson()
                }
                else -> null
            }
            
            val success = response?.status in 200..299
            results[serviceName] = if (success) "Success" else "Failed: ${response?.status}"
            
            // Save to message history
            db.saveMessageHistory(MessageHistory(
                timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                messagingService = serviceName,
                messageType = MessageHistory.TYPE_ALERT,
                transactionType = null,
                originalMessage = message,
                schefterTweet = null,
                finalContent = message,
                success = success,
                responseCode = response?.status,
                errorMessage = if (!success) "HTTP ${response?.status}" else null,
                playersInvolved = null
            ))
            
        } catch (e: Exception) {
            results[serviceName] = "Error: ${e.message}"
            
            // Save error to message history
            db.saveMessageHistory(MessageHistory(
                timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                messagingService = serviceName,
                messageType = MessageHistory.TYPE_ALERT,
                transactionType = null,
                originalMessage = message,
                schefterTweet = null,
                finalContent = message,
                success = false,
                responseCode = null,
                errorMessage = e.message,
                playersInvolved = null
            ))
        }
    }
    
    return results
}
