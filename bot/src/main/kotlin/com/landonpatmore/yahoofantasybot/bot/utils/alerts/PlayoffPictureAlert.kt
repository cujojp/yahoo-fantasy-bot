package com.landonpatmore.yahoofantasybot.bot.utils.alerts

import com.landonpatmore.yahoofantasybot.bot.bridges.MessageBridge
import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import com.landonpatmore.yahoofantasybot.bot.utils.models.YahooApiRequest
import com.landonpatmore.yahoofantasybot.shared.playoffs.PlayoffPicture
import com.landonpatmore.yahoofantasybot.shared.playoffs.PlayoffPictureMessage
import com.landonpatmore.yahoofantasybot.shared.playoffs.PlayoffXml
import org.koin.core.component.inject
import org.quartz.JobExecutionContext

/**
 * Posts who is in, who is on the bubble and who is out. Best scheduled for Tuesday,
 * once Monday night has settled the week.
 */
class PlayoffPictureAlert : BaseAlert() {
    override val name = "Playoff Picture"

    private val messageBridge: MessageBridge by inject()

    override fun execute(context: JobExecutionContext?) {
        super.execute(context)

        try {
            val settingsXml = dataRetriever.yahooApiRequest(YahooApiRequest.Settings).outerHtml()
            val settings = PlayoffXml.parseSettings(settingsXml)
            if (settings == null) {
                println("[PlayoffPictureAlert] No playoff settings in the league response, skipping")
                return
            }

            val standingsXml = dataRetriever.yahooApiRequest(YahooApiRequest.Standings).outerHtml()
            val standings = PlayoffXml.parseStandings(standingsXml)
            if (standings.teams.isEmpty()) {
                println("[PlayoffPictureAlert] No teams in the standings response, skipping")
                return
            }

            val picture = PlayoffPicture.build(settings, standings)
            messageBridge.consumer.accept(Message.PlayoffPicture(PlayoffPictureMessage.format(picture)))
        } catch (e: Exception) {
            println("[PlayoffPictureAlert] Failed to post the playoff picture: ${e.message}")
            e.printStackTrace()
        }
    }
}
