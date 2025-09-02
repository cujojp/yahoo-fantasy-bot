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

import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class HealthCheckResponse(
    val status: String,
    val timestamp: String,
    val environment: EnvironmentStatus,
    val database: DatabaseStatus,
    val oauth: OAuthStatus,
    val messaging: MessagingStatus,
    val lastActivity: LastActivityStatus
)

@Serializable
data class EnvironmentStatus(
    val yahooClientId: Boolean,
    val yahooClientSecret: Boolean,
    val yahooGameKey: String?,
    val yahooLeagueId: String?,
    val railwayEnvironment: String?
)

@Serializable
data class DatabaseStatus(
    val connected: Boolean,
    val tablesExist: Boolean,
    val error: String? = null
)

@Serializable
data class OAuthStatus(
    val hasToken: Boolean,
    val isValid: Boolean,
    val expiresAt: String?,
    val error: String? = null
)

@Serializable
data class MessagingStatus(
    val groupMe: Boolean,
    val discord: Boolean,
    val slack: Boolean,
    val totalConfigured: Int
)

@Serializable
data class LastActivityStatus(
    val lastTransactionCheck: String?,
    val recentMessages24h: Int,
    val recentErrors24h: Int
)

fun Route.healthRoutes(database: Db) {
    get("/health") {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        
        val response = HealthCheckResponse(
            status = "ok",
            timestamp = formatter.format(Instant.now()),
            environment = checkEnvironment(),
            database = checkDatabase(database),
            oauth = checkOAuth(database),
            messaging = checkMessaging(),
            lastActivity = checkLastActivity(database)
        )
        
        call.respond(HttpStatusCode.OK, Json.encodeToString(response))
    }
    
    // Simple ping endpoint for uptime monitoring
    get("/ping") {
        call.respondText("pong", ContentType.Text.Plain)
    }
}

private fun checkEnvironment(): EnvironmentStatus {
    return EnvironmentStatus(
        yahooClientId = EnvVariable.Str.YahooClientId.variable.isNotEmpty(),
        yahooClientSecret = EnvVariable.Str.YahooClientSecret.variable.isNotEmpty(),
        yahooGameKey = EnvVariable.Str.YahooGameKey.variable.takeIf { it.isNotEmpty() },
        yahooLeagueId = EnvVariable.Str.YahooLeagueId.variable.takeIf { it.isNotEmpty() },
        railwayEnvironment = System.getenv("RAILWAY_ENVIRONMENT")
    )
}

private fun checkDatabase(database: Db): DatabaseStatus {
    return try {
        // Try to get token data as a simple connectivity check
        database.getLatestTokenData()
        
        DatabaseStatus(
            connected = true,
            tablesExist = true // Assume tables exist if we can query
        )
    } catch (e: Exception) {
        DatabaseStatus(
            connected = false,
            tablesExist = false,
            error = e.message
        )
    }
}

private fun checkOAuth(database: Db): OAuthStatus {
    return try {
        val tokenData = database.getLatestTokenData()
        
        if (tokenData != null) {
            val (retrieved, token) = tokenData
            val expiryTime = retrieved + (token.expiresIn * 1000L)
            val isValid = expiryTime > System.currentTimeMillis()
            
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            val expiresAt = formatter.format(Instant.ofEpochMilli(expiryTime))
            
            OAuthStatus(
                hasToken = true,
                isValid = isValid,
                expiresAt = expiresAt
            )
        } else {
            OAuthStatus(
                hasToken = false,
                isValid = false,
                expiresAt = null
            )
        }
    } catch (e: Exception) {
        OAuthStatus(
            hasToken = false,
            isValid = false,
            expiresAt = null,
            error = e.message
        )
    }
}

private fun checkMessaging(): MessagingStatus {
    val groupMe = EnvVariable.Str.GroupMeBotId.variable.isNotEmpty()
    val discord = EnvVariable.Str.DiscordWebhookUrl.variable.isNotEmpty()
    val slack = EnvVariable.Str.SlackWebhookUrl.variable.isNotEmpty()
    
    return MessagingStatus(
        groupMe = groupMe,
        discord = discord,
        slack = slack,
        totalConfigured = listOf(groupMe, discord, slack).count { it }
    )
}

private fun checkLastActivity(database: Db): LastActivityStatus {
    return try {
        val lastTimeChecked = database.getLatestTimeChecked()
        val lastCheckStr = if (lastTimeChecked.time > 0) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            formatter.format(Instant.ofEpochMilli(lastTimeChecked.time))
        } else if (lastTimeChecked.time == -1L) {
            "Error state (-1)"
        } else {
            "Never checked"
        }
        
        // For now, return simplified status without querying message history
        LastActivityStatus(
            lastTransactionCheck = lastCheckStr,
            recentMessages24h = 0, // Would need to implement getRecentMessageCount in Db
            recentErrors24h = 0
        )
    } catch (e: Exception) {
        LastActivityStatus(
            lastTransactionCheck = "Error: ${e.message}",
            recentMessages24h = 0,
            recentErrors24h = 0
        )
    }
}