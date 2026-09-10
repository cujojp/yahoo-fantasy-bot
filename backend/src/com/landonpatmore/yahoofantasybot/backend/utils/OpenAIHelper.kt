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

package com.landonpatmore.yahoofantasybot.backend.utils

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import com.landonpatmore.yahoofantasybot.shared.services.news.NewsBrief
import com.landonpatmore.yahoofantasybot.shared.services.news.PlayerNewsService
import com.landonpatmore.yahoofantasybot.shared.services.news.PostStyle
import com.landonpatmore.yahoofantasybot.shared.services.news.SchefterPrompt
import com.landonpatmore.yahoofantasybot.shared.services.news.TweetFactChecker
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import com.mashape.unirest.http.Unirest
import org.json.JSONArray
import org.json.JSONObject

/**
 * The manual test-message path from the web UI.
 *
 * This deliberately builds its prompt and runs its fact check through the same shared
 * [SchefterPrompt] and [TweetFactChecker] the bot uses. Previously the two paths had
 * different rules, and the stricter set lived here, on the path that never posts to
 * Discord. Testing a message now exercises what production will actually do.
 */
object OpenAIHelper {
    private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    private val MODEL = System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() } ?: "gpt-4o"
    private const val MAX_TOKENS = 280
    private const val TEMPERATURE = 0.4

    fun generateTestMessageSchefterTweet(
        originalMessage: String,
        playerNewsService: PlayerNewsService? = null
    ): String? {
        val apiKey = EnvVariable.Str.OpenAIApiKey.variable
        if (apiKey.isEmpty()) {
            println("OpenAIHelper: OpenAI API key not configured, skipping tweet generation")
            return null
        }

        return try {
            val players = extractPlayersFromMessage(originalMessage)
            val isTransactionTest = players.isNotEmpty() && looksLikeTransaction(originalMessage)

            // A bot smoke test is not a transaction and has no facts behind it, so it gets
            // a fixed line rather than an invitation to invent breaking news.
            if (!isTransactionTest) {
                println("OpenAIHelper: message is not a transaction, skipping tweet generation")
                return null
            }

            val brief = playerNewsService?.brief(players) ?: NewsBrief(emptyList())
            val systemPrompt = SchefterPrompt.system("TEST")
            val userPrompt = SchefterPrompt.user("TEST", originalMessage, brief)

            println("OpenAIHelper: generating test post with ${brief.facts.size} sourced fact(s)")

            val tweet = callOpenAI(apiKey, systemPrompt, userPrompt) ?: return null

            val problems = TweetFactChecker.findUnsupportedClaims(
                tweet,
                SchefterPrompt.evidence(originalMessage, brief)
            )
            if (problems.isNotEmpty()) {
                println("OpenAIHelper: dropping test post, unsupported claims: ${problems.joinToString("; ")}")
                return null
            }

            val styled = PostStyle.limitToOneEmoji(tweet)
            val attribution = brief.attribution()
            if (attribution == null) styled else "$styled\n_${attribution}_"
        } catch (e: Exception) {
            println("OpenAIHelper: error: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun looksLikeTransaction(message: String): Boolean =
        listOf("added", "dropped", "traded").any { message.contains(it, ignoreCase = true) }

    private fun callOpenAI(apiKey: String, systemPrompt: String, userPrompt: String): String? {
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

        if (response.status != 200) {
            println("OpenAIHelper: OpenAI API error ${response.status}: ${response.body}")
            return null
        }

        return response.body.`object`
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    /**
     * Pulls players out of a free-text test message so the news lookup has something to
     * work with. Only the `Name (TEAM, POS)` form is accepted. The old loose fallback
     * matched any two capitalised words, which fed fantasy team names to the news lookup
     * as if they were players.
     */
    private fun extractPlayersFromMessage(message: String): List<PlayerInfo> {
        val playerPattern =
            Regex("""([A-Z][a-z']+ [A-Z][a-z']+(?:\s[A-Z][a-z']+)*)\s*\(([A-Za-z]{2,4}),\s*([A-Z]+)\)""")

        return playerPattern.findAll(message)
            .map { match ->
                PlayerInfo(
                    name = match.groupValues[1].trim(),
                    nflTeam = match.groupValues[2].trim(),
                    position = match.groupValues[3].trim()
                )
            }
            .distinctBy { it.name }
            .take(4)
            .toList()
    }
}
