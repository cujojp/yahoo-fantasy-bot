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

package com.landonpatmore.yahoofantasybot.backend.utils

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.request.HttpRequest

class DataRetriever(private val oauthManager: BackendOAuthManager) {
    
    sealed class YahooApiRequest(val url: String) {
        object Transactions : YahooApiRequest("/transactions")
        object Standings : YahooApiRequest("/standings")
        object TeamsData : YahooApiRequest("/teams/matchups")
    }
    
    fun yahooApiRequest(request: YahooApiRequest): String {
        println("[DataRetriever] Fetching data for: ${request.url}")
        
        val leagueKey = oauthManager.getLeagueKey()
        val fullUrl = "https://fantasysports.yahooapis.com/fantasy/v2/league/$leagueKey${request.url}"
        
        println("[DataRetriever] Full URL: $fullUrl")
        println("[DataRetriever] League key: $leagueKey")
        
        val httpRequest = Unirest.get(fullUrl)
        val authenticatedRequest = oauthManager.authenticateRequest(httpRequest)
        
        val response = authenticatedRequest.asString()
        
        println("[DataRetriever] Response status: ${response.status}")
        
        if (response.status != 200) {
            val errorMessage = when (response.status) {
                500 -> "Yahoo API internal server error. This might be due to: 1) Complex query parameters, 2) Temporary Yahoo service issues, or 3) Invalid league configuration."
                401 -> "Authentication failed. Please re-authenticate with Yahoo."
                403 -> "Access forbidden. Check if you have permission to access this league."
                404 -> "League not found. Verify the league ID and game key are correct."
                else -> "Yahoo API request failed with status ${response.status}"
            }
            
            println("[DataRetriever] Error details: $errorMessage")
            println("[DataRetriever] Response body: ${response.body}")
            
            throw RuntimeException("$errorMessage (Status: ${response.status})")
        }
        
        return response.body
    }
}
