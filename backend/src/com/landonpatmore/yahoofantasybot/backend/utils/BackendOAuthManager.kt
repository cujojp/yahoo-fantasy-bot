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

import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthConstants
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.services.YahooNewsService
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable

/**
 * Manages OAuth tokens for the backend service to enable YahooNewsService access
 */
class BackendOAuthManager(private val database: Db) {
    
    private val oauthService = ServiceBuilder(EnvVariable.Str.YahooClientId.variable)
        .apiSecret(EnvVariable.Str.YahooClientSecret.variable)
        .callback(OAuthConstants.OOB)
        .build(YahooApi20.instance())
    
    private var cachedToken: Pair<Long, OAuth2AccessToken>? = null
    
    /**
     * Creates a YahooNewsService instance if OAuth tokens are available
     */
    fun createYahooNewsService(): YahooNewsService? {
        return try {
            println("BackendOAuthManager: Attempting to create YahooNewsService")
            
            // Check if OAuth credentials are configured
            if (EnvVariable.Str.YahooClientId.variable.isEmpty() || 
                EnvVariable.Str.YahooClientSecret.variable.isEmpty()) {
                println("BackendOAuthManager: Yahoo OAuth credentials not configured")
                return null
            }
            
            // Get the latest token from database (shared with bot)
            val tokenData = getValidToken()
            
            if (tokenData != null) {
                val (_, token) = tokenData
                println("BackendOAuthManager: Valid OAuth token found, creating YahooNewsService")
                YahooNewsService(oauthService, token)
            } else {
                println("BackendOAuthManager: No valid OAuth token available")
                null
            }
        } catch (e: Exception) {
            println("BackendOAuthManager: Error creating YahooNewsService: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Gets a valid OAuth token, refreshing if necessary
     */
    private fun getValidToken(): Pair<Long, OAuth2AccessToken>? {
        // Try to use cached token first
        cachedToken?.let { (timestamp, token) ->
            if (!isTokenExpired(timestamp, token.expiresIn)) {
                println("BackendOAuthManager: Using cached token")
                return cachedToken
            }
            println("BackendOAuthManager: Cached token expired")
        }
        
        // Get token from database
        val dbToken = database.getLatestTokenData()
        if (dbToken == null) {
            println("BackendOAuthManager: No token found in database")
            return null
        }
        
        val (timestamp, token) = dbToken
        
        // Check if token needs refresh
        if (isTokenExpired(timestamp, token.expiresIn)) {
            println("BackendOAuthManager: Token expired, attempting refresh")
            return try {
                val refreshedToken = oauthService.refreshAccessToken(token.refreshToken)
                val newTokenData = Pair(System.currentTimeMillis(), refreshedToken)
                
                // Save refreshed token to database
                database.saveToken(refreshedToken)
                
                // Cache the new token
                cachedToken = newTokenData
                println("BackendOAuthManager: Token refreshed successfully")
                
                newTokenData
            } catch (e: Exception) {
                println("BackendOAuthManager: Failed to refresh token: ${e.message}")
                null
            }
        }
        
        // Token is still valid
        cachedToken = dbToken
        return dbToken
    }
    
    /**
     * Checks if a token is expired
     */
    private fun isTokenExpired(retrieved: Long, expiresIn: Int): Boolean {
        val timeElapsed = ((System.currentTimeMillis() - retrieved) / 1000)
        val isExpired = timeElapsed >= expiresIn
        println("BackendOAuthManager: Token age: ${timeElapsed}s, expires in: ${expiresIn}s, expired: $isExpired")
        return isExpired
    }
}
