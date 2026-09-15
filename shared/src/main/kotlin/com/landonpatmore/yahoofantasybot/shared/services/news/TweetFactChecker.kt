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
 *
 * [FORM_CLAIM] is the exception to the numbers-and-quotes rule, and it exists because the
 * post is now allowed to sell. "Boutte's stock is on the rise" and "red-hot WR Kayshon
 * Boutte" are assertions about how a player is performing, we hold no performance data at
 * all, and neither one contains a digit for any of the other checks to catch. The prompt
 * bans them and prompts leak, so the handful of phrases that are always a form claim are
 * listed by name here too.
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
     * Phrases that are a claim about a player's form no matter what surrounds them.
     *
     * Kept short and unambiguous on purpose. Anything that could be a fair description of
     * a move rather than of a player stays off the list, because a false positive here
     * costs us a good post. "Rolling" and "elite" were considered and left off for that
     * reason, and "must have" and "must own" were removed after they flagged "Danimals must
     * have seen something the rest of us did not", which is a sentence about a manager and
     * exactly the kind of line this is meant to leave alone.
     */
    private val FORM_CLAIM = Regex(
        """\b(?:red[ -]?hot|on fire|breakout|breaking out|heating up|unstoppable|""" +
            """stock is (?:rising|up|soaring)|stock is on the rise|league[ -]?winn(?:er|ing)|""" +
            """must[ -]?(?:start|add)|smash play|can'?t miss|cannot miss)\b""",
        RegexOption.IGNORE_CASE
    )

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

        FORM_CLAIM.findAll(tweet).forEach { match ->
            val phrase = canonicalize(match.value)
            if (!supported.contains(phrase)) {
                problems.add("claims \"${match.value.trim()}\" about a player's form, which no source said")
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

/**
 * Trims a generated post down to the house style.
 *
 * The house style is now deliberately wide: a post is allowed to be loud, so capitals,
 * exclamation points, an opening BREAKING and an emoji are all fine. What this strips is
 * only the noise, the things that are never a voice choice.
 *
 * An emoji is capped at one and a hashtag at one. The model does not stop at one on its
 * own, and the version of this feature that ran unbounded produced posts that opened with
 * two emoji and closed with "#FantasyFootball #RosterMove", which reads as filler rather
 * than enthusiasm. "BREAKING:" survives because it is a real register. "TRANSACTION:" does
 * not, because it is a database field leaking into prose.
 */
object PostStyle {
    private const val EMOJI_CHARS = """\p{So}\p{Cs}\uFE0F\u20E3"""

    private val EMOJI = Regex("""[$EMOJI_CHARS]+""")
    private val HASHTAG = Regex("""\s*#\w+""")

    /** Three exclamation points are not three times as exciting as one. */
    private val SHOUTING = Regex("""!{2,}""")

    /**
     * A label the post gained from its own plumbing, optionally hiding behind a leading
     * emoji. The emoji is captured so that stripping the label does not take it along.
     */
    private val PLUMBING_LABEL = Regex(
        """^((?:[$EMOJI_CHARS]+\s*)?)(?:transaction|news|alert|update|roster move)\s*[:\-–—]\s*""",
        RegexOption.IGNORE_CASE
    )

    fun enforce(post: String): String =
        PLUMBING_LABEL.replace(post.trim()) { it.groupValues[1] }
            .let { keepFirstOnly(it, EMOJI) }
            .let { keepFirstOnly(it, HASHTAG) }
            .let { SHOUTING.replace(it, "!") }
            .replace(Regex(" {2,}"), " ")
            .trim()

    /** Keeps the first match of [pattern] where it stood and drops every later one. */
    private fun keepFirstOnly(post: String, pattern: Regex): String {
        var seen = false
        return pattern.replace(post) { match ->
            if (seen) "" else {
                seen = true
                match.value
            }
        }
    }
}
