package com.landonpatmore.yahoofantasybot.shared.playoffs

import com.landonpatmore.yahoofantasybot.shared.messaging.AlertFormatting
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The body of the Playoff Odds post: the same table as the playoff picture, with each
 * team's chances in place of games back.
 *
 * What is certain comes from [PlayoffPicture], not the simulation. A team the math has
 * clinched shows a check, never "100%", and a team the simulation never put in the field
 * shows "<1%" unless the math has actually eliminated it.
 */
object PlayoffOddsMessage {
    private const val NEW_LINE = AlertFormatting.NEW_LINE

    fun format(picture: PlayoffPicture, odds: PlayoffOdds): String {
        val lines = mutableListOf(header(picture, odds), "")
        val spots = picture.spots
        val byes = picture.settings.numByes
        val names = picture.teams.associate { it.team.teamKey to it.team.name }

        picture.teams.forEachIndexed { index, team ->
            if (index == spots && picture.teams.drop(spots).any { it.status != PlayoffStatus.ELIMINATED }) {
                lines += "━━ cut line ━━"
            }
            if (team.status == PlayoffStatus.ELIMINATED) return@forEachIndexed

            val chances = odds.teams[team.team.teamKey] ?: TeamOdds(0.0, 0.0)
            val line = when (team.status) {
                PlayoffStatus.CLINCHED_BYE -> "✅ clinched a bye"
                PlayoffStatus.CLINCHED -> "✅ clinched" + byeOdds(byes, chances)
                else -> "${percent(chances.playoffs)} in" + byeOdds(byes, chances)
            }
            lines += "${team.team.seed}. **${team.team.name}** (${team.team.record}) · $line"
        }

        val eliminated = picture.teams.filter { it.status == PlayoffStatus.ELIMINATED }
        if (eliminated.isNotEmpty()) {
            lines += ""
            lines += "❌ Eliminated: ${eliminated.joinToString(", ") { it.team.name }}"
        }

        odds.gameOfTheWeek?.let { game ->
            val home = names[game.home] ?: return@let
            val away = names[game.away] ?: return@let
            // Only the sides whose odds actually move. A locked-in team gains nothing from a win.
            val stakes = listOf(home to game.homeSwing, away to game.awaySwing)
                .filter { (_, swing) -> swing >= 0.005 }
            if (stakes.isEmpty()) return@let
            val (first, firstSwing) = stakes.first()
            val rest = stakes.drop(1).joinToString("") { (name, swing) -> " and ${(swing * 100).roundToInt()} to $name's" }
            lines += ""
            lines += "📈 Game of the week: **$home** vs **$away**. " +
                    "Winning adds ${points(firstSwing)} to $first's playoff odds$rest."
        }

        lines += ""
        lines += "Based on each team's scoring this season. Ties in the standings go to points for."

        return lines.joinToString(NEW_LINE)
    }

    private fun header(picture: PlayoffPicture, odds: PlayoffOdds): String {
        val week = picture.currentWeek?.let { "Week $it · " }.orEmpty()
        val remaining = if (picture.gamesRemaining == 1) "Final week" else "${picture.gamesRemaining} weeks left"
        val runs = NumberFormat.getIntegerInstance(Locale.US).format(odds.simulations)
        return "$week$remaining · $runs simulated seasons"
    }

    private fun byeOdds(byes: Int, chances: TeamOdds): String =
        if (byes > 0 && chances.bye >= 0.005) " · ${percent(chances.bye)} bye" else ""

    /** Never rounds to a certainty the math hasn't confirmed. */
    private fun percent(chance: Double): String = when {
        chance >= 0.995 -> ">99%"
        chance < 0.005 -> "<1%"
        else -> "${(chance * 100).roundToInt()}%"
    }

    private fun points(swing: Double): String = "${(swing * 100).roundToInt()} points"
}
