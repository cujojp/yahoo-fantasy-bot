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

package com.landonpatmore.yahoofantasybot.bot.services

import com.mashape.unirest.http.Unirest
import io.reactivex.rxjava3.core.Single
import org.json.JSONArray
import org.json.JSONObject
import com.landonpatmore.yahoofantasybot.shared.services.YahooNewsService
import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo

class OpenAIService(private val apiKey: String, private val yahooNewsService: YahooNewsService? = null) {
    
    companion object {
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4"
        private const val MAX_TOKENS = 280 // Twitter-like length
        private const val TEMPERATURE = 0.8
    }
    
    fun generateSchefterTweet(transactionType: String, transactionDetails: String): Single<String> {
        return generateSchefterTweetWithPlayers(transactionType, transactionDetails, emptyList())
    }
    
    fun generateSchefterTweetWithPlayers(
        transactionType: String, 
        transactionDetails: String, 
        players: List<PlayerInfo>
    ): Single<String> {
        return Single.fromCallable {
            // Get news context if available
            val newsContext = yahooNewsService?.generateNewsContext(players) ?: ""
            
            val systemPrompt = when (transactionType) {
                "COMMISH CHANGES" -> """You are Adam Schefter, the renowned NFL insider. Write a brief, punchy tweet about fantasy league administrative changes.
                    |Keep it under 280 characters. Use insider language and create urgency/excitement.
                    |Use only ONE emoji maximum, preferably 🚨 for breaking news or none at all. Make it sound like breaking news.
                    |Focus on how commissioner rule changes affect fantasy managers and league dynamics.
                    |Avoid mentioning bots or technology - focus on the league governance aspect.""".trimMargin()
                else -> """You are Adam Schefter, the renowned NFL insider. Write a brief, punchy tweet about a fantasy football transaction.
                    |Keep it under 280 characters. Use insider language and create urgency/excitement.
                    |Use only ONE emoji maximum, preferably 🚨 for breaking news or 🏈 for football context, or none at all. Make it sound like breaking news.
                    |Focus on the fantasy impact and player value. If recent news context is provided, incorporate relevant details.
                    |Transaction types: ADD (roster addition), DROP (player release), ADD/DROP (roster move), TRADE (player swap).""".trimMargin()
            }
            
            val userPrompt = buildString {
                append("Transaction Type: $transactionType\n")
                append("Details: $transactionDetails")
                if (newsContext.isNotEmpty()) {
                    append("\n$newsContext")
                }
            }
            
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
                put("max_tokens", MAX_TOKENS)
                put("temperature", TEMPERATURE)
            }
            
            val response = Unirest.post(OPENAI_API_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .body(requestBody)
                .asJson()
            
            if (response.status == 200) {
                response.body.`object`
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else {
                throw Exception("OpenAI API error: ${response.status} - ${response.body}")
            }
        }
    }
}
