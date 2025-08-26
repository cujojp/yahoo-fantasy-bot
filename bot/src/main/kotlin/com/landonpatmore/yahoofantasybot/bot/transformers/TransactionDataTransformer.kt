/*
 * MIT License
 *
 * Copyright (c) 2020 Landon Patmore
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
import com.landonpatmore.yahoofantasybot.bot.services.OpenAIService
import com.landonpatmore.yahoofantasybot.bot.utils.bold
import io.reactivex.rxjava3.core.Observable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

fun Observable<Pair<Long, Document>>.convertToTransactionMessage(openAIService: OpenAIService? = null): Observable<Message> =
    flatMap {
        Observable.fromIterable(it.second.select("transaction"))
            .map { transaction ->
                Pair(it.first, transaction)
            }
    }.filter {
        it.second.select("timestamp").text().toLong() >= it.first
    }.flatMap { pair ->
        val transactionType = pair.second.select("type").first().text()
        val baseMessage = when (transactionType) {
            "add" -> addMessage(pair.second, openAIService)
            "drop" -> dropMessage(pair.second, openAIService)
            "add/drop" -> addDropMessage(pair.second, openAIService)
            "trade" -> tradeMessage(pair.second, openAIService)
            "commish" -> commissionerMessage(openAIService)
            else -> Observable.just(Message.Unknown(""))
        }
        baseMessage
    }

private fun addMessage(event: Element, openAIService: OpenAIService?): Observable<Message> {
    val fantasyTeam = event.select("destination_team_name").text()
    val players = event.select("player")

    val playersAdded = StringBuilder()
    val playerDetailsList = mutableListOf<String>()

    for (player: Element in players) {
        val name = player.select("full").text()
        val nflTeam = player.select("editorial_team_abbr").text()
        val position = player.select("display_position").text()

        playersAdded.append("${name.bold()} ($nflTeam, $position), ")
        playerDetailsList.add("$name ($nflTeam, $position)")
    }

    val finalMessage = playersAdded.trimEnd().removeSuffix(",")
    val baseMessage = "${fantasyTeam.bold()}\\n" + "Added: $finalMessage"

    return if (openAIService != null) {
        val transactionDetails = "$fantasyTeam added ${playerDetailsList.joinToString(", ")}"
        openAIService.generateSchefterTweet("ADD", transactionDetails)
            .map { tweet ->
                Message.Transaction.Add(baseMessage, tweet)
            }
            .onErrorReturn {
                Message.Transaction.Add(baseMessage)
            }
            .toObservable()
            .cast(Message::class.java)
    } else {
        Observable.just(Message.Transaction.Add(baseMessage) as Message)
    }
}

private fun dropMessage(event: Element, openAIService: OpenAIService?): Observable<Message> {
    val fantasyTeam = event.select("source_team_name").text()
    val players = event.select("player")

    val playersDropped = StringBuilder()
    val playerDetailsList = mutableListOf<String>()

    for (player: Element in players) {
        val name = player.select("full").text()
        val nflTeam = player.select("editorial_team_abbr").text()
        val position = player.select("display_position").text()

        playersDropped.append("${name.bold()} ($nflTeam, $position), ")
        playerDetailsList.add("$name ($nflTeam, $position)")
    }

    val finalMessage = playersDropped.trimEnd().removeSuffix(",")
    val baseMessage = "${fantasyTeam.bold()}\\n" + "Dropped: $finalMessage"

    return if (openAIService != null) {
        val transactionDetails = "$fantasyTeam dropped ${playerDetailsList.joinToString(", ")}"
        openAIService.generateSchefterTweet("DROP", transactionDetails)
            .map { tweet ->
                Message.Transaction.Drop(baseMessage, tweet)
            }
            .onErrorReturn {
                Message.Transaction.Drop(baseMessage)
            }
            .toObservable()
            .cast(Message::class.java)
    } else {
        Observable.just(Message.Transaction.Drop(baseMessage) as Message)
    }
}

private fun addDropMessage(event: Element, openAIService: OpenAIService?): Observable<Message> {
    val fantasyTeam = event.select("source_team_name").text()
    val players = event.select("player")

    val playersAdded = StringBuilder()
    val playersDropped = StringBuilder()
    val addedPlayersList = mutableListOf<String>()
    val droppedPlayersList = mutableListOf<String>()

    var playersAddedCount = 0
    var playersDroppedCount = 0

    for (player: Element in players) {
        val name = player.select("full").text()
        val nflTeam = player.select("editorial_team_abbr").text()
        val position = player.select("display_position").text()

        val e = "${name.bold()} ($nflTeam, $position), "
        val playerDetails = "$name ($nflTeam, $position)"

        if (player.select("type").text() == "add") {
            playersAdded.append(e)
            addedPlayersList.add(playerDetails)
            playersAddedCount++
        } else {
            playersDropped.append(e)
            droppedPlayersList.add(playerDetails)
            playersDroppedCount++
        }
    }

    val finalMessageAdded = playersAdded.trimEnd().removeSuffix(",")
    val finalMessageDropped = playersDropped.trimEnd().removeSuffix(",")
    val baseMessage = "${fantasyTeam.bold()}\\n" +
            "Added: $finalMessageAdded\\n" +
            "Dropped: $finalMessageDropped"

    return if (openAIService != null) {
        val transactionDetails = "$fantasyTeam added ${addedPlayersList.joinToString(", ")} and dropped ${droppedPlayersList.joinToString(", ")}"
        openAIService.generateSchefterTweet("ADD/DROP", transactionDetails)
            .map { tweet ->
                Message.Transaction.AddDrop(baseMessage, tweet)
            }
            .onErrorReturn {
                Message.Transaction.AddDrop(baseMessage)
            }
            .toObservable()
            .cast(Message::class.java)
    } else {
        Observable.just(Message.Transaction.AddDrop(baseMessage) as Message)
    }
}

private fun tradeMessage(event: Element, openAIService: OpenAIService?): Observable<Message> {
    val trader = event.select("trader_team_name").text()
    val tradee = event.select("tradee_team_name").text()

    val players = event.select("player")

    val fromTraderTeam = StringBuilder()
    val fromTradeeTeam = StringBuilder()
    val traderPlayersList = mutableListOf<String>()
    val tradeePlayersList = mutableListOf<String>()

    for (player: Element in players) {
        val fantasyTeam = player.select("source_team_name").text()
        val name = player.select("full").text()
        val nflTeam = player.select("editorial_team_abbr").text()
        val position = player.select("display_position").text()

        val e = "${name.bold()} ($nflTeam, $position), "
        val playerDetails = "$name ($nflTeam, $position)"

        if (fantasyTeam == trader) {
            fromTraderTeam.append(e)
            traderPlayersList.add(playerDetails)
        } else {
            fromTradeeTeam.append(e)
            tradeePlayersList.add(playerDetails)
        }
    }

    val finalMessageFromTrader = fromTraderTeam.trimEnd().removeSuffix(",")
    val finalMessageFromTradee = fromTradeeTeam.trimEnd().removeSuffix(",")
    val baseMessage = "${trader.bold()} received: $finalMessageFromTradee\\n" +
            "${tradee.bold()} received: $finalMessageFromTrader"

    return if (openAIService != null) {
        val transactionDetails = "$trader traded ${traderPlayersList.joinToString(", ")} to $tradee for ${tradeePlayersList.joinToString(", ")}"
        openAIService.generateSchefterTweet("TRADE", transactionDetails)
            .map { tweet ->
                Message.Transaction.Trade(baseMessage, tweet)
            }
            .onErrorReturn {
                Message.Transaction.Trade(baseMessage)
            }
            .toObservable()
            .cast(Message::class.java)
    } else {
        Observable.just(Message.Transaction.Trade(baseMessage) as Message)
    }
}

private fun commissionerMessage(openAIService: OpenAIService?): Observable<Message> {
    val baseMessage = "A league setting has been modified.  You may want to check or ask them what they changed!".bold()
    
    return if (openAIService != null) {
        val transactionDetails = "Commissioner made changes to league settings"
        openAIService.generateSchefterTweet("COMMISH CHANGES", transactionDetails)
            .map { tweet ->
                Message.Transaction.Commish(baseMessage, tweet)
            }
            .onErrorReturn {
                Message.Transaction.Commish(baseMessage)
            }
            .toObservable()
            .cast(Message::class.java)
    } else {
        Observable.just(Message.Transaction.Commish(baseMessage) as Message)
    }
}
