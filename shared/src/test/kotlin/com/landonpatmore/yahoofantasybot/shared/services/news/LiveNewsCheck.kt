package com.landonpatmore.yahoofantasybot.shared.services.news

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Hits the live ESPN and Sleeper feeds and prints the brief the model would be given.
 *
 * Opt-in, because it needs the network and its output changes daily. Run it when a feed
 * looks wrong or when a player is coming back with no facts:
 *
 *     NEWS_LIVE_CHECK=1 ./gradlew :shared:test --tests '*LiveNewsCheck*' -i
 */
@EnabledIfEnvironmentVariable(named = "NEWS_LIVE_CHECK", matches = ".+")
class LiveNewsCheck {

    @Test
    fun `prints the brief for a sample transaction`() {
        val players = listOf(
            PlayerInfo("Alvin Kamara", "NO", "RB"),
            PlayerInfo("Jaxson Dart", "NYG", "QB"),
            PlayerInfo("T.J. Hockenson", "Min", "TE"),
            PlayerInfo("Jakobi Meyers", "Jax", "WR")
        )

        val espn = EspnNewsClient()
        val injuries = espn.injuries()
        val headlines = espn.headlines()
        println("injury notes: ${injuries.size}, players with headlines: ${headlines.size}")

        players.forEach { player ->
            val key = PlayerNames.normalize(player.name)
            println("--- ${player.name}")
            println("    report:   ${injuries[key]?.toPromptLine() ?: "none"}")
            println("    headline: ${headlines[key]?.firstOrNull()?.toPromptLine() ?: "none"}")
        }

        println("sleeper state: ${SleeperClient().state()}")

        val brief = PlayerNewsService().brief(players)
        println(
            SchefterPrompt.user(
                "ADD/DROP",
                "My Nix in a Box added Jaxson Dart (NYG, QB) and dropped Alvin Kamara (NO, RB)",
                brief
            )
        )
        println("attribution: ${brief.attribution()}")
    }
}
