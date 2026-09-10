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

import com.landonpatmore.yahoofantasybot.shared.messaging.WebhookPayload
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.request.body.RequestBodyEntity

abstract class MessagingService(protected val url: String) : IMessagingService {

    protected abstract val name: String

    protected abstract val maxMessageLength: Int

    protected abstract fun generateRequest(message: String): RequestBodyEntity

    override fun isConfigured(): Boolean = url.isNotEmpty()

    override fun accept(t: Pair<String, String>) {
        if (url.isNotEmpty()) {
            createMessage(t)
        }
    }

    @Throws(UnirestException::class)
    override fun sendMessage(message: String): Int {
        val response = generateRequest(cleanMessage(message)).asJson()
        println("$name status code: ${response.status}")
        return response.status
    }

    /**
     * Builds the request body as real JSON.
     *
     * These payloads used to be assembled by interpolating the message straight into a
     * JSON string literal, so a double quote anywhere in the text produced malformed JSON
     * and the webhook answered 400. That went unnoticed because nothing the bot wrote had
     * quotes in it until posts started citing their sources.
     *
     * [correctMessage] converts real newlines into the two-character sequence \n so that
     * the old concatenation came out valid, so we turn those back into newlines here and
     * let the encoder do the escaping.
     */
    protected fun jsonBody(vararg fields: Pair<String, String>): String =
        WebhookPayload.of(*fields.map { (key, value) -> key to value.replace("\\n", "\n") }.toTypedArray())

    override fun createMessage(messageInfo: Pair<String, String>, title: Boolean): Int? {
        return try {
            // TODO: Remove this sleep
            val message = generateMessage(messageInfo, title)
            Thread.sleep(1000)
            if (message.length > maxMessageLength) {
                val subMessage = message.substring(0, maxMessageLength + 1)
                sendMessage(correctMessage(subMessage))
                // Returning here matters: without it the whole message was sent a second
                // time after the split, so anything over the limit posted twice.
                return createMessage(
                    Pair(messageInfo.first, message.substring(maxMessageLength + 1)),
                    false
                )
            }
            sendMessage(correctMessage(message))
        } catch (e: Exception) {
            println(e.message)
            null
        }
    }

    override fun correctMessage(message: String): String {
        val properJsonFormat = message.replace("\n", "\\n")

        return when {
            properJsonFormat.startsWith("n") -> properJsonFormat.substring(1)
            properJsonFormat.startsWith("\\") -> properJsonFormat.substring(1)
            properJsonFormat.startsWith("\\n") -> properJsonFormat.substring(2)
            properJsonFormat.endsWith("\\") -> properJsonFormat.substring(
                0,
                message.length
            )
            properJsonFormat.endsWith("\\n") -> properJsonFormat.substring(
                0,
                message.length - 1
            )
            else -> properJsonFormat
        }.trim()
    }

    override fun generateMessage(message: Pair<String, String>, title: Boolean): String {
        return if (title) {
            "${message.first}\\n${message.second}"
        } else {
            message.second
        }
    }
}
