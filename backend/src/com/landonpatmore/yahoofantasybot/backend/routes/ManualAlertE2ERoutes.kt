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

import com.landonpatmore.yahoofantasybot.backend.utils.DataRetriever
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.bot.transformers.*
import com.landonpatmore.yahoofantasybot.bot.messaging.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.jsoup.nodes.Document

fun Route.manualAlertE2ERoutes(db: Db) {
    // Create a DataRetriever for backend use
    val dataRetriever = DataRetriever(db)
    
    route("/manual/e2e") {
        get("/matchup") {
            try {
                println("[ManualE2E] Starting E2E matchup alert test")
                
                // Step 1: Fetch data exactly like the bot does
                val data = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.TeamsData)
                println("[ManualE2E] Fetched data from Yahoo API")
                
                // Step 2: Create a temporary bridge to process the data
                val documentSubject = PublishSubject.create<Document>()
                val messageSubject = PublishSubject.create<Message>()
                
                // Step 3: Set up the exact same transformer pipeline as the bot
                val pipeline = documentSubject
                    .doOnNext { println("[ManualE2E] Document received in pipeline") }
                    .convertToMatchUpObject()
                    .doOnNext { println("[ManualE2E] Converted to matchup: ${it.first.name} vs ${it.second.name}") }
                    .convertToMatchUpMessage()
                    .doOnNext { println("[ManualE2E] Converted to message: ${it.message}") }
                
                // Step 4: Collect messages
                val messages = mutableListOf<Message>()
                pipeline.subscribe(
                    { message ->
                        println("[ManualE2E] Message created: ${message.message}")
                        messages.add(message)
                    },
                    { error ->
                        println("[ManualE2E] Error in pipeline: ${error.message}")
                        error.printStackTrace()
                    }
                )
                
                // Step 5: Send data through the pipeline
                documentSubject.onNext(data)
                
                // Wait a moment for processing
                Thread.sleep(100)
                
                // Step 6: Send messages to services (using bot's messaging logic)
                if (messages.isNotEmpty()) {
                    val messagingServices = db.getMessagingServices()
                    messagingServices.forEach { service ->
                        try {
                            val sender = when(service.name) {
                                "Discord" -> DiscordMessagingService()
                                "Slack" -> SlackMessagingService()
                                "GroupMe" -> GroupMeMessagingService()
                                else -> null
                            }
                            
                            sender?.let {
                                messages.forEach { message ->
                                    it.send(service.url!!, message.message, db)
                                    println("[ManualE2E] Sent message to ${service.name}")
                                }
                            }
                        } catch (e: Exception) {
                            println("[ManualE2E] Error sending to ${service.name}: ${e.message}")
                        }
                    }
                    
                    call.respondText(
                        "E2E Matchup alert sent successfully! Found ${messages.size} matchups.",
                        ContentType.Text.Plain
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "No matchups found for current week")
                }
            } catch (e: Exception) {
                println("[ManualE2E] Error: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        
        get("/standings") {
            try {
                println("[ManualE2E] Starting E2E standings alert test")
                
                // Similar implementation for standings
                val data = dataRetriever.yahooApiRequest(DataRetriever.YahooApiRequest.Standings)
                
                val documentSubject = PublishSubject.create<Document>()
                val pipeline = documentSubject
                    .convertToStandingsMessage()
                
                val messages = mutableListOf<Message>()
                pipeline.subscribe(
                    { message -> messages.add(message) },
                    { error -> println("[ManualE2E] Error: ${error.message}") }
                )
                
                documentSubject.onNext(data)
                Thread.sleep(100)
                
                if (messages.isNotEmpty()) {
                    val messagingServices = db.getMessagingServices()
                    messagingServices.forEach { service ->
                        try {
                            val sender = when(service.name) {
                                "Discord" -> DiscordMessagingService()
                                "Slack" -> SlackMessagingService()
                                "GroupMe" -> GroupMeMessagingService()
                                else -> null
                            }
                            
                            sender?.let {
                                messages.forEach { message ->
                                    it.send(service.url!!, message.message, db)
                                }
                            }
                        } catch (e: Exception) {
                            println("[ManualE2E] Error sending to ${service.name}: ${e.message}")
                        }
                    }
                    
                    call.respondText("E2E Standings alert sent successfully!", ContentType.Text.Plain)
                } else {
                    call.respond(HttpStatusCode.NotFound, "No standings data found")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
    }
}

// Copy of the bot's YahooApiRequest enum for the backend
private sealed class YahooApiRequest {
    object Transactions : YahooApiRequest()
    object Standings : YahooApiRequest()
    object TeamsData : YahooApiRequest()
}

// Extension to make DataRetriever work with our enum
private fun DataRetriever.yahooApiRequest(request: YahooApiRequest): Document {
    return when (request) {
        is YahooApiRequest.Transactions -> this.yahooApiRequest("/transactions")
        is YahooApiRequest.Standings -> this.yahooApiRequest("/standings")
        is YahooApiRequest.TeamsData -> this.yahooApiRequest("/teams/matchups")
    }
}
