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
 * Checks a generated post against the evidence it was given.
 *
 * The prompt tells the model not to invent things, and prompts leak. This is the part
 * that makes it a property rather than a hope: any specific claim the model makes that is
 * not traceable to the FACTS block or the transaction itself gets the post dropped, and
 * the alert falls back to the plain roster move.
 *
 * The checks are deliberately narrow. We look for the shapes fabrication actually takes
 * here, which is invented statistics, invented week numbers, invented rankings and
 * invented quotes. Being conservative matters: a false positive silences a good post.
 */
object TweetFactChecker {

    private val STAT_UNITS = listOf(
        "yards?", "yds?", "touchdowns?", "tds?", "catches", "receptions", "targets",
        "carries", "attempts", "completions", "sacks", "tackles", "interceptions",
        "ints?", "fumbles?", "points?", "pts", "snaps?", "games?", "seasons?", "weeks?"
    ).joinToString("|")

    private val WEEK = Regex("""\bweeks?\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val STAT = Regex("""\b(\d[\d,.]*)\s*(?:$STAT_UNITS)\b""", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("""\b(\d[\d,.]*)\s*(?:%|percent)""", RegexOption.IGNORE_CASE)
    private val RANK = Regex("""\b(?:no\.?|#)\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val RECORD = Regex("""\b(\d{1,2}-\d{1,2})\b""")
    private val QUOTE = Regex(""""([^"]{12,})"|“([^”]{12,})”""")

    /**
     * Returns a reason for every unsupported claim found. An empty list means the post
     * only asserts things present in [evidence].
     */
    fun findUnsupportedClaims(tweet: String, evidence: String): List<String> {
        val supported = canonicalize(evidence)
        val problems = mutableListOf<String>()

        WEEK.findAll(tweet).forEach { match ->
            val week = match.groupValues[1]
            if (!supported.contains("week $week")) {
                problems.add("cites week $week, which is not in the evidence")
            }
        }

        listOf(
            STAT to "statistic",
            PERCENT to "percentage",
            RANK to "ranking",
            RECORD to "record"
        ).forEach { (pattern, label) ->
            pattern.findAll(tweet).forEach { match ->
                val value = canonicalizeNumber(match.groupValues[1])
                if (!supported.contains(value)) {
                    problems.add("cites $label \"${match.value.trim()}\", which is not in the evidence")
                }
            }
        }

        QUOTE.findAll(tweet).forEach { match ->
            val quoted = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
            if (quoted.isNotEmpty() && !supported.contains(canonicalize(quoted))) {
                problems.add("quotes \"${quoted.take(40)}\", which is not in the evidence")
            }
        }

        return problems
    }

    /**
     * Lowercases, drops thousands separators and collapses whitespace so that "1,000
     * yards" in the evidence matches "1000 yards" in the post.
     */
    private fun canonicalize(text: String): String =
        text.lowercase()
            .replace(",", "")
            .replace("\\s+".toRegex(), " ")

    private fun canonicalizeNumber(value: String): String =
        value.replace(",", "").trimEnd('.')
}
