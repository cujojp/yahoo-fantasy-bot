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

package com.landonpatmore.yahoofantasybot.shared.services.news

import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal JSON-over-HTTP GET for the public news feeds. These are unauthenticated
 * endpoints, so there is nothing to sign and no client library worth pulling in.
 *
 * We deliberately use [HttpURLConnection] rather than the newer HTTP client because it
 * negotiates and transparently inflates gzip for us. The ESPN and Sleeper payloads are
 * several megabytes uncompressed, so that matters.
 */
internal object HttpJson {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "yahoo-fantasy-bot/1.0 (+https://github.com/cujojp/yahoo-fantasy-bot)"

    /**
     * Fetches [url] and parses it as a JSON object. Returns null on any failure: news is
     * an enhancement, and a feed being down must never take an alert with it.
     */
    fun getObject(url: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                println("HttpJson: $url returned ${connection.responseCode}")
                return null
            }

            // Stream straight into the parser so the raw body is never held as one big String.
            InputStreamReader(connection.inputStream, Charsets.UTF_8).use { reader ->
                JSONObject(JSONTokener(reader))
            }
        } catch (e: Exception) {
            println("HttpJson: $url failed: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            // The Sleeper player dump is large enough to matter on a small container.
            println("HttpJson: $url ran out of memory while parsing")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
