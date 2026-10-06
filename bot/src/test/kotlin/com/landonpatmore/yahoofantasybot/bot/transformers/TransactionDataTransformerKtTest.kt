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

    /** New enough to pass the time filter, but with no type, so building it throws. */
    private val malformed = "<transaction><timestamp>200</timestamp></transaction>"

    @Test
    fun `a transaction that fails to build is skipped and the rest of the batch still posts`() {
        val relay = PublishRelay.create<Pair<Long, Document>>()
        val observer = relay.convertToTransactionMessage(null).test()

        relay.accept(Pair(100L, transactions(malformed, add("Alvin Kamara"))))

        observer.assertNoErrors()
        assertEquals(1, observer.values().size)
        val message = observer.values().single()
        assertTrue(message is Message.Transaction.Add)
        assertTrue(message.message.contains("Alvin Kamara"))
    }

    @Test
    fun `a catch-up posts only the newest transactions, oldest of those first`() {
        val relay = PublishRelay.create<Pair<Long, Document>>()
        val observer = relay.convertToTransactionMessage(null, catchUpLimit = 5).test()

        // Newest first, the way Yahoo lists them: eight new since the stale check time,
        // and one from before it that was already posted.
        val backlog = (8 downTo 1).map { add("Player $it", timestamp = 1000L + it) } + add("Old Player", timestamp = 50)
        relay.accept(Pair(100L, transactions(*backlog.toTypedArray())))

        observer.assertNoErrors()
        val posted = observer.values().map { Regex("Player \\d").find(it.message)!!.value }
        assertEquals(listOf("Player 4", "Player 5", "Player 6", "Player 7", "Player 8"), posted)
    }

    @Test
    fun `a normal check under the limit posts everything new`() {
        val relay = PublishRelay.create<Pair<Long, Document>>()
        val observer = relay.convertToTransactionMessage(null, catchUpLimit = 5).test()

        relay.accept(Pair(100L, transactions(add("Jaxson Dart", 300), add("Alvin Kamara", 200), add("Old Player", 50))))

        observer.assertNoErrors()
        assertEquals(2, observer.values().size)
        assertTrue(observer.values().first().message.contains("Alvin Kamara"))
        assertTrue(observer.values().last().message.contains("Jaxson Dart"))
    }

    @Test
    fun `the stream keeps posting batches after one transaction fails`() {
        val relay = PublishRelay.create<Pair<Long, Document>>()
        val observer = relay.convertToTransactionMessage(null).test()

        relay.accept(Pair(100L, transactions(malformed)))
        relay.accept(Pair(100L, transactions(add("Jaxson Dart"))))

        observer.assertNoErrors()
        observer.assertNotComplete()
        assertEquals(1, observer.values().size, "the batch after the failure must still go out")
        assertTrue(observer.values().single().message.contains("Jaxson Dart"))
    }
}
