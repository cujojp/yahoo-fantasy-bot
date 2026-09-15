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
import java.util.Locale

/**
 * Turns Yahoo's `(Jax, RB)` shorthand into the words a reporter would actually use.
 *
 * Schefter names a player as team, then position, then name: "Patriots wide receiver A.J.
 * Brown". Getting there from Yahoo's data means expanding an abbreviation and a position
 * code, and that expansion is the one place the model would otherwise have to supply a
 * fact of its own. "NE" is New England and "LA" is ambiguous, and a model that guesses
 * wrong puts a real player on the wrong team in a post we then have no way to fact check,
 * because the team name is not a number or a quote.
 *
 * So we look it up instead. The table is small, it does not change during a season, and it
 * keeps the rule that nothing in a post comes from the model's own memory.
 */
object NflTeams {

    /**
     * Keyed by lowercased abbreviation. Yahoo, ESPN and Sleeper disagree on a handful of
     * these, so the aliases are listed alongside the primary form rather than normalized
     * away. Relocated franchises keep their old abbreviations because historical feeds
     * still use them.
     */
    private val NICKNAMES = mapOf(
        "ari" to "Cardinals", "arz" to "Cardinals",
        "atl" to "Falcons",
        "bal" to "Ravens", "blt" to "Ravens",
        "buf" to "Bills",
        "car" to "Panthers",
        "chi" to "Bears",
        "cin" to "Bengals",
        "cle" to "Browns", "clv" to "Browns",
        "dal" to "Cowboys",
        "den" to "Broncos",
        "det" to "Lions",
        "gb" to "Packers", "gnb" to "Packers",
        "hou" to "Texans", "hst" to "Texans",
        "ind" to "Colts",
        "jax" to "Jaguars", "jac" to "Jaguars",
        "kc" to "Chiefs", "kan" to "Chiefs",
        "lac" to "Chargers", "sd" to "Chargers", "sdg" to "Chargers",
        "lar" to "Rams", "la" to "Rams", "stl" to "Rams",
        "lv" to "Raiders", "lvr" to "Raiders", "oak" to "Raiders",
        "mia" to "Dolphins",
        "min" to "Vikings",
        "ne" to "Patriots", "nwe" to "Patriots",
        "no" to "Saints", "nor" to "Saints",
        "nyg" to "Giants",
        "nyj" to "Jets",
        "phi" to "Eagles",
        "pit" to "Steelers",
        "sea" to "Seahawks",
        "sf" to "49ers", "sfo" to "49ers",
        "tb" to "Buccaneers", "tam" to "Buccaneers",
        "ten" to "Titans",
        "was" to "Commanders", "wsh" to "Commanders"
    )

    /** Yahoo's display positions, including the IDP codes leagues sometimes turn on. */
    private val POSITIONS = mapOf(
        "qb" to "quarterback",
        "rb" to "running back",
        "fb" to "fullback",
        "wr" to "wide receiver",
        "te" to "tight end",
        "k" to "kicker",
        "pk" to "kicker",
        "p" to "punter",
        "ol" to "offensive lineman",
        "dl" to "defensive lineman",
        "de" to "defensive end",
        "dt" to "defensive tackle",
        "lb" to "linebacker",
        "db" to "defensive back",
        "cb" to "cornerback",
        "s" to "safety"
    )

    private val TEAM_POSITIONS = setOf("def", "dst", "d", "d/st")

    fun nickname(abbreviation: String): String? =
        NICKNAMES[abbreviation.trim().lowercase(Locale.US)]

    /**
     * A team defense is a roster slot, not a person, so it never gets the reporter's
     * "team position name" treatment.
     */
    fun isTeamDefense(position: String): Boolean =
        position.trim().lowercase(Locale.US) in TEAM_POSITIONS

    /**
     * Yahoo gives multi-position players a comma list such as `WR,TE`. The first entry is
     * the one the player is actually known for, so that is the one we say out loud.
     */
    fun position(code: String): String? {
        val primary = code.trim().substringBefore(",").lowercase(Locale.US)
        return POSITIONS[primary]
    }

    /**
     * How a reporter would introduce this player: "Jaguars running back Chris Rodriguez
     * Jr.", or "Eagles defense" for a team unit.
     *
     * Falls back gracefully rather than guessing. An unknown team drops the team, an
     * unknown position drops the position, and if we know neither we are left with the
     * name, which is still true.
     */
    fun describe(player: PlayerInfo): String {
        val team = nickname(player.nflTeam)

        if (isTeamDefense(player.position)) {
            return if (team != null) "$team defense" else "${player.name} defense"
        }

        val position = position(player.position)
        return listOfNotNull(team, position, player.name).joinToString(" ")
    }
}
