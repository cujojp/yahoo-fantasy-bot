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
import com.landonpatmore.yahoofantasybot.shared.database.models.MessagingService
import com.landonpatmore.yahoofantasybot.shared.messaging.AlertFormatting
import com.mashape.unirest.http.Unirest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.DecimalFormat
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

            // Parse every matchup in the current week
            val matchups = parseMatchups(teamsData)

            if (matchups.isEmpty()) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse matchup data"))
                return@post
            }

            // Format one message per matchup, the same as the scheduled alert
            val messages = matchups.map { formatMatchupMessage(it) }

            // Send to all configured messaging services
            val results = sendToMessagingServices(db, messages, AlertFormatting.MATCH_UP)

            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Matchup alert sent (${messages.size} matchups)",
                "data" to messages,
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

            // One message per team, the same as the scheduled alert
            val messages = standings.map { formatStandingsMessage(it) }

            // Send to all configured messaging services
            val results = sendToMessagingServices(db, messages, AlertFormatting.STANDINGS)

            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Standings alert sent (${messages.size} teams)",
                "data" to messages,
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

            // Parse every matchup in the current week
            val matchups = parseMatchups(teamsData)

            if (matchups.isEmpty()) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse score data"))
                return@post
            }

            // Format one message per matchup, the same as the scheduled alert
            val messages = matchups.map { formatScoreMessage(it) }

            // Send to all configured messaging services
            val results = sendToMessagingServices(db, messages, AlertFormatting.SCORE)

            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Score alert sent (${messages.size} matchups)",
                "data" to messages,
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

            // Parse every matchup in the current week
            val matchups = parseMatchups(teamsData)

            if (matchups.isEmpty()) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to parse score data"))
                return@post
            }

            // Keep only the matchups that are actually close
            val closeMatchups = matchups.filter { isCloseScore(it) }

            if (closeMatchups.isEmpty()) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "No close matchups this week (none within 40% win probability)",
                    "data" to matchups.map { formatScoreMessage(it) },
                    "results" to emptyMap<String, String>()
                ))
                return@post
            }

            // Format one message per close matchup, the same as the scheduled alert
            val messages = closeMatchups.map { formatScoreMessage(it) }

            // Send to all configured messaging services
            val results = sendToMessagingServices(db, messages, AlertFormatting.CLOSE_SCORE)

            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Close score alert sent (${messages.size} of ${matchups.size} matchups)",
                "data" to messages,
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
    val streak: String?,
    val pointsFor: String,
    val pointsAgainst: String,
    val clinchedPlayoffs: Boolean
)

// Helper functions
private fun parseMatchups(teamsData: String): List<Pair<Team, Team>> {
    return try {
        val doc = Jsoup.parse(teamsData, "", Parser.xmlParser())
        val currentWeek = doc.select("league > current_week").text().toIntOrNull() ?: 1

        // Yahoo lists every matchup once under each of its two teams, so the same
        // pairing comes back twice and has to be deduped on the team ids.
        val seen = mutableSetOf<String>()

        doc.select("league > teams > team > matchups > matchup")
            .toList()
            .filter { matchupWeek(it) == currentWeek }
            .mapNotNull { matchup ->
                // Scope every field read to the matchup's own teams. A bare
                // select("team") also matches the league level team elements, which
                // is what turned one team name into all 29 names concatenated.
                val teams = matchup.select("teams > team")
                if (teams.size < 2) return@mapNotNull null

                val key = listOf(teams[0], teams[1])
                    .map { it.select("team_id").first()?.text().orEmpty() }
                    .sorted()
                    .joinToString("-")

                if (!seen.add(key)) return@mapNotNull null

                Pair(parseTeam(teams[0]), parseTeam(teams[1]))
            }
    } catch (e: Exception) {
        println("[ManualAlert] Error parsing matchup data: ${e.message}")
        emptyList()
    }
}

// team_points also carries a <week>, so read the matchup's own child rather than
// the first <week> anywhere beneath it.
private fun matchupWeek(matchup: Element): Int? =
    matchup.children().toList().firstOrNull { it.tagName() == "week" }?.text()?.toIntOrNull()

