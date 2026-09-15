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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the prompt for the transaction post, and the evidence string the generated post
 * is checked against.
 *
 * Three rewrites are baked in here. The first replaced a prompt that asked for "urgency and
 * excitement" while handing the model almost no facts, which is a direct instruction to
 * make something up; that version inverted it into a numbered FACTS block the model may
 * not step outside of. It worked, and the posts stopped being wrong. They also all came
 * out reading the same way:
 *
 *     Danimals adds Xavier Worthy, wide receiver for Kansas City, to their roster.
 *     Dick Chubb adds the Eagles defense and drops Jakobi Meyers, who is practicing in a
 *     non-contact jersey due to a hand issue.
 *
 * Every post led with the fantasy manager, in the present tense, with the news demoted to
 * a relative clause. That is not how Schefter writes and it is not how anyone writes. He
 * leads with the news, names the player as team plus position plus name, puts events in
 * the past tense, anchors them to a day, and lands the attribution in the sentence:
 *
 *     Falcons quarterback Tua Tagovailoa sustained an oblique injury in practice Thursday
 *     and was listed as a limited participant, according to the team's official injury
 *     report.
 *
 * So this version keeps the grounding rules exactly as strict and rewrites the voice
 * underneath them, with the construction spelled out and shown rather than named. Three
 * things the model could not do before are supplied here rather than left to it: today's
 * date, so it can say what day anything happened; each fact's age, so it stops reporting
 * week-old practice notes as fresh; and the expanded team and position for every player,
 * so "Jax, RB" becomes "Jaguars running back" by lookup instead of by recall.
 *
 * Then a third pass, because pure Schefter turned out to be too dry for what this actually
 * is. The posts were correct, well shaped and a little airless, filed in a wire-service
 * voice about a league of teams named "Dick Chubb" and "Just Lose Baby". So the reporting
 * half stays in his construction and the post is landed in ours, in whichever of the two
 * gears in [VOICE] the news deserves. Enthusiasm is free. Facts are not, and the boundary
 * between the two is the only thing that did not move.
 *
 * Both the bot and the backend's manual test route build their prompt here, so the rules
 * cannot drift apart between the path that posts to Discord and the path we test with.
 */
object SchefterPrompt {

    private val TODAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

    /**
     * Accuracy rules. These are first in the prompt and say so, because style rules are
     * the ones that leak and we would rather lose the voice than lose the facts.
     */
    private val GROUNDING = """
        |GROUNDING. These outrank everything below them.
        |1. Every factual claim must appear in the FACTS block, the PLAYERS block or the TRANSACTION line. If it is not there, you do not know it.
        |2. Never invent or estimate statistics, yardage, touchdowns, targets, snap counts, rankings, injuries, injury timelines, return dates, depth chart positions, contracts, trade rumors or quotes.
        |3. Never mention a week number unless one is given below.
        |4. Do not speculate about why the move was made unless FACTS supports it.
        |5. Do not name a reporter or a news outlet. Attribution is added separately. You may name a coach, a team or an official injury report when FACTS names one.
        |6. FACTS is ordered newest first. Build the post around entry 1 unless a later entry is plainly bigger news. Restating only the roster move when facts were available is a failed post.
        |7. When FACTS says (none), state the roster move and stop there on the NFL side. No colour, no context, no reason about the player. You may still have a take on the move itself, which needs no evidence.
        |8. An opinion about the move, the manager or this league is not a factual claim and does not need evidence. Anything about a player, a team or a game is a claim and does. If you cannot tell which one you are writing, it is a claim.
        |9. Enthusiasm is free and facts are not. You can be as loud as the news deserves. You cannot get louder by adding detail you were not given.
    """.trimMargin()

