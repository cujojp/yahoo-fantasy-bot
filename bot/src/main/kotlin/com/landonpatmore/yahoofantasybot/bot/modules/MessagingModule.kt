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

package com.landonpatmore.yahoofantasybot.bot.modules

import com.landonpatmore.yahoofantasybot.bot.messaging.Discord
import com.landonpatmore.yahoofantasybot.bot.messaging.GroupMe
import com.landonpatmore.yahoofantasybot.bot.messaging.IMessagingService
import com.landonpatmore.yahoofantasybot.bot.messaging.Slack
import com.landonpatmore.yahoofantasybot.bot.messaging.EnhancedMessagingService
import com.landonpatmore.yahoofantasybot.bot.services.OpenAIService
import com.landonpatmore.yahoofantasybot.bot.utils.DataRetriever
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory
import com.landonpatmore.yahoofantasybot.shared.services.YahooNewsService
import com.landonpatmore.yahoofantasybot.shared.services.news.PlayerNewsService
import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.oauth.OAuth20Service
import org.koin.dsl.module

val messagingModule = module {
    single { Discord(EnvVariable.Str.DiscordWebhookUrl.variable) }
    single { Slack(EnvVariable.Str.SlackWebhookUrl.variable) }
    single { GroupMe(EnvVariable.Str.GroupMeBotId.variable) }
    single<OpenAIService?> { 
        val apiKey = EnvVariable.Str.OpenAIApiKey.variable
        if (apiKey.isNotEmpty()) {
            // Create YahooNewsService directly here instead of through DataRetriever
            val db = get<Db>()
            val yahooNewsService = try {
                val tokenData = db.getLatestTokenData()
                if (tokenData != null) {
                    val oauthService = com.github.scribejava.core.builder.ServiceBuilder(
                        EnvVariable.Str.YahooClientId.variable
                    )
                        .apiSecret(EnvVariable.Str.YahooClientSecret.variable)
                        .callback("oob")
                        .build(YahooApi20.instance())
                    
                    YahooNewsService(oauthService, tokenData.second)
                } else {
                    println("MessagingModule: No OAuth token available for YahooNewsService")
                    null
                }
            } catch (e: Exception) {
                println("MessagingModule: Could not create YahooNewsService: ${e.message}")
                null
            }
            
            // PlayerNewsService is built even when Yahoo is unavailable: ESPN and Sleeper
            // need no auth, so a Yahoo 403 costs us one source rather than all of them.
            OpenAIService(apiKey, PlayerNewsService(yahooNewsService))
        } else {
            null
        }
    }
    single {
        val database = get<Db>()
        listOf<IMessagingService>(
            EnhancedMessagingService(get<Discord>(), MessageHistory.SERVICE_DISCORD, database),
            EnhancedMessagingService(get<Slack>(), MessageHistory.SERVICE_SLACK, database),
            EnhancedMessagingService(get<GroupMe>(), MessageHistory.SERVICE_GROUPME, database)
        )
    }
}