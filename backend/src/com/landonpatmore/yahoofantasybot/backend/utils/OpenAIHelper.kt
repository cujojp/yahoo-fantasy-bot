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

import com.mashape.unirest.http.Unirest
import org.json.JSONArray
import org.json.JSONObject
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo

object OpenAIHelper {
    private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4"
    private const val MAX_TOKENS = 280
    private const val TEMPERATURE = 0.8
    
    fun generateTestMessageSchefterTweet(originalMessage: String): String? {
        val apiKey = EnvVariable.Str.OpenAIApiKey.variable
        println("OpenAI API Key check: ${if (apiKey.isNotEmpty()) "Present (length: ${apiKey.length})" else "Not set"}")
        
        if (apiKey.isEmpty()) {
            println("OpenAI API key not configured - skipping Schefter tweet generation")
            return null
        }
        
        return try {
            // Check if the test message contains transaction-like content
            val isTransactionTest = originalMessage.contains("added", ignoreCase = true) || 
                                  originalMessage.contains("dropped", ignoreCase = true) || 
                                  originalMessage.contains("traded", ignoreCase = true)
            
            val systemPrompt = if (isTransactionTest) {
                """You are Adam Schefter, the renowned NFL insider. Write a brief, punchy tweet about this fantasy football transaction.
                    |Keep it under 280 characters. Use insider language and create urgency/excitement.
                    |Use only ONE emoji maximum, preferably 🚨 for breaking news or 🏈 for football context, or none at all. Make it sound like breaking news.
                    |Focus on the fantasy impact and player value. This is a test of the transaction alert system.""".trimMargin()
            } else {
                """You are Adam Schefter, the renowned NFL insider. Write a brief, punchy tweet about a fantasy football bot test message.
                    |Keep it under 280 characters. Use insider language and create urgency/excitement.
                    |Use only ONE emoji maximum, preferably 🚨 for breaking news or 🤖 for bot context, or none at all. Make it sound like breaking news about the bot being operational.
                    |Focus on the technology and reliability aspect.""".trimMargin()
            }
            
            val userPrompt = if (isTransactionTest) {
                "Fantasy Transaction: $originalMessage"
            } else {
                "Test Message: $originalMessage"
            }
            
            // For transaction tests, try to add some context
            val enhancedUserPrompt = if (isTransactionTest) {
                buildString {
                    append(userPrompt)
                    // Extract player names for potential context
                    val playerNames = extractPlayerNamesFromMessage(originalMessage)
                    if (playerNames.isNotEmpty()) {
                        append("\nPlayers involved: ${playerNames.joinToString(", ")}")
                    }
                }
            } else {
                userPrompt
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
                        put("content", enhancedUserPrompt)
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
            
            println("OpenAI API Response Status: ${response.status}")
            
            if (response.status == 200) {
                val generatedTweet = response.body.`object`
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                println("Generated Schefter tweet: $generatedTweet")
                generatedTweet
            } else {
                println("OpenAI API error response: ${response.body}")
                null
            }
        } catch (e: Exception) {
            println("OpenAI error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Extracts player names from a transaction message for enhanced context
     */
    private fun extractPlayerNamesFromMessage(message: String): List<String> {
        val playerNames = mutableListOf<String>()
        
        // Simple regex patterns to extract names from common formats
        // "Player Name (TEAM, POS)" format
        val playerPattern = Regex("""([A-Z][a-z]+ [A-Z][a-z]+(?:\s[A-Z][a-z]+)*)\s*\([A-Z]{2,4},\s*[A-Z]+\)""")
        val matches = playerPattern.findAll(message)
        
        matches.forEach { match ->
            val playerName = match.groupValues[1].trim()
            if (playerName.isNotEmpty() && !playerNames.contains(playerName)) {
                playerNames.add(playerName)
            }
        }
        
        // Fallback: look for capitalized names (less reliable)
        if (playerNames.isEmpty()) {
            val namePattern = Regex("""([A-Z][a-z]+\s+[A-Z][a-z]+)""")
            val nameMatches = namePattern.findAll(message)
            nameMatches.take(3).forEach { match ->
                val name = match.groupValues[1]
                // Filter out common non-player words
                if (!name.matches(Regex("(added|dropped|traded|received|Team|Name|League).*", RegexOption.IGNORE_CASE))) {
                    playerNames.add(name)
                }
            }
        }
        
        return playerNames.take(3) // Limit to 3 players for context
    }
}
