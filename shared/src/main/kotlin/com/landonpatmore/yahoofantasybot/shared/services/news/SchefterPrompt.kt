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

package com.landonpatmore.yahoofantasybot.shared.services.news

/**
 * Builds the prompt for the transaction post, and the evidence string the generated post
 * is checked against.
 *
 * The old prompt asked for "urgency and excitement" and handed the model almost no facts,
 * which is a direct instruction to make something up. This one inverts that: the model
 * gets a numbered FACTS block and is allowed to assert nothing outside it. Tone is a
 * distant second to being right.
 *
 * Both the bot and the backend's manual test route build their prompt here, so the rules
 * cannot drift apart between the path that posts to Discord and the path we test with.
 */
object SchefterPrompt {

    private val RULES = """
        |Absolute rules, in priority order:
        |1. Every factual claim must appear in the FACTS block or the TRANSACTION line. If it is not there, you do not know it.
        |2. Never invent or estimate statistics, yardage, touchdowns, targets, snap counts, rankings, injuries, injury timelines, return dates, depth chart positions, contracts, trade rumors or quotes.
        |3. Never mention a week number unless one is given below.
        |4. Do not speculate about why the move was made unless FACTS supports it.
        |5. When FACTS has entries, build the post around the most newsworthy one. Name the player it concerns and say what it reports, in your own words. Restating only the roster move when facts were available is a failed post.
        |6. When FACTS says (none), write one plain sentence stating only the roster move, and stop. Do not add color, context or a reason.
        |7. Do not name a source, a reporter or an outlet. Attribution is added separately.
        |8. Do not open with a label such as "TRANSACTION", "BREAKING" or "NEWS". Start with the move or the news itself.
        |
        |Style: one or two sentences, under 280 characters, present tense, clipped and factual like an NFL insider report. At most one emoji. No hashtags.
    """.trimMargin()

    fun system(transactionType: String): String {
        val focus = if (transactionType == "COMMISH CHANGES") {
            "Write a short post about a fantasy league administrative change, in the style of NFL insider Adam Schefter."
        } else {
            "Write a short post about a fantasy football roster move, in the style of NFL insider Adam Schefter."
        }
        return "$focus\n\n$RULES"
    }

    fun user(transactionType: String, transactionDetail: String, brief: NewsBrief): String =
        buildString {
            append("TRANSACTION\n")
            append("Type: $transactionType\n")
            append("Detail: $transactionDetail\n")
            brief.week?.let { append("Current NFL week: $it\n") }
            brief.seasonType?.let { append("Season type: $it\n") }
            append("\nFACTS\n")
            append(brief.toFactsBlock())
        }

    /**
     * Everything the post is permitted to draw on, flattened for [TweetFactChecker]. The
     * transaction line counts as evidence because it is Yahoo's own data.
     */
    fun evidence(transactionDetail: String, brief: NewsBrief): String =
        buildString {
            append(transactionDetail)
            brief.week?.let { append("\nweek $it") }
            append("\n")
            append(brief.facts.joinToString("\n") { it.text })
        }
}
