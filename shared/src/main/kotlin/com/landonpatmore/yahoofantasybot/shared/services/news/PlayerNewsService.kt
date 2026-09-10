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

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import com.landonpatmore.yahoofantasybot.shared.services.YahooNewsService

/**
 * Assembles the real, dated, attributed facts about the players in a transaction.
 *
 * This is the grounding layer. Nothing here summarises or interprets: it collects
 * statements that a named source published at a known time, and everything it cannot
 * source it simply leaves out. An empty brief is a valid and common answer, and the
 * prompt handles it by writing a plain roster line.
 *
 * Sources, in the order we prefer them per player:
 *
 *  1. ESPN's player report, which carries a dated beat-writer comment and is always about
 *     that one player
 *  2. ESPN headlines ESPN itself tagged to that athlete, which are richer but sometimes a
 *     roundup that only mentions them in passing
 *  3. Yahoo's own injury designation for the player
 *  4. Sleeper's injury status, as a last-resort cross-check
 */
class PlayerNewsService(
    private val yahooNewsService: YahooNewsService? = null,
    private val espn: EspnNewsClient = EspnNewsClient(),
    private val sleeper: SleeperClient = SleeperClient()
) {

    companion object {
        /** Enough players to cover both sides of an add/drop or a small trade. */
        private const val MAX_PLAYERS = 4

        /** Keeps one player's news from crowding out the rest of the transaction. */
        private const val MAX_FACTS_PER_PLAYER = 2

        private const val MAX_FACTS = 6
    }

    fun brief(players: List<PlayerInfo>): NewsBrief {
        val week = resolveWeek()
        val seasonType = sleeper.state()?.seasonType

        if (players.isEmpty()) {
            return NewsBrief(emptyList(), week, seasonType)
        }

        val injuries = espn.injuries()
        val headlines = espn.headlines()
        val facts = mutableListOf<NewsFact>()

        for (player in players.take(MAX_PLAYERS)) {
            val key = PlayerNames.normalize(player.name)
            val forPlayer = mutableListOf<NewsFact>()

            injuries[key]?.let { forPlayer.add(it) }

            if (forPlayer.size < MAX_FACTS_PER_PLAYER) {
                headlines[key]?.take(MAX_FACTS_PER_PLAYER - forPlayer.size)
                    ?.let { forPlayer.addAll(it) }
            }

            if (forPlayer.isEmpty()) {
                yahooNewsService?.playerFact(player)?.let { forPlayer.add(it) }
            }

            if (forPlayer.isEmpty()) {
                sleeper.playerByYahooId(player.playerId)?.toInjuryFact()?.let { forPlayer.add(it) }
            }

            facts.addAll(forPlayer.take(MAX_FACTS_PER_PLAYER))
            println("PlayerNewsService: ${player.name} -> ${forPlayer.size} fact(s)")
        }

        val brief = NewsBrief(facts.take(MAX_FACTS), week, seasonType)
        println("PlayerNewsService: ${brief.facts.size} fact(s), week=$week, seasonType=$seasonType")
        return brief
    }

    /**
     * Yahoo's league metadata is the source of truth for our league's week. Sleeper is the
     * fallback so a Yahoo 403 does not silently cost us the week, and null means we could
     * not confirm it, in which case no week is mentioned at all.
     */
    private fun resolveWeek(): Int? = yahooNewsService?.currentWeek() ?: sleeper.state()?.week
}
