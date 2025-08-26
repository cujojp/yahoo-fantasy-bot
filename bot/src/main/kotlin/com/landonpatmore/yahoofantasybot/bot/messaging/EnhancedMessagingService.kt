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

package com.landonpatmore.yahoofantasybot.bot.messaging

import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory
import com.landonpatmore.yahoofantasybot.shared.messaging.MessageContext
import com.mashape.unirest.http.exceptions.UnirestException

/**
 * Enhanced messaging service that wraps existing services and adds message history logging
 */
class EnhancedMessagingService(
    private val wrappedService: IMessagingService,
    private val serviceName: String,
    private val database: Db
) : IMessagingService {

    override fun accept(t: Pair<String, String>) {
        // Create a basic message context for backward compatibility
        val context = MessageContext(
            title = t.first,
            originalMessage = t.second,
            messageType = determineMessageType(t.first),
            transactionType = if (isTransactionMessage(t.first)) t.first else null,
            playersInvolved = extractPlayersFromContent(t.second)
        )
        
        sendMessageWithHistory(context)
    }

    override fun sendMessage(message: String) {
        // For direct string messages, create minimal context
        val context = MessageContext.forTestMessage(message)
        sendMessageWithHistory(context)
    }

    override fun createMessage(message: Pair<String, String>, title: Boolean) {
        val context = MessageContext(
            title = message.first,
            originalMessage = message.second,
            messageType = determineMessageType(message.first),
            transactionType = if (isTransactionMessage(message.first)) message.first else null,
            playersInvolved = extractPlayersFromContent(message.second)
        )
        
        sendMessageWithHistory(context)
    }

    override fun cleanMessage(message: String): String {
        return wrappedService.cleanMessage(message)
    }

    override fun correctMessage(message: String): String {
        return wrappedService.correctMessage(message)
    }

    override fun generateMessage(message: Pair<String, String>, title: Boolean): String {
        return wrappedService.generateMessage(message, title)
    }

    /**
     * Sends a message with MessageContext and logs to history
     */
    fun sendMessageWithContext(context: MessageContext) {
        sendMessageWithHistory(context)
    }

    /**
     * Internal method to send message and log history
     */
    private fun sendMessageWithHistory(context: MessageContext) {
        var responseCode: Int? = null
        var success = false
        var errorMessage: String? = null

        try {
            println("EnhancedMessagingService: Sending ${context.messageType} message to $serviceName")
            
            // Use the wrapped service to send the message
            wrappedService.createMessage(context.toPair())
            
            success = true
            responseCode = 200 // Assume success if no exception
            println("EnhancedMessagingService: Message sent successfully to $serviceName")
            
        } catch (e: UnirestException) {
            errorMessage = "HTTP Error: ${e.message}"
            responseCode = extractResponseCode(e)
            println("EnhancedMessagingService: HTTP error sending to $serviceName: ${e.message}")
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            println("EnhancedMessagingService: Error sending to $serviceName: ${e.message}")
        }

        // Save to message history
        try {
            val messageHistory = context.toMessageHistory(
                messagingService = serviceName,
                success = success,
                responseCode = responseCode,
                errorMessage = errorMessage
            )
            
            database.saveMessageHistory(messageHistory)
        } catch (e: Exception) {
            println("EnhancedMessagingService: Failed to save message history: ${e.message}")
        }
    }

    /**
     * Determines message type from title
     */
    private fun determineMessageType(title: String): String {
        return when {
            isTransactionMessage(title) -> MessageHistory.TYPE_TRANSACTION
            isAlertMessage(title) -> MessageHistory.TYPE_ALERT
            title.contains("test", ignoreCase = true) -> MessageHistory.TYPE_TEST
            title.contains("startup", ignoreCase = true) -> MessageHistory.TYPE_STARTUP
            else -> MessageHistory.TYPE_TEST
        }
    }

    /**
     * Checks if this is a transaction message
     */
    private fun isTransactionMessage(title: String): Boolean {
        val transactionTypes = listOf("add", "drop", "trade", "commish")
        return transactionTypes.any { title.contains(it, ignoreCase = true) }
    }

    /**
     * Checks if this is an alert message
     */
    private fun isAlertMessage(title: String): Boolean {
        val alertTypes = listOf("score", "standings", "match", "close")
        return alertTypes.any { title.contains(it, ignoreCase = true) }
    }

    /**
     * Simple extraction of player names from message content
     */
    private fun extractPlayersFromContent(content: String): List<String>? {
        val playerPattern = Regex("""([A-Z][a-z]+ [A-Z][a-z]+(?:\s[A-Z][a-z]+)*)\s*\([A-Z]{2,4},\s*[A-Z]+\)""")
        val matches = playerPattern.findAll(content)
        
        val players = matches.map { it.groupValues[1].trim() }.toList()
        return if (players.isNotEmpty()) players else null
    }

    /**
     * Extracts response code from UnirestException
     */
    private fun extractResponseCode(e: UnirestException): Int? {
        return try {
            // Try to extract status code from exception message
            val statusPattern = Regex("""status (\d+)""")
            val match = statusPattern.find(e.message ?: "")
            match?.groupValues?.get(1)?.toIntOrNull()
        } catch (ex: Exception) {
            null
        }
    }
}