private fun parseTeam(team: Element): Team = Team(
    name = team.select("name").first()?.text().orEmpty(),
    points = team.select("team_points > total").first()?.text().orEmpty(),
    projectedPoints = team.select("team_projected_points > total").first()?.text().orEmpty(),
    // Yahoo sends this as a fraction, for example 0.35.
    winProbability = (team.select("win_probability").first()?.text()?.toDoubleOrNull() ?: 0.0) * 100
)

private fun isCloseScore(matchup: Pair<Team, Team>): Boolean =
    abs(matchup.first.winProbability - matchup.second.winProbability) < 40.0

private fun parseStandingsData(standingsData: String): List<StandingTeam> {
    return try {
        val doc = Jsoup.parse(standingsData, "", Parser.xmlParser())

        doc.select("league > standings > teams > team").toList().map { team ->
            val teamStandings = team.select("team_standings")
            val outcomeTotals = teamStandings.select("outcome_totals")
            val clinchedPlayoffs = team.select("clinched_playoffs").first()?.text().orEmpty()

            StandingTeam(
                rank = teamStandings.select("rank").first()?.text().orEmpty(),
                name = team.select("name").first()?.text().orEmpty(),
                record = listOf("wins", "losses", "ties").joinToString("-") {
                    outcomeTotals.select(it).first()?.text().orEmpty()
                },
                streak = parseStreak(teamStandings.select("streak").first()),
                pointsFor = teamStandings.select("points_for").first()?.text().orEmpty(),
                pointsAgainst = teamStandings.select("points_against").first()?.text().orEmpty(),
                clinchedPlayoffs = clinchedPlayoffs.isNotEmpty() && clinchedPlayoffs != "0"
            )
        }.sortedBy { it.rank.toIntOrNull() ?: 999 }
    } catch (e: Exception) {
        println("[ManualAlert] Error parsing standings data: ${e.message}")
        emptyList()
    }
}

// Yahoo omits the streak until a team has played, so this stays null in preseason.
private fun parseStreak(streak: Element?): String? {
    val amount = streak?.select("value")?.first()?.text().orEmpty()
    if (amount.isEmpty()) return null

    return "$amount${if (streak?.select("type")?.first()?.text() == "win") "W" else "L"}"
}

private fun Double.toPercentage(): String = "${DecimalFormat("#.##").format(this)}%"

private fun formatMatchupMessage(matchup: Pair<Team, Team>): String {
    val (teamOne, teamTwo) = matchup
    return "**${teamOne.name}** vs. **${teamTwo.name}**\\n" +
            "**${teamOne.projectedPoints}** (${teamOne.winProbability.toPercentage()}) " +
            "- **${teamTwo.projectedPoints}** (${teamTwo.winProbability.toPercentage()})"
}

private fun formatScoreMessage(score: Pair<Team, Team>): String {
    val (teamOne, teamTwo) = score
    return "**${teamOne.name}** vs. **${teamTwo.name}**\\n" +
            "**${teamOne.points}** (${teamOne.projectedPoints}) - **${teamTwo.points}** (${teamTwo.projectedPoints})"
}

private fun formatStandingsMessage(team: StandingTeam): String {
    val rank = if (team.rank.isNotEmpty()) "${team.rank}. " else ""
    val streak = team.streak?.let { " ($it)" }.orEmpty()

    return "$rank**${team.name}**\\n" +
            "Record: **${team.record}**$streak\\n" +
            "PF: **${team.pointsFor}** | PA: **${team.pointsAgainst}**" +
            if (team.clinchedPlayoffs) "\\n**Clinched Playoffs!**" else ""
}

private fun sendToMessagingServices(db: Db, messages: List<String>, alertName: String): Map<String, String> {
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

        // Each message is its own post, so one bad send does not hide the rest.
        val failures = messages.mapNotNull { message ->
            sendMessage(db, service, serviceName, AlertFormatting.render(service.service, alertName, message))
        }

        results[serviceName] = if (failures.isEmpty()) {
            "Success (${messages.size} sent)"
        } else {
            "Failed ${failures.size}/${messages.size}: ${failures.first()}"
        }
    }

    return results
}

// Returns null on success, or the failure reason.
private fun sendMessage(
    db: Db,
    service: MessagingService,
    serviceName: String,
    message: String
): String? {
    return try {
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

        if (success) null else "HTTP ${response?.status}"
    } catch (e: Exception) {
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

        e.message ?: "Unknown error"
    }
}
