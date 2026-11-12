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

package com.landonpatmore.yahoofantasybot.shared.services

import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.httpclient.HttpClient
import com.github.scribejava.core.httpclient.multipart.MultipartPayload
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthAsyncRequestCallback
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.model.Verb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.Future

class YahooNewsServiceTest {

    /**
     * Answers Yahoo requests from memory and records the Authorization header each one was
     * signed with, so the tests can see which token actually went out.
     */
    private class RecordingYahoo : HttpClient {
        val authorizations = mutableListOf<String?>()

        override fun execute(
            userAgent: String?,
            headers: MutableMap<String, String>?,
            httpVerb: Verb?,
            completeUrl: String,
            bodyContents: ByteArray?
        ): Response {
            authorizations.add(headers?.get("Authorization"))
            val body = when {
                completeUrl.endsWith("/game/nfl") -> "<fantasy_content><game><game_key>461</game_key></game></fantasy_content>"
                else -> "<fantasy_content><player><status>Q</status><status_full>Questionable</status_full>" +
                        "<injury_note>Knee</injury_note></player></fantasy_content>"
            }
            return Response(200, "OK", emptyMap(), body)
        }

        override fun execute(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?,
            completeUrl: String, bodyContents: String?
        ): Response = execute(userAgent, headers, httpVerb, completeUrl, bodyContents?.toByteArray())

        override fun execute(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?,
            completeUrl: String, bodyContents: MultipartPayload?
        ): Response = execute(userAgent, headers, httpVerb, completeUrl, null as ByteArray?)

        override fun execute(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?,
            completeUrl: String, bodyContents: File?
        ): Response = execute(userAgent, headers, httpVerb, completeUrl, null as ByteArray?)

        override fun <T : Any?> executeAsync(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?, completeUrl: String?,
            bodyContents: ByteArray?, callback: OAuthAsyncRequestCallback<T>?, converter: OAuthRequest.ResponseConverter<T>?
        ): Future<T> = throw UnsupportedOperationException()

        override fun <T : Any?> executeAsync(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?, completeUrl: String?,
            bodyContents: MultipartPayload?, callback: OAuthAsyncRequestCallback<T>?, converter: OAuthRequest.ResponseConverter<T>?
        ): Future<T> = throw UnsupportedOperationException()

        override fun <T : Any?> executeAsync(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?, completeUrl: String?,
            bodyContents: String?, callback: OAuthAsyncRequestCallback<T>?, converter: OAuthRequest.ResponseConverter<T>?
        ): Future<T> = throw UnsupportedOperationException()

        override fun <T : Any?> executeAsync(
            userAgent: String?, headers: MutableMap<String, String>?, httpVerb: Verb?, completeUrl: String?,
            bodyContents: File?, callback: OAuthAsyncRequestCallback<T>?, converter: OAuthRequest.ResponseConverter<T>?
        ): Future<T> = throw UnsupportedOperationException()

        override fun close() {}
    }

    private fun oauthService(http: HttpClient) =
        ServiceBuilder("client-id").apiSecret("secret").httpClient(http).build(YahooApi20.instance())

    /** Distinct names matter: playerFact caches by name and team. */
    private fun player(id: String, name: String = "Alvin Kamara") = PlayerInfo(name, "NO", "RB", playerId = id)

    @Test
    fun `signs every request with the token current at the time, not the one it was built with`() {
        val yahoo = RecordingYahoo()
        var current = OAuth2AccessToken("token-at-startup")
        val service = YahooNewsService(oauthService(yahoo)) { current }

        service.playerFact(player("1"))
        current = OAuth2AccessToken("token-after-refresh")
        service.playerFact(player("2", "Chris Olave"))

        assertTrue(yahoo.authorizations.first()!!.contains("token-at-startup"))
        assertTrue(
            yahoo.authorizations.last()!!.contains("token-after-refresh"),
            "an hour in, the startup token has expired and Yahoo answers 401"
        )
    }

    @Test
    fun `still reads Yahoo's injury designation through the provider`() {
        val service = YahooNewsService(oauthService(RecordingYahoo())) { OAuth2AccessToken("token") }

        val fact = service.playerFact(player("1"))

        assertEquals("Listed Questionable - Knee", fact?.text)
    }

    @Test
    fun `with no token it returns nothing and makes no request`() {
        val yahoo = RecordingYahoo()
        val service = YahooNewsService(oauthService(yahoo)) { null }

        assertNull(service.playerFact(player("1")))
        assertTrue(yahoo.authorizations.isEmpty())
    }

    @Test
    fun `Yahoo clients time out instead of waiting forever`() {
        val config = YahooOAuth.httpClientConfig()

        assertEquals(YahooOAuth.CONNECT_TIMEOUT_MS, config.connectTimeout)
        assertEquals(YahooOAuth.READ_TIMEOUT_MS, config.readTimeout)
    }
}
