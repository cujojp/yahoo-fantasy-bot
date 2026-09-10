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
import java.time.LocalDateTime

/**
 * Reads Sleeper's public NFL endpoints. No key and no auth.
 *
 * Sleeper earns its place here for two things Yahoo and ESPN do not give us cleanly: an
 * authoritative week and season type, and a player table carrying Yahoo's own player id.
 * That id is how we match a Yahoo transaction to a player without guessing at names.
 */
class SleeperClient(
    private val playerMapEnabled: Boolean = System.getenv("SLEEPER_PLAYER_MAP")?.lowercase() != "false"
) {

    companion object {
        private const val STATE_URL = "https://api.sleeper.app/v1/state/nfl"
        private const val PLAYERS_URL = "https://api.sleeper.app/v1/players/nfl"
        private const val STATE_CACHE_MINUTES = 30L
        private const val PLAYER_CACHE_HOURS = 24L
        private const val SOURCE = "Sleeper"

        /**
         * The player table is about 15 MB of JSON. Parsing it transiently costs far more
         * than that, so we skip it outright on a small container rather than risk taking
         * the bot down for an optional enrichment.
         */
        private const val MIN_HEAP_BYTES = 384L * 1024 * 1024
    }

    data class SeasonState(val week: Int?, val seasonType: String?, val season: String?)

    data class SleeperPlayer(
        val fullName: String,
        val team: String?,
        val position: String?,
        val injuryStatus: String?,
        val injuryBodyPart: String?,
        val newsUpdated: Instant?
    ) {
        /**
         * An injury fact, or null when the player is healthy. "NA" is Sleeper's way of
         * saying it has nothing, and reporting that as news would be noise.
         */
        fun toInjuryFact(): NewsFact? {
            val status = injuryStatus?.takeIf { it.isNotBlank() && it != "NA" } ?: return null
            val detail = listOfNotNull(injuryBodyPart?.takeIf { it.isNotBlank() })
                .joinToString(", ")
            val text = if (detail.isEmpty()) "Listed $status" else "Listed $status ($detail)"
            return NewsFact(fullName, text, SOURCE, newsUpdated)
        }
    }

    private var stateCache: Pair<SeasonState, LocalDateTime>? = null
    private var playerCache: Pair<Map<String, SleeperPlayer>, LocalDateTime>? = null
    private var playerMapFailed = false

    /**
     * Current week and season type straight from Sleeper. Cheap enough to keep fresh, and
     * a useful independent check on Yahoo's `current_week`.
     */
    fun state(): SeasonState? {
        stateCache?.let { (state, fetchedAt) ->
            if (java.time.Duration.between(fetchedAt, LocalDateTime.now()).toMinutes() < STATE_CACHE_MINUTES) {
                return state
            }
        }

        val body = HttpJson.getObject(STATE_URL) ?: return stateCache?.first
        val state = SeasonState(
            week = body.optInt("week", -1).takeIf { it > 0 },
            seasonType = body.optString("season_type").takeIf { it.isNotBlank() },
            season = body.optString("season").takeIf { it.isNotBlank() }
        )
        stateCache = Pair(state, LocalDateTime.now())
        return state
    }

    /** Looks up a player by the Yahoo player id carried on the transaction. */
    fun playerByYahooId(yahooId: String?): SleeperPlayer? {
        if (yahooId.isNullOrBlank()) return null
        return players()[yahooId]
    }

    /**
     * Yahoo id to player, built once a day. Only players Sleeper has a Yahoo id for are
     * retained, which is roughly half the table and all of the ones a transaction can
     * reference.
     */
    private fun players(): Map<String, SleeperPlayer> {
        if (!playerMapEnabled || playerMapFailed) return emptyMap()

        playerCache?.let { (map, fetchedAt) ->
            if (java.time.Duration.between(fetchedAt, LocalDateTime.now()).toHours() < PLAYER_CACHE_HOURS) {
                return map
            }
        }

        if (Runtime.getRuntime().maxMemory() < MIN_HEAP_BYTES) {
            println("SleeperClient: heap below ${MIN_HEAP_BYTES / 1024 / 1024}MB, skipping player map")
            playerMapFailed = true
            return emptyMap()
        }

        val body = HttpJson.getObject(PLAYERS_URL) ?: run {
            // Keep serving the previous map if we have one rather than losing enrichment.
            return playerCache?.first ?: emptyMap()
        }

        val map = mutableMapOf<String, SleeperPlayer>()
        for (key in body.keys()) {
            val player = body.optJSONObject(key) ?: continue
            val yahooId = player.opt("yahoo_id")?.takeIf { it != org.json.JSONObject.NULL }?.toString()
            if (yahooId.isNullOrBlank()) continue
            val fullName = player.optString("full_name").takeIf { it.isNotBlank() } ?: continue

            map[yahooId] = SleeperPlayer(
                fullName = fullName,
                team = player.optString("team").takeIf { it.isNotBlank() },
                position = player.optString("position").takeIf { it.isNotBlank() },
                injuryStatus = player.optString("injury_status").takeIf { it.isNotBlank() },
                injuryBodyPart = player.optString("injury_body_part").takeIf { it.isNotBlank() },
                newsUpdated = player.optLong("news_updated", 0L).takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it) }
            )
        }

        println("SleeperClient: mapped ${map.size} players by Yahoo id")
        playerCache = Pair(map, LocalDateTime.now())
        return map
    }
}