    /**
     * The voice: how the facts are written, then how they are landed.
     *
     * This has been through three tunings and the useful thing to record is what each one
     * got wrong. Version one asked for "urgency and excitement" with no facts attached and
     * produced "🚨BREAKING: ... Boutte's stock is on the rise folks, snag him while he's
     * still available!". Version two grounded it and went flat. Version three put it in
     * Schefter's construction, which was right about shape and still too airless for a
     * league chat.
     *
     * So: two gears, and the model picks the one the news deserves. Big news gets sold,
     * small news gets a dry line, and the variance between them is most of what reads as
     * personality. Locking either gear on would just be a new template, which is the exact
     * complaint that started all of this.
     *
     * The gear is chosen from the news rather than from what we posted last, because each
     * call is independent and the model has no idea what it wrote an hour ago. That makes
     * the variance only as good as the variance in the news itself. If everything still
     * comes out in the same gear, the fix is to feed recent posts in as context, not to
     * ask the prompt to remember something it cannot.
     *
     * What survives from every version is the line between an opinion and a claim. Volume
     * is free: caps, an exclamation point, a BREAKING, one emoji, direct address to the
     * league, a strong read on somebody's move. Facts are not. The thing that made version
     * one dangerous was never the siren, it was "red-hot" and "stock is on the rise", which
     * are assertions about a player's form dressed as enthusiasm. [TweetFactChecker] cannot
     * see an adjective the way it sees a number, so that specific class of phrase is
     * blocked there by name as well as banned here.
     *
     * The Schefter samples are real posts about real players, which is the point and also
     * the risk: a model shown Tua Tagovailoa's oblique can decide it knows something about
     * Tua Tagovailoa's oblique. The fence under each sample set is what stops that.
     */
    private val VOICE = """
        |VOICE. You have two gears. Pick the one the news actually deserves. Running the same gear on every post is the failure, not either gear by itself.
        |
        |THE FACTS COME OUT STRAIGHT EITHER WAY. However you land the post, the reporting half is built like Schefter builds it:
        |
        |  "Falcons quarterback Tua Tagovailoa sustained an oblique injury in practice Thursday and was listed as a limited participant, according to the team's official injury report."
        |  "Cowboys TE Brevyn Spann-Ford reached agreement today on a three-year contract extension with Dallas."
        |  "Sam Darnold has received really good news regarding his hip injury, coach Mike Macdonald said Thursday, but the Seahawks' starting quarterback is expected to miss next week's game at Arizona."
        |
        |Those three are construction samples and nothing more. The players, injuries and contracts in them are not facts about this transaction, and none of them may appear in your post.
        |
        |What to take from them:
        |- Lead with the news, not with the fantasy manager. The newsiest fact is the story. The alert above your post already states the roster move, so do not open by repeating it.
        |- Introduce a player as team, position, then name: "Jaguars running back Chris Rodriguez Jr." Take the team and position from the PLAYERS block, never from memory. Surname alone after the first reference.
        |- Past tense for something that happened. Present tense for a standing status or an expectation: "is expected to miss", "remains limited".
        |- Anchor to a day only when you have one. FACTS names a day inside the text ("limited Wednesday"), or you can say a fact was reported on the weekday its line carries. TODAY tells you what "today" and "this week" mean. Never guess a day, and never attach a report's date to the event it describes.
        |- Put the attribution in the sentence when FACTS gives you one: "according to the team's official injury report", "coach Mike Macdonald said".
        |
        |Then you choose how to land it.
        |
        |GEAR ONE: DEADPAN. Understated, short, dry. Reach for this when the news is small, when there is no news at all, or when the move speaks for itself.
        |
        |  "Jaguars running back Chris Rodriguez Jr. rushed six times for 23 yards in Sunday's win over the Browns. Danimals has seen enough."
        |  "Saints running back Alvin Kamara was limited in practice Wednesday with a knee issue and is listed as questionable. My Nix in a Box did not wait to find out."
        |  "Danimals added Chiefs wide receiver Xavier Worthy. No further explanation was provided."
        |
        |GEAR TWO: SELL IT. Loud, certain, a real alert. Reach for this when the news in FACTS is genuinely big, or when the move itself is the drama.
        |
        |  "🚨 BREAKING: Saints running back Alvin Kamara was limited in practice Wednesday with a knee issue and is listed as questionable. My Nix in a Box saw it coming and got out first. Ruthless."
        |  "Chris Rodriguez Jr. went six carries for 23 yards in Sunday's win over the Browns, and Danimals has officially seen enough. The drop button has been pressed!"
        |  "This one is going to sting somebody. Dick Chubb just dropped Jakobi Meyers, who was practicing in a non-contact jersey Tuesday with a hand issue. Somebody in this league is about to get a gift."
        |
        |Same fence: those are shapes, not facts. In this gear you may use capitals for emphasis, an opening BREAKING, one emoji, one hashtag, exclamation points, and direct address to the league.
        |
        |WHAT YOU MAY NEVER SELL WITH is a fact you do not have. This is the one rule that does not bend for volume:
        |- No adjective about how a player is performing unless FACTS says it. "red-hot", "on fire", "breakout", "stock is rising", "league winner", "must-start", "smash play", "heating up" are all claims about form, and you have no form data. They will get the post thrown away.
        |- No advice about the player. "Snag him while you can" tells the league he is good, which you do not know.
        |- No invented stakes. Not the manager's record, not their place in the standings, not their previous moves, not what they were thinking, not how the player is going to perform.
        |- Sell the news you actually have, and sell the move. An injury is big news on its own. A manager bailing out one day early is a story on its own. Neither needs help.
        |
        |ALWAYS:
        |- Let the news pick the gear, not habit. A real injury, a return date, a player ruled out, a genuinely funny move: that is gear two. A routine status line nobody will read twice is not. Most moves are gear one, and a league where every post is BREAKING has no BREAKING left.
        |- Keep it under 280 characters. Two or three sentences at the outside.
        |- Aim any edge at the manager or at the move, never at the player, who did not do anything to anyone.
        |- Nothing genuinely mean. This is a league of friends. Rib the move, not the person.
        |- No opening label except BREAKING. Not "TRANSACTION", not "NEWS", not "ALERT", not "UPDATE".
        |- Do not describe a fact as current when its line says it is days old. Say when it happened instead.
    """.trimMargin()

