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

package com.landonpatmore.yahoofantasybot.shared.messaging

import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory

/**
 * Context information for messages being sent to capture proper history
 */
data class MessageContext(
    val title: String,
    val originalMessage: String,
    val schefterTweet: String? = null,
    val messageType: String,
    val transactionType: String? = null,
    val playersInvolved: List<String>? = null
) {
    /**
     * Gets the final message content (original + Schefter tweet if present)
     */
    fun getFinalContent(): String {
        return if (!schefterTweet.isNullOrEmpty()) {
            "$originalMessage\n\n$schefterTweet"
        } else {
            originalMessage
        }
    }
    
    /**
     * Converts to a Pair for backward compatibility with existing messaging services
     */
    fun toPair(): Pair<String, String> = Pair(title, getFinalContent())
    
    /**
     * Creates a MessageHistory record for this context
     */
    fun toMessageHistory(
        messagingService: String,
        success: Boolean,
        responseCode: Int? = null,
        errorMessage: String? = null
    ): MessageHistory {
        return MessageHistory(
            timestamp = System.currentTimeMillis(),
            messageType = messageType,
            transactionType = transactionType,
            messagingService = messagingService,
            originalMessage = originalMessage,
            schefterTweet = schefterTweet,
            finalContent = getFinalContent(),
            success = success,
            errorMessage = errorMessage,
            playersInvolved = playersInvolved,
            responseCode = responseCode
        )
    }
    
    companion object {
        /**
         * Creates MessageContext from a bot Message object
         */
        fun fromBotMessage(message: Any): MessageContext {
            return when (message) {
                is BotMessage.Transaction -> MessageContext(
                    title = message.title,
                    originalMessage = message.message,
                    schefterTweet = message.schefterTweet,
                    messageType = MessageHistory.TYPE_TRANSACTION,
                    transactionType = message.title.uppercase().replace("/", "_"),
                    playersInvolved = extractPlayersFromMessage(message.message)
                )
                is BotMessage.Alert -> MessageContext(
                    title = message.title,
                    originalMessage = message.message,
                    schefterTweet = null,
                    messageType = MessageHistory.TYPE_ALERT,
                    transactionType = message.title.uppercase().replace(" ", "_"),
                    playersInvolved = null
                )
                else -> MessageContext(
                    title = "Unknown",
                    originalMessage = message.toString(),
                    schefterTweet = null,
                    messageType = MessageHistory.TYPE_TEST,
                    transactionType = null,
                    playersInvolved = null
                )
            }
        }
        
        /**
         * Creates MessageContext for test messages
         */
        fun forTestMessage(message: String): MessageContext {
            return MessageContext(
                title = "Test Message",
                originalMessage = message,
                schefterTweet = null,
                messageType = MessageHistory.TYPE_TEST,
                transactionType = null,
                playersInvolved = null
            )
        }
        
        /**
         * Creates MessageContext for startup messages
         */
        fun forStartupMessage(): MessageContext {
            return MessageContext(
                title = "Startup",
                originalMessage = "Bot has started up successfully",
                schefterTweet = null,
                messageType = MessageHistory.TYPE_STARTUP,
                transactionType = null,
                playersInvolved = null
            )
        }
        
        /**
         * Simple extraction of player names from message text
         */
        private fun extractPlayersFromMessage(message: String): List<String>? {
            // Look for patterns like "PlayerName (TEAM, POS)"
            val playerPattern = Regex("""([A-Z][a-z]+ [A-Z][a-z]+(?:\s[A-Z][a-z]+)*)\s*\([A-Z]{2,4},\s*[A-Z]+\)""")
            val matches = playerPattern.findAll(message)
            
            val players = matches.map { it.groupValues[1].trim() }.toList()
            return if (players.isNotEmpty()) players else null
        }
    }
}

/**
 * Sealed class to represent bot messages with proper typing
 */
sealed class BotMessage(val title: String, val message: String, val schefterTweet: String? = null) {
    class Transaction(title: String, message: String, schefterTweet: String? = null) : 
        BotMessage(title, message, schefterTweet)
    class Alert(title: String, message: String) : 
        BotMessage(title, message, null)
    class Test(message: String) : 
        BotMessage("Test", message, null)
    class Startup : 
        BotMessage("Startup", "Bot has started up successfully", null)
}