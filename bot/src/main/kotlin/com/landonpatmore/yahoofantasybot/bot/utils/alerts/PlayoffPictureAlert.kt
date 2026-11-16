package com.landonpatmore.yahoofantasybot.bot.utils.alerts

import com.landonpatmore.yahoofantasybot.bot.bridges.MessageBridge
import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import com.landonpatmore.yahoofantasybot.bot.utils.models.YahooApiRequest
import com.landonpatmore.yahoofantasybot.shared.playoffs.PlayoffPosts
import org.koin.core.component.inject
import org.quartz.JobExecutionContext

/** Fetches what a playoff post needs from Yahoo and hands the finished post to the chat. */
abstract class PlayoffAlert : BaseAlert() {
    private val messageBridge: MessageBridge by inject()

    protected abstract fun build(): PlayoffPosts.Result

    protected abstract fun message(body: String): Message

    protected fun xml(request: YahooApiRequest): String = dataRetriever.yahooApiRequest(request).outerHtml()

    override fun execute(context: JobExecutionContext?) {
        super.execute(context)

        try {
            when (val result = build()) {
                is PlayoffPosts.Result.Post -> messageBridge.consumer.accept(message(result.message))
                is PlayoffPosts.Result.Skip -> println("[$name] Skipping: ${result.reason}")
            }
        } catch (e: Exception) {
            println("[$name] Failed to post: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Who is in, who is on the bubble and who is out. Best scheduled for Tuesday, once
 * Monday night has settled the week.
 */
class PlayoffPictureAlert : PlayoffAlert() {
    override val name = "Playoff Picture"

    override fun build() = PlayoffPosts.picture(
        xml(YahooApiRequest.Settings),
        xml(YahooApiRequest.Standings)
    )

    override fun message(body: String) = Message.PlayoffPicture(body)
}

/** Each team's chances of making the field and getting a bye, from simulating the rest of the season. */
class PlayoffOddsAlert : PlayoffAlert() {
    override val name = "Playoff Odds"

    override fun build() = PlayoffPosts.odds(
        xml(YahooApiRequest.Settings),
        xml(YahooApiRequest.Standings),
        xml(YahooApiRequest.TeamsData)
    )

    override fun message(body: String) = Message.PlayoffOdds(body)
}
