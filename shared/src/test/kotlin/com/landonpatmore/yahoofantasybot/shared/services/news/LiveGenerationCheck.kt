package com.landonpatmore.yahoofantasybot.shared.services.news

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generates a post for each sample transaction against the live feeds and the live model,
 * then runs the fact checker over the result.
 *
 * Opt-in: it needs the network and an OpenAI key, and it costs a fraction of a cent per
 * run. Use it when changing the prompt, which is exactly the case where reading the real
 * output beats reasoning about it.
 *
 * Gated on its own variable, not on OPENAI_API_KEY, so a key sitting in the shell does
 * not quietly make `gradle test` cost money.
 *
 *     OPENAI_LIVE_CHECK=1 OPENAI_API_KEY=sk-... ./gradlew :shared:test --tests '*LiveGenerationCheck*' -i
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_LIVE_CHECK", matches = ".+")
class LiveGenerationCheck {

    private val transactions = listOf(
        Triple(
            "Dick Chubb added Eagles (Phi, DEF) and dropped Jakobi Meyers (Jax, WR)",
            listOf(PlayerInfo("Eagles", "Phi", "DEF"), PlayerInfo("Jakobi Meyers", "Jax", "WR")),
            "ADD/DROP"
        ),
        Triple(
            "My Nix in a Box added Jaguars (Jax, DEF) and dropped T.J. Hockenson (Min, TE)",
            listOf(PlayerInfo("Jaguars", "Jax", "DEF"), PlayerInfo("T.J. Hockenson", "Min", "TE")),
            "ADD/DROP"
        ),
        Triple(
            "My Nix in a Box added Jaxson Dart (NYG, QB) and dropped Alvin Kamara (NO, RB)",
            listOf(PlayerInfo("Jaxson Dart", "NYG", "QB"), PlayerInfo("Alvin Kamara", "NO", "RB")),
            "ADD/DROP"
        ),
        Triple(
            "Samuel Lamar Jackson added Kayshon Boutte (Hou, WR) and dropped Jahan Dotson (Atl, WR)",
            listOf(PlayerInfo("Kayshon Boutte", "Hou", "WR"), PlayerInfo("Jahan Dotson", "Atl", "WR")),
            "ADD/DROP"
        )
    )

    @Test
    fun `generates and checks a post for each sample transaction`() {
        val news = PlayerNewsService()

        transactions.forEach { (detail, players, type) ->
            val brief = news.brief(players)
            val tweet = complete(
                SchefterPrompt.system(type),
                SchefterPrompt.user(type, detail, brief)
            )
            val problems = TweetFactChecker.findUnsupportedClaims(
                tweet,
                SchefterPrompt.evidence(detail, brief)
            )

            println("=========================================")
            println("FACTS GIVEN (${brief.facts.size}):")
            println(brief.toFactsBlock())
            println("POST: $tweet")
            println("ATTRIBUTION: ${brief.attribution()}")
            println("CHECK: ${if (problems.isEmpty()) "clean" else "DROPPED -> $problems"}")
        }
    }

    private fun complete(system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() } ?: "gpt-4o")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
            put("max_tokens", 280)
            put("temperature", 0.4)
        }

        val connection = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${System.getenv("OPENAI_API_KEY")}")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val stream = if (connection.responseCode == 200) connection.inputStream else connection.errorStream
        val response = InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
        check(connection.responseCode == 200) { "OpenAI ${connection.responseCode}: $response" }

        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }
}
