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

package com.landonpatmore.yahoofantasybot.shared.services

import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import com.landonpatmore.yahoofantasybot.shared.services.news.NewsFact
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.time.Duration
import java.time.LocalDateTime

/**
 * The Yahoo half of player context: injury designations from the fantasy player resource,
 * and the league's own current week.
 *
 * Yahoo carries a status and an injury note but no actual reporting, so this is a
 * supporting source rather than the main one. See
 * [com.landonpatmore.yahoofantasybot.shared.services.news.PlayerNewsService] for how it is
 * combined with ESPN and Sleeper.
 */
class YahooNewsService(
    private val oauthService: OAuth20Service,
    private val accessToken: OAuth2AccessToken
) {

    companion object {
        private const val YAHOO_SPORTS_API_BASE = "https://fantasysports.yahooapis.com/fantasy/v2"
        private const val CACHE_DURATION_MINUTES = 15L
        private const val SOURCE = "Yahoo"
    }

    private val newsCache = mutableMapOf<String, Pair<NewsFact?, LocalDateTime>>()
    private var gameKeyCache: String? = null
    private var weekCache: Pair<Int, LocalDateTime>? = null

    /**
     * Yahoo's injury designation for a player, or null when Yahoo has nothing. Null is the
     * common case for a healthy player and is a fine answer: we would rather send no fact
     * than a filler one.
     */
    fun playerFact(player: PlayerInfo): NewsFact? {
        val playerId = player.playerId ?: return null
        val cacheKey = "${player.name}_${player.nflTeam}"

        newsCache[cacheKey]?.let { (cached, fetchedAt) ->
            if (Duration.between(fetchedAt, LocalDateTime.now()).toMinutes() < CACHE_DURATION_MINUTES) {
                return cached
            }
        }

        val fact = fetchPlayerFact(player.name, playerId)
        newsCache[cacheKey] = Pair(fact, LocalDateTime.now())
        return fact
    }

    private fun fetchPlayerFact(playerName: String, playerId: String): NewsFact? {
        return try {
            val gameKey = gameKey() ?: return null
            val doc = makeYahooApiRequest("$YAHOO_SPORTS_API_BASE/player/$gameKey.p.$playerId")

            val injuryNote = doc.select("injury_note").text()
            val status = doc.select("status").text()
            val statusFull = doc.select("status_full").text().ifBlank { status }

            val parts = mutableListOf<String>()
            if (status.isNotEmpty() && status != "NA") {
                parts.add("Listed $statusFull")
            }
            if (injuryNote.isNotEmpty()) {
                parts.add(injuryNote)
            }

            // Yahoo also exposes has_player_notes, but not the notes themselves. Saying
            // "updates available" told the model nothing and invited it to fill the gap.
            if (parts.isEmpty()) null
            else NewsFact(playerName, parts.joinToString(" - "), SOURCE, null)
        } catch (e: Exception) {
            println("YahooNewsService: news fetch failed for $playerName: ${e.message}")
            null
        }
    }

    /**
     * Current NFL week straight from Yahoo's league metadata. Returns null when it cannot
     * be determined, so callers leave the week out rather than guess at it.
     */
    fun currentWeek(): Int? {
        weekCache?.let { (week, fetchedAt) ->
            if (Duration.between(fetchedAt, LocalDateTime.now()).toMinutes() < CACHE_DURATION_MINUTES) {
                return week
            }
        }

        val gameKey = gameKey() ?: return null
        val leagueId = EnvVariable.Str.YahooLeagueId.variable
        if (leagueId.isEmpty()) return null

        return try {
            makeYahooApiRequest("$YAHOO_SPORTS_API_BASE/league/$gameKey.l.$leagueId")
                .select("current_week").first()?.text()?.toIntOrNull()
                ?.also { weekCache = Pair(it, LocalDateTime.now()) }
        } catch (e: Exception) {
            println("YahooNewsService: could not resolve current week: ${e.message}")
            null
        }
    }

    /**
     * Resolves the current season's game key from Yahoo instead of hardcoding it, so
     * player lookups keep working when the season rolls over.
     */
    private fun gameKey(): String? {
        gameKeyCache?.let { return it }

        return try {
            makeYahooApiRequest("$YAHOO_SPORTS_API_BASE/game/nfl")
                .select("game_key").first()?.text()
                ?.takeIf { it.isNotEmpty() }
                ?.also { gameKeyCache = it }
        } catch (e: Exception) {
            println("YahooNewsService: could not resolve game key: ${e.message}")
            null
        }
    }

    private fun makeYahooApiRequest(url: String): Document {
        val request = OAuthRequest(Verb.GET, url)
        oauthService.signRequest(accessToken, request)
        val response = oauthService.execute(request)
        return Jsoup.parse(response.body, "", Parser.xmlParser())
    }
}

/**
 * Data class for player information
 */
data class PlayerInfo(
    val name: String,
    val nflTeam: String,
    val position: String,
    val playerId: String? = null,
    val playerKey: String? = null
)
