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

package com.landonpatmore.yahoofantasybot.bot.transformers

import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import com.landonpatmore.yahoofantasybot.bot.utils.bold
import io.reactivex.rxjava3.core.Observable
import org.jsoup.nodes.Document

fun Observable<Document>.convertToStandingsMessage(): Observable<Message> =
    flatMapIterable {
        it.select("team")
    }.map {
        val name = it.select("name").text()
        val clinchedPlayoffs = it.select("clinched_playoffs").text()?.let { cp ->
            !(cp.isEmpty() || cp == "0")
        } ?: false

        val teamStandings = it.select("team_standings")
        val rank = teamStandings.select("rank").text()

        val outcomeTotals = teamStandings.select("outcome_totals")
        val wins = outcomeTotals.select("wins").text()
        val losses = outcomeTotals.select("losses").text()
        val ties = outcomeTotals.select("ties").text()

        // Yahoo omits the streak until a team has played, so this stays null in
        // preseason rather than rendering an empty "(L)".
        val streak = teamStandings.select("streak").firstOrNull()?.let { element ->
            val amount = element.select("value").text()
            if (amount.isEmpty()) {
                null
            } else {
                "$amount${if (element.select("type").text() == "win") "W" else "L"}"
            }
        }

        val pointsFor = teamStandings.select("points_for").text()
        val pointsAgainst = teamStandings.select("points_against").text()

        val finalMessage = "${if (rank.isNotEmpty()) "$rank. " else ""}${name.bold()}\\n" +
                "Record: ${"$wins-$losses-$ties".bold()}${if (streak != null) " ($streak)" else ""}\\n" +
                "PF: ${pointsFor.bold()} | PA: ${pointsAgainst.bold()}" +
                // The bold has to sit inside the line, markdown does not span newlines.
                if (clinchedPlayoffs) "\\n" + "Clinched Playoffs!".bold() else ""

        Message.Standings(finalMessage)
    }
