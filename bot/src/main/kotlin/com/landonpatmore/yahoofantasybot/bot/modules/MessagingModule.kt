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
import com.landonpatmore.yahoofantasybot.bot.utils.IDataRetriever
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.database.models.MessageHistory
import com.landonpatmore.yahoofantasybot.shared.services.news.PlayerNewsService
import org.koin.dsl.module

val messagingModule = module {
    single { Discord(EnvVariable.Str.DiscordWebhookUrl.variable) }
    single { Slack(EnvVariable.Str.SlackWebhookUrl.variable) }
    single { GroupMe(EnvVariable.Str.GroupMeBotId.variable) }
    single<OpenAIService?> { 
        val apiKey = EnvVariable.Str.OpenAIApiKey.variable
        if (apiKey.isNotEmpty()) {
            // Built through DataRetriever so Yahoo lookups share its OAuth client, with its
            // timeouts, and sign with its current token. This used to build a separate
            // client around the token in the database at startup, which expired an hour
            // later and was never refreshed.
            val yahooNewsService = get<IDataRetriever>().createYahooNewsService()

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