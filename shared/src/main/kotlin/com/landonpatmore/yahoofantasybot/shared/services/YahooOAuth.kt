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

import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig

/**
 * HTTP settings for every OAuth client that reads data from Yahoo.
 *
 * scribejava's default client sets no timeouts, which leaves HttpURLConnection waiting
 * forever. The bot runs its whole transaction loop on one thread, so a single Yahoo
 * request that never answered stopped transactions for good: no exception, no log line,
 * and the process and scheduled alerts still up, so nothing looked wrong.
 */
object YahooOAuth {
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 30_000

    fun httpClientConfig(): JDKHttpClientConfig = JDKHttpClientConfig()
        .withConnectTimeout(CONNECT_TIMEOUT_MS)
        .withReadTimeout(READ_TIMEOUT_MS)
}
