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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for fetching Yahoo Sports news and player information
 */
class YahooNewsService(
    private val oauthService: OAuth20Service,
    private val accessToken: OAuth2AccessToken
) {
    
    companion object {
        private const val YAHOO_SPORTS_API_BASE = "https://fantasysports.yahooapis.com/fantasy/v2"
        private const val CACHE_DURATION_MINUTES = 15
    }
    
    private val newsCache = mutableMapOf<String, Pair<String, LocalDateTime>>()
    private var gameKeyCache: String? = null
    private var weekCache: Pair<Int, LocalDateTime>? = null
    
    /**
     * Fetches recent news context for a list of players
     */
    fun getRecentNewsForPlayers(players: List<PlayerInfo>): String {
        println("YahooNewsService: Fetching news for ${players.size} players")
        if (players.isEmpty()) {
            println("YahooNewsService: No players provided, returning empty news")
            return ""
        }
        
        val newsItems = mutableListOf<String>()
        val maxNewsItems = 3 // Limit to avoid token bloat
        
        players.take(maxNewsItems).forEach { player ->
            println("YahooNewsService: Fetching news for ${player.name} (${player.nflTeam}, ${player.position})")
            val news = getPlayerNews(player.name, player.nflTeam, player.playerId)
            if (news.isNotEmpty()) {
                println("YahooNewsService: Found news for ${player.name}: $news")
                newsItems.add("${player.name}: $news")
            } else {
                println("YahooNewsService: No news found for ${player.name}")
            }
        }
        
        val result = if (newsItems.isNotEmpty()) {
            "Recent NFL News: ${newsItems.joinToString(" | ")}"
        } else {
            ""
        }
        
        println("YahooNewsService: Final news context: '$result'")
        return result
    }
    
    /**
     * Gets news for a specific player, with caching
     */
    private fun getPlayerNews(playerName: String, nflTeam: String, playerId: String?): String {
        val cacheKey = "${playerName}_${nflTeam}"
        println("YahooNewsService: Looking up news for cache key: $cacheKey")
        
        // Check cache first
        newsCache[cacheKey]?.let { (cachedNews, timestamp) ->
            if (timestamp.isAfter(LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES.toLong()))) {
                println("YahooNewsService: Using cached news for $playerName: '$cachedNews'")
                return cachedNews
            } else {
                println("YahooNewsService: Cache expired for $playerName, fetching fresh news")
            }
        }
        
        println("YahooNewsService: No cache found for $playerName, fetching from Yahoo API")
        val news = fetchPlayerNewsFromYahoo(playerName, nflTeam, playerId)
        
        // Cache the result
        newsCache[cacheKey] = Pair(news, LocalDateTime.now())
        println("YahooNewsService: Cached news result for $playerName: '$news'")
        
        return news
    }
    
    /**
     * Fetches player news from Yahoo Sports API or searches Yahoo Sports
     */
    private fun fetchPlayerNewsFromYahoo(playerName: String, nflTeam: String, playerId: String?): String {
        println("YahooNewsService: Attempting to fetch news from Yahoo for $playerName (playerId: $playerId)")
        return try {
            // Try specific player endpoint if we have player_id
            val result = playerId?.let { id ->
                println("YahooNewsService: Trying specific player news endpoint for ID: $id")
                fetchSpecificPlayerNews(id)
            } ?: run {
                println("YahooNewsService: No player ID, falling back to team news approach")
                // Fallback to team news or general approach
                fetchTeamNews(nflTeam, playerName)
            }
            println("YahooNewsService: Yahoo API fetch result for $playerName: '$result'")
            result
        } catch (e: Exception) {
            println("YahooNewsService: Error fetching news for $playerName: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * Fetches news for a specific player using their Yahoo player ID
     */
    private fun fetchSpecificPlayerNews(playerId: String): String {
        return try {
            // Try Yahoo API endpoint for player data
            val gameKey = gameKey() ?: return ""
            val playerKey = "$gameKey.p.$playerId"
            val url = "https://fantasysports.yahooapis.com/fantasy/v2/player/$playerKey"
            
            val doc = makeYahooApiRequest(url)
            
            // Extract real player data: injury status, notes, etc.
            val injuryNote = doc.select("injury_note").text()
            val status = doc.select("status").text()
            val statusFull = doc.select("status_full").text()
            val hasNotes = doc.select("has_player_notes").text() == "1"
            
            val updates = mutableListOf<String>()
            
            // Add injury information if available
            if (injuryNote.isNotEmpty()) {
                updates.add("Injury: $injuryNote")
            }
            
            // Add status if not healthy
            if (status.isNotEmpty() && status != "NA") {
                updates.add("Status: $statusFull")
            }
            
            // Note if player has updates available
            if (hasNotes) {
                updates.add("Recent updates available")
            }
            
            updates.joinToString(". ")
        } catch (e: Exception) {
            println("Player-specific news fetch failed: ${e.message}")
            ""
        }
    }
    
    /**
     * Fetches team-related news that might mention the player
     */
    private fun fetchTeamNews(nflTeam: String, playerName: String): String {
        return try {
            // Use Yahoo Sports team news approach
            val teamNews = getTeamRelatedNews(nflTeam)
            
            // Filter for mentions of the specific player
            filterNewsForPlayer(teamNews, playerName)
        } catch (e: Exception) {
            println("Team news fetch failed: ${e.message}")
            ""
        }
    }
    
    /**
     * Makes authenticated request to Yahoo API
     */
    private fun makeYahooApiRequest(url: String): Document {
        val request = OAuthRequest(Verb.GET, url)
        oauthService.signRequest(accessToken, request)
        val response = oauthService.execute(request)
        return Jsoup.parse(response.body, "", Parser.xmlParser())
    }
    
    /**
     * Parses news content from Yahoo API response
     */
    private fun parseNewsFromResponse(doc: Document): String {
        return try {
            // Look for common news fields in Yahoo responses
            val newsItems = doc.select("news, headline, summary, description")
            
            newsItems.take(2).joinToString(" ") { element: org.jsoup.nodes.Element ->
                element.text().take(100) // Limit length
            }.trim()
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Gets team-related news
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getTeamRelatedNews(nflTeam: String): String {
        // This could be enhanced to use Yahoo's team news endpoints
        // For now, return empty to avoid API errors
        return ""
    }
    
    /**
     * Filters team news for player mentions
     */
    private fun filterNewsForPlayer(teamNews: String, playerName: String): String {
        val lastName = playerName.substringAfterLast(" ")
        return if (teamNews.contains(lastName, ignoreCase = true)) {
            teamNews.take(150) // Limit length
        } else {
            ""
        }
    }
    
    /**
     * Generates a contextual news summary for OpenAI
     */
    fun generateNewsContext(players: List<PlayerInfo>): String {
        println("YahooNewsService: Generating news context for OpenAI with ${players.size} players")
        val news = getRecentNewsForPlayers(players)
        
        val result = if (news.isNotEmpty()) {
            println("YahooNewsService: Using news context: $news")
            "Context: $news"
        } else {
            // Fallback to current week/season context
            val currentWeek = getCurrentNFLWeek()
            val fallback = if (currentWeek != null) {
                "Context: Week $currentWeek of the NFL season"
            } else {
                "Context: NFL season"
            }
            println("YahooNewsService: No news found, using fallback context: $fallback")
            fallback
        }
        
        println("YahooNewsService: Final context for OpenAI: '$result'")
        return result
    }
    
    /**
     * Resolves the current season's game key from Yahoo instead of hardcoding it,
     * so player lookups keep working when the season rolls over.
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

    /**
     * Current NFL week straight from Yahoo's league metadata. Yahoo is the source of
     * truth, so this stays right across seasons and reads 1 during the preseason.
     * Returns null when it cannot be determined, so callers can leave the week out
     * rather than guess at it.
     */
    private fun getCurrentNFLWeek(): Int? {
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
