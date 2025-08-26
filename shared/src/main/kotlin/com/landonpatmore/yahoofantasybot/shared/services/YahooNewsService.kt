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
    
    /**
     * Fetches recent news context for a list of players
     */
    fun getRecentNewsForPlayers(players: List<PlayerInfo>): String {
        if (players.isEmpty()) return ""
        
        val newsItems = mutableListOf<String>()
        val maxNewsItems = 3 // Limit to avoid token bloat
        
        players.take(maxNewsItems).forEach { player ->
            val news = getPlayerNews(player.name, player.nflTeam, player.playerId)
            if (news.isNotEmpty()) {
                newsItems.add("${player.name}: $news")
            }
        }
        
        return if (newsItems.isNotEmpty()) {
            "Recent NFL News: ${newsItems.joinToString(" | ")}"
        } else {
            ""
        }
    }
    
    /**
     * Gets news for a specific player, with caching
     */
    private fun getPlayerNews(playerName: String, nflTeam: String, playerId: String?): String {
        val cacheKey = "${playerName}_${nflTeam}"
        
        // Check cache first
        newsCache[cacheKey]?.let { (cachedNews, timestamp) ->
            if (timestamp.isAfter(LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES.toLong()))) {
                return cachedNews
            }
        }
        
        val news = fetchPlayerNewsFromYahoo(playerName, nflTeam, playerId)
        
        // Cache the result
        newsCache[cacheKey] = Pair(news, LocalDateTime.now())
        
        return news
    }
    
    /**
     * Fetches player news from Yahoo Sports API or searches Yahoo Sports
     */
    private fun fetchPlayerNewsFromYahoo(playerName: String, nflTeam: String, playerId: String?): String {
        return try {
            // Try specific player endpoint if we have player_id
            playerId?.let { id ->
                fetchSpecificPlayerNews(id)
            } ?: run {
                // Fallback to team news or general approach
                fetchTeamNews(nflTeam, playerName)
            }
        } catch (e: Exception) {
            println("Error fetching news for $playerName: ${e.message}")
            ""
        }
    }
    
    /**
     * Fetches news for a specific player using their Yahoo player ID
     */
    private fun fetchSpecificPlayerNews(playerId: String): String {
        return try {
            // Yahoo Fantasy Sports API may have player news endpoints
            val url = "$YAHOO_SPORTS_API_BASE/player/$playerId/news"
            val response = makeYahooApiRequest(url)
            
            // Parse response for news content
            parseNewsFromResponse(response)
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
        val news = getRecentNewsForPlayers(players)
        
        return if (news.isNotEmpty()) {
            "Context: $news"
        } else {
            // Fallback to current week/season context
            val currentWeek = getCurrentNFLWeek()
            "Context: Week $currentWeek of NFL season"
        }
    }
    
    /**
     * Gets current NFL week (simplified)
     */
    private fun getCurrentNFLWeek(): Int {
        val now = LocalDateTime.now()
        val month = now.monthValue
        
        return when {
            month in 9..10 -> ((now.dayOfMonth / 7) + 1).coerceIn(1, 8)
            month == 11 -> ((now.dayOfMonth / 7) + 9).coerceIn(9, 12)
            month == 12 -> ((now.dayOfMonth / 7) + 13).coerceIn(13, 18)
            month == 1 -> if (now.dayOfMonth < 15) 18 else 1
            else -> 1
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
