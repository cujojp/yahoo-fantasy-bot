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

package com.landonpatmore.yahoofantasybot.bot.transformers

import com.jakewharton.rxrelay3.PublishRelay
import com.landonpatmore.yahoofantasybot.bot.messaging.Message
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransactionDataTransformerKtTest {

    private fun transactions(vararg bodies: String): Document = Jsoup.parse(
        "<fantasy_content><league><transactions>${bodies.joinToString("")}</transactions></league></fantasy_content>",
        "",
        Parser.xmlParser()
    )

    private fun add(player: String, timestamp: Long = 200) = """
        <transaction>
            <type>add</type>
            <timestamp>$timestamp</timestamp>
            <players>
                <player>
                    <player_key>461.p.1</player_key>
                    <player_id>1</player_id>
                    <name><full>$player</full></name>
                    <editorial_team_abbr>NO</editorial_team_abbr>
                    <display_position>RB</display_position>
                    <transaction_data>
                        <type>add</type>
                        <destination_team_name>Danimals</destination_team_name>
                    </transaction_data>
                </player>
            </players>
        </transaction>
    """

    /** No type, so building its message throws. */
    private val malformed = "<transaction><timestamp>200</timestamp></transaction>"

    /** The Arbiter hands the transformer only the transactions it has chosen to post. */
    private fun batch(vararg bodies: String): List<Element> =
        transactions(*bodies).getElementsByTag("transaction").toList()

    @Test
    fun `a transaction that fails to build is skipped and the rest of the batch still posts`() {
        val relay = PublishRelay.create<List<Element>>()
        val observer = relay.convertToTransactionMessage(null).test()

        relay.accept(batch(malformed, add("Alvin Kamara")))

        observer.assertNoErrors()
        assertEquals(1, observer.values().size)
        val message = observer.values().single()
        assertTrue(message is Message.Transaction.Add)
        assertTrue(message.message.contains("Alvin Kamara"))
    }

    @Test
    fun `the stream keeps posting batches after one transaction fails`() {
        val relay = PublishRelay.create<List<Element>>()
        val observer = relay.convertToTransactionMessage(null).test()

        relay.accept(batch(malformed))
        relay.accept(batch(add("Jaxson Dart")))

        observer.assertNoErrors()
        observer.assertNotComplete()
        assertEquals(1, observer.values().size, "the batch after the failure must still go out")
        assertTrue(observer.values().single().message.contains("Jaxson Dart"))
    }
}
