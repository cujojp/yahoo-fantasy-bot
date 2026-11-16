package com.landonpatmore.yahoofantasybot.shared.playoffs

import com.landonpatmore.yahoofantasybot.shared.messaging.AlertFormatting

/**
 * The body of the Playoff Picture post. The title banner and block quote are added by
 * [AlertFormatting], the same as every other alert.
 *
 * It is one post rather than one per team, so the cut line reads as a single table.
 */
object PlayoffPictureMessage {
    private const val NEW_LINE = AlertFormatting.NEW_LINE

    fun format(picture: PlayoffPicture): String {
        val lines = mutableListOf(header(picture), "")
        val spots = picture.spots

        picture.teams.forEachIndexed { index, team ->
            if (index == spots && picture.teams.drop(spots).any { it.status != PlayoffStatus.ELIMINATED }) {
                lines += "━━ cut line ━━"
            }
            if (team.status != PlayoffStatus.ELIMINATED) {
                lines += "${team.team.seed}. **${team.team.name}** (${team.team.record})${note(picture, index, team)}"
            }
        }

        val eliminated = picture.teams.filter { it.status == PlayoffStatus.ELIMINATED }
        if (eliminated.isNotEmpty()) {
            lines += ""
            lines += "❌ Eliminated: ${eliminated.joinToString(", ") { it.team.name }}"
        }

        bubbleWatch(picture)?.let {
            lines += ""
            lines += it
        }

        return lines.joinToString(NEW_LINE)
    }

    private fun header(picture: PlayoffPicture): String {
        val week = picture.currentWeek?.let { "Week $it · " }.orEmpty()
        val remaining = when (val games = picture.gamesRemaining) {
            0 -> "Regular season over"
            1 -> "Final week of the regular season"
            else -> "$games weeks left"
        }
        val byes = picture.settings.numByes
        val field = "top ${picture.spots} make it" + if (byes > 0) ", top $byes get a bye" else ""
        return "$week$remaining · $field"
    }

    private fun note(picture: PlayoffPicture, index: Int, team: PlayoffTeam): String {
        val byes = picture.settings.numByes
        return when (team.status) {
            PlayoffStatus.CLINCHED_BYE -> " ✅ clinched a bye"
            PlayoffStatus.CLINCHED -> " ✅ clinched"
            PlayoffStatus.IN -> when {
                index < byes -> " · bye spot"
                index == picture.spots - 1 -> " · last spot in"
                else -> ""
            }
            PlayoffStatus.OUT -> when (val back = team.gamesBack) {
                null -> ""
                0.0 -> " · tied for the last spot, out on tiebreaker"
                else -> " · ${back.asGames()} GB"
            }
            PlayoffStatus.ELIMINATED -> ""
        }
    }

    /** Teams outside the field but within a game of it, while there are still games left. */
    private fun bubbleWatch(picture: PlayoffPicture): String? {
        if (picture.gamesRemaining == 0) return null
        val bubble = picture.teams.filter {
            it.status == PlayoffStatus.OUT && (it.gamesBack ?: Double.MAX_VALUE) <= 1.0
        }
        if (bubble.isEmpty()) return null

        val names = bubble.map { it.team.name }
        val joined = if (names.size == 1) names.single()
            else names.dropLast(1).joinToString(", ") + " and " + names.last()
        val verb = if (names.size == 1) "is" else "are"
        return "🔥 Bubble watch: $joined $verb within a game of the last spot."
    }

    private fun Double.asGames(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()
}
