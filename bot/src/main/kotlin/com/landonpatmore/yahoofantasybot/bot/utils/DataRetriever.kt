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

package com.landonpatmore.yahoofantasybot.bot.utils

import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.landonpatmore.yahoofantasybot.bot.utils.models.YahooApiRequest
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import com.landonpatmore.yahoofantasybot.shared.services.YahooNewsService
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

class DataRetriever(private val database: Db) : IDataRetriever {
    private var currentToken: Pair<Long, OAuth2AccessToken>? = null

    private val oauthService = ServiceBuilder(EnvVariable.Str.YahooClientId.variable)
        .apiSecret(EnvVariable.Str.YahooClientSecret.variable)
        .callback(OAuthConstants.OOB)
        .build(YahooApi20.instance())
    private val gameKeyUrl = "/game/${EnvVariable.Str.YahooGameKey.variable}"
    private var leagueUrl: String? = null

    override fun isTokenExpired(retrieved: Long, expiresIn: Int): Boolean {
        val timeElapsed = ((System.currentTimeMillis() - retrieved) / 1000)
        return timeElapsed >= expiresIn
    }

    override fun refreshExpiredToken() {
        currentToken?.let {
            if (isTokenExpired(it.first, it.second.expiresIn)) {
                val refreshToken =
                    oauthService.refreshAccessToken(it.second.refreshToken)
                currentToken = Pair(System.currentTimeMillis(), refreshToken)
                database.saveToken(refreshToken)
            }
        }
    }

    override fun getAuthenticationToken() {
        while (true) {
            currentToken = database.getLatestTokenData()
            if (currentToken == null) { // This will run only if there is no data in the database
                println("There is currently no token data in the database.  Please authenticate with Yahoo.")
            } else {
                return
            }

            Thread.sleep(5000)
        }
    }

    override fun grabData(url: String): Document {
        refreshExpiredToken()
        println("[DataRetriever] Grabbing data from URL: $url")
        val request = OAuthRequest(Verb.GET, url)
        oauthService.signRequest(currentToken?.second, request)
        
        println("[DataRetriever] Executing API request...")
        val response = oauthService.execute(request)
        
        println("[DataRetriever] Response code: ${response.code}")
        println("[DataRetriever] Response body length: ${response.body?.length ?: 0} characters")
        
        if (response.code != 200) {
            println("[DataRetriever] ERROR: Non-200 response code")
            println("[DataRetriever] Response body: ${response.body}")
        }
        
        val document = Jsoup.parse(response.body, "", Parser.xmlParser())
        
        // Log some basic info about the response
        val rootElement = document.root?.tagName() ?: "unknown"
        println("[DataRetriever] Parsed XML root element: $rootElement")
        
        return document
    }

    override fun retrieveGameKey(): String? {
        while (true) {
            val data = grabData(BASE_URL + gameKeyUrl)
            val gameKey = data.select("game_key").first().text()

            if (gameKey.isNullOrEmpty()) {
                println("Game key could not retrieved from Yahoo, retrying in 5 seconds...")
                Thread.sleep(5000)
            }

            return gameKey
        }
    }

    override fun yahooApiRequest(yahooApiRequest: YahooApiRequest): Document {
        if (leagueUrl == null) {
            leagueUrl = "/league/${retrieveGameKey()}.l.${EnvVariable.Str.YahooLeagueId.variable}"
        }

        return when (yahooApiRequest) {
            YahooApiRequest.Transactions -> getTransactions()
            YahooApiRequest.Standings -> getStandings()
            YahooApiRequest.TeamsData -> getTeamsData()
        }
    }

    override fun getTransactions(): Document {
        return grabData(BASE_URL + leagueUrl + TRANSACTIONS)
    }

    override fun getStandings(): Document {
        return grabData(BASE_URL + leagueUrl + STANDINGS)
    }

    override fun getTeamsData(): Document {
        return grabData(BASE_URL + leagueUrl + SCOREBOARD)
    }
    
    /**
     * Creates a YahooNewsService using this DataRetriever's OAuth configuration
     */
    fun createYahooNewsService(): YahooNewsService? {
        return currentToken?.let { (_, token) ->
            YahooNewsService(oauthService, token)
        }
    }

    companion object {
        private const val SCOREBOARD = "/scoreboard"
        private const val STANDINGS = "/standings"
        private const val TRANSACTIONS = "/transactions"
        private const val BASE_URL =
            "https://fantasysports.yahooapis.com/fantasy/v2"
    }
}
