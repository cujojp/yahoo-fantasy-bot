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

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads ESPN's public NFL feeds. No key, no auth, and both feeds carry a publication
 * timestamp, which is what lets us hand the model dated facts instead of vibes.
 *
 * Two feeds are useful here and both are league-wide, so we fetch once and index in
 * memory rather than making a call per player:
 *
 *  - the injury report, roughly 800 entries across all 32 teams, each with a status and
 *    a sourced beat-writer comment
 *  - the headline list, which tags articles with the athletes they are about
 *
 * Other ESPN endpoints look like they would fit better but do not work. The news feed
 * silently ignores an `athlete` query parameter and returns unrelated league news, the
 * per-team injuries route returns an empty object, and the core API's per-athlete
 * injuries route 404s. Do not reach for them again.
 */
class EspnNewsClient(private val cacheMinutes: Long = DEFAULT_CACHE_MINUTES) {

    companion object {
        private const val INJURIES_URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/injuries"
        private const val NEWS_URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/news?limit=50"
        private const val DEFAULT_CACHE_MINUTES = 15L
        private const val SOURCE = "ESPN"

        /** ESPN stamps injuries as `2026-09-09T21:13Z` and articles as `...T21:13:07Z`. */
        private val INJURY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

        internal fun parseInstant(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            return try {
                Instant.parse(raw)
            } catch (e: DateTimeParseException) {
                try {
                    LocalDateTime.parse(raw, INJURY_DATE).toInstant(ZoneOffset.UTC)
                } catch (e2: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private var injuryCache: Cached<Map<String, NewsFact>>? = null
    private var headlineCache: Cached<Map<String, List<NewsFact>>>? = null

    private data class Cached<T>(val value: T, val fetchedAt: LocalDateTime)

    private fun <T> Cached<T>?.freshOrNull(): T? = this
        ?.takeIf { java.time.Duration.between(it.fetchedAt, LocalDateTime.now()).toMinutes() < cacheMinutes }
        ?.value

    /**
     * Current injury report, keyed by normalized player name. Only entries carrying an
     * actual comment are kept: a bare "Active" row tells the model nothing and would just
     * dilute the FACTS block.
     */
    fun injuries(): Map<String, NewsFact> {
        injuryCache.freshOrNull()?.let { return it }

        val body = HttpJson.getObject(INJURIES_URL) ?: return injuryCache?.value ?: emptyMap()
        val indexed = mutableMapOf<String, NewsFact>()

        val teams = body.optJSONArray("injuries") ?: JSONArray()
        for (t in 0 until teams.length()) {
            val entries = teams.optJSONObject(t)?.optJSONArray("injuries") ?: continue
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val name = entry.optJSONObject("athlete")?.optString("displayName").orEmpty()
                if (name.isBlank()) continue

                val comment = entry.optString("shortComment").ifBlank { entry.optString("longComment") }
                if (comment.isBlank()) continue

                val status = entry.optString("status").ifBlank { "Unknown" }
                val published = parseInstant(entry.optString("date"))
                val key = PlayerNames.normalize(name)

                // Keep the most recent entry when a player appears more than once.
                val existing = indexed[key]
                if (existing != null && existing.published != null && published != null &&
                    existing.published >= published
                ) {
                    continue
                }

                indexed[key] = NewsFact(
                    playerName = name,
                    text = "$status ($comment)",
                    source = SOURCE,
                    published = published
                )
            }
        }

        println("EspnNewsClient: indexed ${indexed.size} injury notes")
        injuryCache = Cached(indexed, LocalDateTime.now())
        return indexed
    }

    /**
     * Recent headlines keyed by normalized player name.
     *
     * We only index articles ESPN has explicitly tagged with an athlete. Matching on a
     * last name appearing somewhere in the body would attach the wrong player's news to a
     * transaction, which is the exact failure we are trying to eliminate.
     */
    fun headlines(): Map<String, List<NewsFact>> {
        headlineCache.freshOrNull()?.let { return it }

        val body = HttpJson.getObject(NEWS_URL) ?: return headlineCache?.value ?: emptyMap()
        val indexed = mutableMapOf<String, MutableList<NewsFact>>()

        val articles = body.optJSONArray("articles") ?: JSONArray()
        for (a in 0 until articles.length()) {
            val article = articles.optJSONObject(a) ?: continue
            val headline = article.optString("headline")
            if (headline.isBlank()) continue

            val description = article.optString("description")
            val published = parseInstant(article.optString("published"))
            val text = if (description.isBlank()) headline else "$headline. $description"

            for (name in taggedAthletes(article)) {
                indexed.getOrPut(PlayerNames.normalize(name)) { mutableListOf() }
                    .add(NewsFact(name, text, SOURCE, published))
            }
        }

        val result = indexed.mapValues { (_, facts) ->
            facts.sortedByDescending { it.published ?: Instant.EPOCH }
        }
        println("EspnNewsClient: indexed headlines for ${result.size} players")
        headlineCache = Cached(result, LocalDateTime.now())
        return result
    }

    private fun taggedAthletes(article: JSONObject): List<String> {
        val categories = article.optJSONArray("categories") ?: return emptyList()
        val names = mutableListOf<String>()
        for (c in 0 until categories.length()) {
            val category = categories.optJSONObject(c) ?: continue
            if (category.optString("type") != "athlete") continue
            val name = category.optJSONObject("athlete")?.optString("description")
                ?: category.optString("description")
            if (!name.isNullOrBlank()) names.add(name)
        }
        return names
    }
}
