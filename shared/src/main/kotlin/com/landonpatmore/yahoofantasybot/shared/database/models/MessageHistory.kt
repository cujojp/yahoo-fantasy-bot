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

package com.landonpatmore.yahoofantasybot.shared.database.models

import java.util.*

data class MessageHistory(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val messageType: String, // "TRANSACTION", "ALERT", "TEST", etc.
    val transactionType: String? = null, // "ADD", "DROP", "TRADE", etc.
    val messagingService: String, // "DISCORD", "SLACK", "GROUPME"
    val originalMessage: String, // The basic transaction/alert message
    val schefterTweet: String? = null, // The generated Schefter tweet (if any)
    val finalContent: String, // The complete message sent (original + tweet)
    val success: Boolean, // Whether the message was sent successfully
    val errorMessage: String? = null, // Error details if failed
    val playersInvolved: List<String>? = null, // Player names for transactions
    val responseCode: Int? = null // HTTP response code from messaging service
) {
    companion object {
        // Message type constants
        const val TYPE_TRANSACTION = "TRANSACTION"
        const val TYPE_ALERT = "ALERT"
        const val TYPE_TEST = "TEST"
        const val TYPE_STARTUP = "STARTUP"
        
        // Transaction type constants
        const val TRANSACTION_ADD = "ADD"
        const val TRANSACTION_DROP = "DROP"
        const val TRANSACTION_ADD_DROP = "ADD/DROP"
        const val TRANSACTION_TRADE = "TRADE"
        const val TRANSACTION_COMMISH = "COMMISH"
        
        // Messaging service constants
        const val SERVICE_DISCORD = "DISCORD"
        const val SERVICE_SLACK = "SLACK"
        const val SERVICE_GROUPME = "GROUPME"
        
        // Alert type constants
        const val ALERT_SCORE = "SCORE"
        const val ALERT_CLOSE_SCORE = "CLOSE_SCORE"
        const val ALERT_STANDINGS = "STANDINGS"
        const val ALERT_MATCHUP = "MATCHUP"
    }
    
    /**
     * Returns true if this message included a Schefter tweet
     */
    fun hasSchefterTweet(): Boolean = !schefterTweet.isNullOrEmpty()
    
    /**
     * Returns a short description of the message for logging
     */
    fun getDescription(): String {
        val typeDesc = when (messageType) {
            TYPE_TRANSACTION -> transactionType ?: "TRANSACTION"
            TYPE_ALERT -> "ALERT"
            TYPE_TEST -> "TEST"
            TYPE_STARTUP -> "STARTUP"
            else -> messageType
        }
        
        val players = if (!playersInvolved.isNullOrEmpty()) {
            " (${playersInvolved.joinToString(", ")})"
        } else ""
        
        val tweet = if (hasSchefterTweet()) " + Schefter tweet" else ""
        
        return "$typeDesc$players$tweet → $messagingService"
    }
}
