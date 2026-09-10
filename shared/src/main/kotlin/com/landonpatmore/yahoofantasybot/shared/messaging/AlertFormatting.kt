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

import com.landonpatmore.yahoofantasybot.shared.database.models.MessagingService

/**
 * How an alert looks once it reaches Discord, Slack or GroupMe.
 *
 * The scheduled alerts and the manual triggers in the web UI both post the same
 * four alerts, and they used to shape them separately, which is how the manual
 * ones ended up with no title and no block quote. Both go through here now.
 *
 * Newlines are a literal backslash and n rather than a real newline. They are
 * embedded straight into the JSON body of the webhook request, which is what
 * turns them into real newlines on the other end.
 */
object AlertFormatting {
    const val NEW_LINE = "\\n"

    // The alert names that go into the title banner.
    const val SCORE = "Score"
    const val CLOSE_SCORE = "Close Score"
    const val MATCH_UP = "Match Up"
    const val STANDINGS = "Standings"

    /** For example: 📣 **MATCH UP ALERT** followed by a rule. */
    fun title(alertName: String): String =
        "📣 **${"$alertName Alert".uppercase()}**${NEW_LINE}━━━━━━━━━"

    /** Discord and Slack block quote the body. GroupMe has no equivalent. */
    fun body(service: Int, message: String): String = when (service) {
        MessagingService.DISCORD, MessagingService.SLACK -> ">>> $message"
        else -> message
    }

    /** Discord takes ** as bold, Slack wants a single *, GroupMe has no markdown. */
    fun applyMarkdown(service: Int, message: String): String = when (service) {
        MessagingService.SLACK -> message.replace("**", "*")
        MessagingService.GROUP_ME -> message.replace("**", "")
        else -> message
    }

    /** The finished post: title banner, then the block quoted body. */
    fun render(service: Int, alertName: String, message: String): String =
        applyMarkdown(service, "${title(alertName)}$NEW_LINE${body(service, message)}")
}
