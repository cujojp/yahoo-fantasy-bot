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
import com.landonpatmore.yahoofantasybot.backend.utils.OpenAIHelper
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.database.models.Alert
import com.landonpatmore.yahoofantasybot.shared.database.models.League
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageType
import com.landonpatmore.yahoofantasybot.shared.database.models.MessagingService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.putRoutes(db: Db) {
    routing {
        putMessagingServices(db)
        putLeagues(db)
        putAlerts(db)
        putMessageType(db)
        postTestMessage(db)
    }
}

private fun Route.putMessagingServices(db: Db) {
    put("/messagingServices") {
        val services = call.receiveJson<Array<MessagingService>>()
        db.saveMessagingServices(services)
        call.respond(db.getMessagingServices())
    }
}

private fun Route.putLeagues(db: Db) {
    put("/leagues") {
        val leagues = call.receiveJson<Array<League>>()
        db.saveLeagues(leagues)
        call.respond(db.getLeagues())
    }
}

private fun Route.putAlerts(db: Db) {
    put("/alerts") {
        val alerts = call.receiveJson<Array<Alert>>()
        db.saveAlerts(alerts)
        call.respond(db.getAlerts())
    }
}

private fun Route.putMessageType(db: Db) {
    put("/messageType") {
        val messageType = call.receiveJson<MessageType>()
        db.saveMessageType(messageType)
        call.respond(db.getMessageType())
    }
}

private suspend inline fun <reified T> ApplicationCall.receiveJson(): T {
    val json = this.receiveOrNull<String>()
    return Gson().fromJson(json, T::class.java)
}

private fun Route.postTestMessage(db: Db) {
    post("/testMessage") {
        data class TestMessageRequest(val message: String = "🤖 Test message from Yahoo Fantasy Bot! If you see this, your webhook is working correctly.")
        
        val request = try {
            call.receiveJson<TestMessageRequest>()
        } catch (e: Exception) {
            TestMessageRequest()
        }
        
        // Generate Schefter-style tweet if OpenAI is configured
        println("Attempting to generate Schefter tweet for message: ${request.message}")
        val schefterTweet = OpenAIHelper.generateTestMessageSchefterTweet(request.message)
        println("Schefter tweet result: ${if (schefterTweet != null) "Generated successfully" else "Not generated"}")
        
        val fullMessage = if (schefterTweet != null) {
            "${request.message}\n\n$schefterTweet"
        } else {
            request.message
        }
        
        val messagingServices = db.getMessagingServices()
        val results = mutableMapOf<String, String>()
        
        // Send test message to each configured service
        messagingServices.forEach { service ->
            // Skip empty URLs
            if (service.url.isBlank()) {
                val serviceName = when (service.service) {
                    0 -> "Discord"
                    1 -> "Slack"
                    2 -> "GroupMe"
                    else -> "Unknown"
                }
                results[serviceName] = "Error: Empty webhook URL"
                return@forEach
            }
            
            when (service.service) {
                0 -> { // Discord
                    try {
                        val response = com.mashape.unirest.http.Unirest.post(service.url)
                            .header("Content-Type", "application/json")
                            .body("{\"content\" : \"${fullMessage.replace("\"", "\\\\\"\"").replace("\n", "\\n")}\"}")  
                            .asJson()
                        results["Discord"] = if (response.status in 200..299) "Success" else "Failed: ${response.status}"
                    } catch (e: Exception) {
                        results["Discord"] = "Error: ${e.message}"
                    }
                }
                1 -> { // Slack
                    try {
                        val response = com.mashape.unirest.http.Unirest.post(service.url)
                            .header("Content-Type", "application/json")
                            .body("{\"text\" : \"${fullMessage.replace("\"", "\\\\\"\"").replace("\n", "\\n")}\"}")
                            .asJson()
                        results["Slack"] = if (response.status in 200..299) "Success" else "Failed: ${response.status}"
                    } catch (e: Exception) {
                        results["Slack"] = "Error: ${e.message}"
                    }
                }
                2 -> { // GroupMe
                    try {
                        val response = com.mashape.unirest.http.Unirest.post("https://api.groupme.com/v3/bots/post")
                            .header("Content-Type", "application/json")
                            .body("{\"bot_id\" : \"${service.url}\", \"text\" : \"${fullMessage.replace("\"", "\\\\\"\"").replace("\n", "\\n")}\"}")
                            .asJson()
                        results["GroupMe"] = if (response.status in 200..299) "Success" else "Failed: ${response.status}"
                    } catch (e: Exception) {
                        results["GroupMe"] = "Error: ${e.message}"
                    }
                }
            }
        }
        
        // Add info about Schefter tweet
        if (schefterTweet != null) {
            results["SchefterTweet"] = "Generated successfully"
        }
        
        call.respond(results)
    }
}