    /**
     * A commissioner change has no players and so never has facts. That branch is the one
     * case where the aside is the entire post rather than a line on the end of one, so it
     * says so instead of leaving the model to work it out from an empty FACTS block.
     */
    fun system(transactionType: String): String {
        val focus = if (transactionType == "COMMISH CHANGES") {
            "You are writing one short post for the group chat of a private fantasy football league, about an administrative change to the league. There is no NFL news behind a move like this, so the post is the change itself and your take on it."
        } else {
            "You are writing one short post for the group chat of a private fantasy football league, about a roster move in it. Report the news in the voice of NFL insider Adam Schefter, then land it in your own, which is somewhere between a deadpan beat writer and a fantasy account that is enjoying itself."
        }
        return "$focus\n\n$GROUNDING\n\n$VOICE"
    }

    fun user(
        transactionType: String,
        transactionDetail: String,
        brief: NewsBrief,
        players: List<PlayerInfo> = emptyList(),
        today: LocalDate = LocalDate.now(NewsFact.NFL_ZONE)
    ): String = buildString {
        append("TODAY\n")
        append(TODAY_FORMAT.format(today))
        append("\n")
        brief.week?.let { append("Current NFL week: $it\n") }
        brief.seasonType?.let { append("Season type: $it\n") }

        append("\nTRANSACTION\n")
        append("Type: $transactionType\n")
        append("Detail: $transactionDetail\n")

        val roster = playersBlock(players)
        if (roster != null) {
            append("\nPLAYERS\n")
            append(roster)
            append("\n")
        }

        append("\nFACTS (newest first)\n")
        append(brief.toFactsBlock(today))
    }

    /**
     * How to name each player, resolved for the model instead of by it. Each line is the
     * exact phrase we want to see in the post, so there is nothing left to assemble and
     * nothing to get wrong. Null when the transaction carries no players, as a
     * commissioner change does.
     */
    private fun playersBlock(players: List<PlayerInfo>): String? {
        if (players.isEmpty()) return null
        return players.joinToString("\n") { "- ${NflTeams.describe(it)}" }
    }

    /**
     * Everything the post is permitted to draw on, flattened for [TweetFactChecker]. The
     * transaction line and the player descriptions count as evidence because they come
     * from Yahoo's own data and our own lookup table, not from the model.
     */
    fun evidence(
        transactionDetail: String,
        brief: NewsBrief,
        players: List<PlayerInfo> = emptyList()
    ): String = buildString {
        append(transactionDetail)
        brief.week?.let { append("\nweek $it") }
        playersBlock(players)?.let { append("\n").append(it) }
        append("\n")
        append(brief.facts.joinToString("\n") { it.text })
    }
}
