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
                    
                    val latestTimeChecked = database.getLatestTimeChecked()
                    println("[Arbiter] Latest time checked: ${latestTimeChecked.time}")
                    
                    if (latestTimeChecked.time != -1L) {
                        val checkTime = if (latestTimeChecked.time == -1L) {
                            // we say -15 so that we can grab anything right before we got this bad value
                            System.currentTimeMillis() - 15
                        } else {
                            latestTimeChecked.time
                        } / 1000
                        
                        println("[Arbiter] Checking for transactions since: $checkTime")
                        
                        // Count transactions for debugging
                        val transactionCount = event.select("transaction").size
                        println("[Arbiter] Total transactions in response: $transactionCount")
                        
                        if (transactionCount > 0) {
                            // Store the current time before processing
                            val processingStartTime = System.currentTimeMillis()
                            
                            transactionsBridge.consumer.accept(Pair(checkTime, event))
                            
                            // Only update latest time if we actually had transactions to process
                            // This prevents losing transactions if processing fails
                            println("[Arbiter] Processing $transactionCount transactions...")
                            
                            // TODO: Ideally we should wait for confirmation that transactions were sent
                            // For now, we'll add a small delay to reduce race condition likelihood
                            Thread.sleep(2000) // 2 second delay
                            
                            database.saveLatestTimeChecked(processingStartTime)
                            println("[Arbiter] Updated latest check time to: $processingStartTime")
                        } else {
                            // No transactions, safe to update the check time
                            database.saveLatestTimeChecked(System.currentTimeMillis())
                            println("[Arbiter] No new transactions found, updated check time")
                        }
                    }
                    println("[Arbiter] Transaction check cycle completed")
                } catch (e: Exception) {
                    println("[Arbiter] ERROR during transaction check: ${e.message}")
                    e.printStackTrace()
                }
            }
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
        val transactions = transactionsBridge.eventStream
            .convertToTransactionMessage(openAIService)

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
