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

import org.json.JSONObject

/**
 * Builds a webhook request body as real JSON.
 *
 * There were four hand-rolled versions of this across the bot and the backend, and every
 * one of them was wrong in a different way. Two interpolated the message straight into a
 * JSON string literal, one escaped a quote as `\\""` and so produced malformed JSON for
 * any quoted text, and the fourth escaped quotes correctly but not backslashes. The
 * result was a 400 from the webhook and, because the send paths reported success
 * regardless, no sign of it anywhere but the raw logs.
 *
 * There is exactly one of these now. Use it for anything posted to Discord, Slack or
 * GroupMe.
 */
object WebhookPayload {
    fun of(vararg fields: Pair<String, String>): String {
        val json = JSONObject()
        fields.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }
}
