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

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One dated, attributed statement about a player.
 *
 * Everything the model is allowed to assert has to arrive as one of these. If a claim is
 * not traceable to a [NewsFact] then it did not come from a source, and the fact checker
 * treats it as invented.
 */
data class NewsFact(
    val playerName: String,
    val text: String,
    val source: String,
    val published: Instant?
) {
    /** The day this was reported, in the league's own timezone. */
    fun publishedOn(): LocalDate? = published?.atZone(NFL_ZONE)?.toLocalDate()

    /** How many days back this is from [today]. Null when the source gave us no date. */
    fun ageInDays(today: LocalDate): Long? =
        publishedOn()?.let { ChronoUnit.DAYS.between(it, today) }

    /**
     * Renders as `[ESPN, Wed Sep 9, 5 days ago] Alvin Kamara: ...` for the FACTS block.
     *
     * The weekday and the age are both here on purpose. The model needs the weekday to
     * write "reported Wednesday" the way a reporter would, and it needs the age so it
     * stops writing five-day-old practice news as though it happened this morning. A date
     * alone gave it neither, because nothing in the prompt said what day it was.
     */
    fun toPromptLine(today: LocalDate): String {
        val on = publishedOn()
        val attribution = if (on == null) {
            "[$source, date unknown]"
        } else {
            "[$source, ${DAY.format(on)}, ${describeAge(ChronoUnit.DAYS.between(on, today))}]"
        }
        return "$attribution $playerName: ${trim(text)}"
    }

    /**
     * Keeps one long roundup article from crowding out the other players in the
     * transaction. Cuts on a sentence boundary where there is one nearby.
     */
    private fun trim(value: String): String {
        if (value.length <= MAX_TEXT) return value
        val cut = value.take(MAX_TEXT)
        val lastStop = cut.lastIndexOf(". ")
        return if (lastStop > MAX_TEXT / 2) cut.take(lastStop + 1) else "${cut.trimEnd()}..."
    }

    companion object {
        private const val MAX_TEXT = 300

        /**
         * The NFL's own clock. UTC rolls over mid-evening on the east coast, which would
         * file a Sunday night game under Monday and have us calling it the wrong day.
         */
        val NFL_ZONE: ZoneId = ZoneId.of("America/New_York")

        private val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

        val SHORT_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(NFL_ZONE)

        internal fun describeAge(days: Long): String = when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }
}

/**
 * The full grounding set for one transaction: the facts we found, plus the league context
 * we could verify. A null [week] means we could not confirm it, in which case the model is
 * told not to mention a week at all rather than guess at one.
 */
data class NewsBrief(
    val facts: List<NewsFact>,
    val week: Int? = null,
    val seasonType: String? = null
) {
    val isEmpty: Boolean get() = facts.isEmpty()

    /**
     * The numbered FACTS block handed to the model, newest first. The order is the point:
     * the prompt tells the model to build the post around the first entry, so the freshest
     * thing we know is the thing it leads with.
     */
    fun toFactsBlock(today: LocalDate = LocalDate.now(NewsFact.NFL_ZONE)): String {
        if (facts.isEmpty()) return "(none)"
        return facts.mapIndexed { index, fact -> "${index + 1}. ${fact.toPromptLine(today)}" }
            .joinToString("\n")
    }

    /** Distinct source names, most-cited first, for the Discord attribution line. */
    fun sources(): List<String> = facts.map { it.source }.distinct()

    /**
     * `via ESPN, Sep 9`. Null when nothing was sourced, so an ungrounded post is visibly
     * missing its attribution rather than quietly claiming one.
     */
    fun attribution(): String? {
        if (facts.isEmpty()) return null
        val latest = facts.mapNotNull { it.published }.maxOrNull()
        val names = sources().joinToString(" and ")
        return if (latest != null) {
            "via $names, ${NewsFact.SHORT_DATE.format(latest)}"
        } else {
            "via $names"
        }
    }
}

/**
 * Yahoo, ESPN and Sleeper all spell names slightly differently once suffixes and
 * punctuation are involved, so we match on a normalized form.
 */
internal object PlayerNames {
    private val SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv")

    fun normalize(name: String): String =
        name.lowercase(Locale.US)
            .replace("[.'`’-]".toRegex(), "")
            .split(" ")
            .filter { it.isNotBlank() && it !in SUFFIXES }
            .joinToString(" ")

    fun lastName(name: String): String =
        normalize(name).substringAfterLast(" ")
}
