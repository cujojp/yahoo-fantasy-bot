package com.landonpatmore.yahoofantasybot.shared.playoffs

/**
 * Builds the two playoff posts from Yahoo's raw responses. The scheduled alerts in the
 * bot and the manual triggers in the backend both come through here, so they post the
 * same thing.
 */
object PlayoffPosts {
    sealed class Result {
        data class Post(val message: String) : Result()

        /** Nothing to post, and why. */
        data class Skip(val reason: String) : Result()
    }

    fun picture(settingsXml: String, standingsXml: String): Result =
        when (val read = read(settingsXml, standingsXml)) {
            is Read.Failed -> read.skip
            is Read.Ok -> Result.Post(PlayoffPictureMessage.format(read.picture))
        }

    fun odds(settingsXml: String, standingsXml: String, matchupsXml: String): Result {
        val read = read(settingsXml, standingsXml)
        if (read is Read.Failed) return read.skip
        val (picture, standings) = read as Read.Ok

        if (picture.gamesRemaining == 0) {
            return Result.Skip("The regular season is over, so there are no odds left to run")
        }

        val odds = PlayoffSimulator.simulate(picture.settings, standings, PlayoffXml.parseMatchups(matchupsXml))
            ?: return Result.Skip("No games have been played yet, so there is no scoring to model")

        return Result.Post(PlayoffOddsMessage.format(picture, odds))
    }

    private sealed class Read {
        data class Ok(val picture: PlayoffPicture, val standings: LeagueStandings) : Read()
        data class Failed(val skip: Result.Skip) : Read()
    }

    private fun read(settingsXml: String, standingsXml: String): Read {
        val settings = PlayoffXml.parseSettings(settingsXml)
            ?: return Read.Failed(Result.Skip("This league has no playoff settings"))
        val standings = PlayoffXml.parseStandings(standingsXml)
        if (standings.teams.isEmpty()) return Read.Failed(Result.Skip("Failed to parse standings data"))
        return Read.Ok(PlayoffPicture.build(settings, standings), standings)
    }
}
