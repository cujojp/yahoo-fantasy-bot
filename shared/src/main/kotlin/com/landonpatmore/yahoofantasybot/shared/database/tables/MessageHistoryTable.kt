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

package com.landonpatmore.yahoofantasybot.shared.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable

object MessageHistoryTable : UUIDTable("message_history") {
    val timestamp = long("timestamp")
    val messageType = varchar("message_type", 50) // "TRANSACTION", "ALERT", "TEST", etc.
    val transactionType = varchar("transaction_type", 50).nullable() // "ADD", "DROP", "TRADE", etc.
    val messagingService = varchar("messaging_service", 50) // "DISCORD", "SLACK", "GROUPME"
    val originalMessage = text("original_message") // The basic transaction/alert message
    val schefterTweet = text("schefter_tweet").nullable() // The generated Schefter tweet (if any)
    val finalContent = text("final_content") // The complete message sent (original + tweet)
    val success = bool("success") // Whether the message was sent successfully
    val errorMessage = text("error_message").nullable() // Error details if failed
    val playersInvolved = text("players_involved").nullable() // JSON array of player names for transactions
    val responseCode = integer("response_code").nullable() // HTTP response code from messaging service
}
