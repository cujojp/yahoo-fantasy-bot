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
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * API routes for message history
 */
fun Route.messageHistoryRouting() {
    val database by inject<Db>()

    route("/api/messageHistory") {
        
        // Get recent message history
        get {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val messageHistory = database.getRecentMessageHistory(limit)
                
                val response = messageHistory.map { it.toApiResponse() }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // Get message history by type
        get("/type/{messageType}") {
            try {
                val messageType = call.parameters["messageType"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, 
                    mapOf("error" to "Message type is required")
                )
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                
                val messageHistory = database.getMessageHistoryByType(messageType, limit)
                val response = messageHistory.map { it.toApiResponse() }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // Get message history statistics
        get("/stats") {
            try {
                val stats = database.getMessageHistoryStats()
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // Get recent SchefterBot tweets
        get("/schefter") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val allHistory = database.getRecentMessageHistory(limit * 3) // Get more to filter
                
                val schefterMessages = allHistory
                    .filter { it.hasSchefterTweet() }
                    .take(limit)
                    .map { it.toApiResponse() }
                
                call.respond(HttpStatusCode.OK, schefterMessages)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // Get transaction messages only
        get("/transactions") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val transactionHistory = database.getMessageHistoryByType(MessageHistory.TYPE_TRANSACTION, limit)
                
                val response = transactionHistory.map { it.toApiResponse() }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
}

/**
 * API response model for message history
 */
data class MessageHistoryApiResponse(
    val id: String,
    val timestamp: String,
    val messageType: String,
    val transactionType: String?,
    val messagingService: String,
    val originalMessage: String,
    val schefterTweet: String?,
    val finalContent: String,
    val success: Boolean,
    val errorMessage: String?,
    val playersInvolved: List<String>?,
    val responseCode: Int?,
    val hasSchefterTweet: Boolean,
    val description: String
)

/**
 * Extension function to convert MessageHistory to API response
 */
private fun MessageHistory.toApiResponse(): MessageHistoryApiResponse {
    return MessageHistoryApiResponse(
        id = id.toString(),
        timestamp = timestamp.toString(),
        messageType = messageType,
        transactionType = transactionType,
        messagingService = messagingService,
        originalMessage = originalMessage,
        schefterTweet = schefterTweet,
        finalContent = finalContent,
        success = success,
        errorMessage = errorMessage,
        playersInvolved = playersInvolved,
        responseCode = responseCode,
        hasSchefterTweet = hasSchefterTweet(),
        description = getDescription()
    )
}
