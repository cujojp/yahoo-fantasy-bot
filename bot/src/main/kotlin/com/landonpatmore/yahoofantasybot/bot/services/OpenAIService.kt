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

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import com.landonpatmore.yahoofantasybot.shared.services.news.NewsBrief
import com.landonpatmore.yahoofantasybot.shared.services.news.PlayerNewsService
import com.landonpatmore.yahoofantasybot.shared.services.news.SchefterPrompt
import com.landonpatmore.yahoofantasybot.shared.services.news.TweetFactChecker
import com.mashape.unirest.http.Unirest
import io.reactivex.rxjava3.core.Single
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the short insider-style post that rides along with a transaction alert.
 *
 * The model is only ever a writer here, never a source. Facts are gathered first by
 * [PlayerNewsService], handed over as an explicit evidence block, and the finished post is
 * checked back against that evidence before it can be sent. Anything the model asserts
 * that we cannot trace to a source gets the post dropped and the alert goes out as the
 * plain roster move.
 */
class OpenAIService(
    private val apiKey: String,
    private val playerNewsService: PlayerNewsService? = null
) {

    companion object {
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"

        /**
         * Overridable so the model can be moved without a deploy. The old default was
         * `gpt-4`, whose training data predates this season by years; with grounding that
         * matters less, but a current model follows the evidence-only rules far better.
         */
        private val MODEL = System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() } ?: "gpt-4o"

        private const val MAX_TOKENS = 280

        /** Low, because this is a reporting task and not a creative one. */
        private const val TEMPERATURE = 0.4
    }

    fun generateSchefterTweet(transactionType: String, transactionDetails: String): Single<String> =
        generateSchefterTweetWithPlayers(transactionType, transactionDetails, emptyList())

    fun generateSchefterTweetWithPlayers(
        transactionType: String,
        transactionDetails: String,
        players: List<PlayerInfo>
    ): Single<String> = generatePost(transactionType, transactionDetails, players).map { it.text }

    /**
     * The post plus the attribution line for whatever sources backed it. Callers that want
     * to show "via ESPN, Sep 9" under the tweet use this; [generateSchefterTweetWithPlayers]
     * stays for the simpler callers.
     */
    fun generatePost(
        transactionType: String,
        transactionDetails: String,
        players: List<PlayerInfo>
    ): Single<GeneratedPost> = Single.fromCallable {
        val brief = playerNewsService?.brief(players) ?: NewsBrief(emptyList())
        val systemPrompt = SchefterPrompt.system(transactionType)
        val userPrompt = SchefterPrompt.user(transactionType, transactionDetails, brief)

        println("OpenAIService: generating $transactionType post with ${brief.facts.size} sourced fact(s)")
        println("--- USER PROMPT START ---\n$userPrompt\n--- USER PROMPT END ---")

        val tweet = callOpenAI(systemPrompt, userPrompt)

        val evidence = SchefterPrompt.evidence(transactionDetails, brief)
        val problems = TweetFactChecker.findUnsupportedClaims(tweet, evidence)
        if (problems.isNotEmpty()) {
            println("OpenAIService: dropping generated post, unsupported claims: ${problems.joinToString("; ")}")
            throw UnsupportedClaimException(problems)
        }

        GeneratedPost(tweet, brief.attribution())
    }

    private fun callOpenAI(systemPrompt: String, userPrompt: String): String {
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
            throw Exception("OpenAI API error: ${response.status} - ${response.body}")
        }

        return response.body.`object`
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}

/** A generated post and the sources that backed it, if any. */
data class GeneratedPost(val text: String, val attribution: String?) {
    /** The post as it should appear, with its attribution line when we have one. */
    fun withAttribution(): String = if (attribution == null) text else "$text\n_${attribution}_"
}

/** Thrown when the generated post asserts something the evidence does not support. */
class UnsupportedClaimException(val problems: List<String>) :
    Exception("Generated post made unsupported claims: ${problems.joinToString("; ")}")
