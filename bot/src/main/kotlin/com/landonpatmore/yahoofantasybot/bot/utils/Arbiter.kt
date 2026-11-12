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

package com.landonpatmore.yahoofantasybot.bot.utils

import com.landonpatmore.yahoofantasybot.bot.bridges.*
import com.landonpatmore.yahoofantasybot.bot.messaging.IMessagingService
import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import com.landonpatmore.yahoofantasybot.bot.services.OpenAIService
import com.landonpatmore.yahoofantasybot.bot.transformers.*
import com.landonpatmore.yahoofantasybot.bot.utils.models.Configuration
import com.landonpatmore.yahoofantasybot.bot.utils.models.YahooApiRequest
import com.landonpatmore.yahoofantasybot.shared.database.Db
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit

class Arbiter(
    private val dataRetriever: IDataRetriever,
    private val messageBridge: MessageBridge,
    private val alertsRunner: AlertsRunner,
    private val transactionsBridge: TransactionsBridge,
    private val scoreUpdateBridge: ScoreUpdateBridge,
    private val closeScoreUpdateBridge: CloseScoreUpdateBridge,
    private val standingsBridge: StandingsBridge,
    private val matchUpBridge: MatchUpBridge,
    private val configurationBridge: ConfigurationBridge,
    private val messagingServices: List<IMessagingService>,
    private val database: Db,
    private val openAIService: OpenAIService? = null
) {

    private fun setup() {
        setupTransactionsBridge()
        setupScoreUpdateBridge()
        setupCloseScoreUpdateBridge()
        setupMatchUpBridge()
        setupStandingsBridge()
        setupMessageBridge()
        sendInitialMessage()
        alertsRunner.start()
    }

    fun start() {
        setup()

        Observable.interval(0, 15, TimeUnit.SECONDS)
            .subscribe {
                println("[Arbiter] Running transaction check cycle...")
                val alerts = database.getAlerts()
                println("[Arbiter] Loaded ${alerts.size} alerts from database:")
                alerts.forEachIndexed { index, alert ->
                    println("[Arbiter] Alert $index: type=${alert.type}, hour=${alert.hour}, minute=${alert.minute}, " +
                            "startMonth=${alert.startMonth}, endMonth=${alert.endMonth}, dayOfWeek=${alert.dayOfWeek}, uuid=${alert.uuid}")
                }
                configurationBridge.consumer.accept(Configuration.Alerts(alerts))
                try {
                    println("[Arbiter] Fetching transactions from Yahoo API...")
                    val event = dataRetriever.yahooApiRequest(YahooApiRequest.Transactions)
                    println("[Arbiter] Received response from Yahoo API")
                    
                    val transactions = TransactionSelector.parse(event)
                    if (transactions == null) {
                        println("[Arbiter] Response is not a transactions document, skipping this cycle")
                    } else {
                        println("[Arbiter] Total transactions in response: ${transactions.size}")
                        handleTransactions(transactions)
                    }
                    println("[Arbiter] Transaction check cycle completed")
                } catch (e: Throwable) {
                    // Throwable, not Exception. An Error such as OutOfMemoryError used to get
                    // past this, and RxJava then ends the interval for good, so transaction
                    // checks stopped while the process and scheduled alerts carried on.
                    println("[Arbiter] ERROR during transaction check: $e")
                    e.printStackTrace()
                }
            }
    }

    private fun handleTransactions(transactions: List<YahooTransaction>) {
        val now = System.currentTimeMillis()
        val legacyCutoff = database.getLatestTimeChecked().time.takeIf { it > 0 }?.div(1000)
        val selection = TransactionSelector.select(
            transactions,
            database.getSeenTransactionKeys(),
            legacyCutoff,
            now / 1000
        )

        if (selection.isFirstRun) {
            println("[Arbiter] First run with transaction keys: recording ${selection.toSkip.size} existing transactions without posting")
        } else if (selection.toSkip.isNotEmpty()) {
            println("[Arbiter] Recording ${selection.toSkip.size} unseen transactions older than the lookback without posting: " +
                    selection.toSkip.joinToString { "${it.key}@${it.timestamp}" })
        }
        if (selection.overCatchUpLimit.isNotEmpty()) {
            println("[Arbiter] ${selection.overCatchUpLimit.size} catch-up transactions over the limit of " +
                    "${TransactionSelector.CATCH_UP_LIMIT}, recorded without posting: " +
                    selection.overCatchUpLimit.joinToString { "${it.key}@${it.timestamp}" })
        }

        // Recorded before the hand-off: posting is asynchronous and can take several
        // seconds (news lookup, OpenAI), and the next cycle must not pick these up again.
        database.markTransactionsSeen(selection.keysToMarkSeen())

        if (selection.toPost.isNotEmpty()) {
            println("[Arbiter] Posting ${selection.toPost.size} new transactions: " +
                    selection.toPost.joinToString { "${it.key}@${it.timestamp}" })
            transactionsBridge.consumer.accept(selection.toPost.map { it.element })
        }

        // No longer decides what is new; kept for the health check's "last activity".
        database.saveLatestTimeChecked(now)
    }

    private fun sendInitialMessage() {
        if (!database.wasStartupMessageReceived()) {
            messageBridge.consumer.accept(
                Message.Generic(
                    """
                        |Thanks for using me!  I will notify you about things happening in your league in real time!
                        |Star/fork me on Github: https://github.com/cujojp/yahoo-fantasy-bot
                        |Having issues?: https://github.com/cujojp/yahoo-fantasy-bot/issues
                    """.trimMargin()
                )
            )
            database.startupMessageReceived()
        }
    }

    private fun setupTransactionsBridge() {
        // Each transaction already handles its own failure in convertToTransactionMessage.
        // This is the backstop: an error that reaches here would otherwise end the
        // subscription, and every later transaction would be dropped without a word while
        // the check loop kept moving the latest time forward. retry() resubscribes to the
        // relay so the next batch still gets through.
        val transactions = transactionsBridge.eventStream
            .convertToTransactionMessage(openAIService)
            .doOnError { error ->
                println("[Arbiter] ERROR in transaction stream, resubscribing: $error")
                error.printStackTrace()
            }
            .retry()

        transactions.subscribe(messageBridge.consumer)
    }

    private fun setupScoreUpdateBridge() {
        val transactions = scoreUpdateBridge.eventStream
            .convertToMatchUpObject()
            .convertToScoreUpdateMessage()

        transactions.subscribe(messageBridge.consumer)
    }

    private fun setupCloseScoreUpdateBridge() {
        val transactions = closeScoreUpdateBridge.eventStream
            .convertToMatchUpObject()
            .convertToScoreUpdateMessage(true)

        transactions.subscribe(messageBridge.consumer)
    }

    private fun setupMatchUpBridge() {
        val transactions = matchUpBridge.eventStream
            .doOnNext { println("[Arbiter] MatchUpBridge received document") }
            .convertToMatchUpObject()
            .doOnNext { println("[Arbiter] Converted to matchup object: ${it.first.name} vs ${it.second.name}") }
            .doOnError { error ->
                println("[Arbiter] Error in convertToMatchUpObject: ${error.message}")
                error.printStackTrace()
            }
            .onErrorResumeNext { error ->
                println("[Arbiter] Resuming after error in convertToMatchUpObject")
                Observable.empty()
            }
            .convertToMatchUpMessage()
            .doOnNext { println("[Arbiter] Converted to message: ${it.message}") }
            .doOnError { error ->
                println("[Arbiter] Error in convertToMatchUpMessage: ${error.message}")
                error.printStackTrace()
            }

        transactions.subscribe(
            { message -> 
                println("[Arbiter] Sending matchup message to messageBridge")
                messageBridge.consumer.accept(message)
            },
            { error ->
                println("[Arbiter] Error in matchup bridge subscription: ${error.message}")
                error.printStackTrace()
            },
            {
                println("[Arbiter] MatchUp bridge stream completed")
            }
        )
    }

    private fun setupStandingsBridge() {
        val standings = standingsBridge.eventStream
            .convertToStandingsMessage()

        standings.subscribe(messageBridge.consumer)
    }

    private fun setupMessageBridge() {
        val messages = messageBridge.eventStream
            .convertToMessageInfo()

        messagingServices.forEach {
            messages.subscribe(it)
        }
    }

//    private fun configureMessagingServices(services: List<MessagingService>) {
//        messagingDisposable.clear()
//
//        services.map {
//            if (validateMessagingService(it.service)) {
//                when (it.service) {
//                    MessagingService.DISCORD -> Discord(it.url)
//                    MessagingService.SLACK -> Slack(it.url)
//                    MessagingService.GROUP_ME -> GroupMe(it.url)
//                    else -> null
//                }
//            } else {
//                null
//            }
//        }.forEach {
//            it?.let {
//                messagingDisposable.add(messages.subscribe(it))
//            }
//        }
//    }
//
//    private fun validateMessagingService(service: Int): Boolean {
//        return when (service) {
//            MessagingService.DISCORD, MessagingService.SLACK, MessagingService.GROUP_ME -> true
//            else -> false
//        }
//    }
}
