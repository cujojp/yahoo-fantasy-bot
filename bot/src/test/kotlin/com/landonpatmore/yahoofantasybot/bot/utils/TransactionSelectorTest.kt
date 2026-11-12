package com.landonpatmore.yahoofantasybot.bot.utils

import com.landonpatmore.yahoofantasybot.bot.utils.TransactionSelector.INITIALIZED_MARKER
import com.landonpatmore.yahoofantasybot.bot.utils.TransactionSelector.LOOKBACK_SECONDS
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Moves used to be picked out by timestamp against the time of the previous check.
 * On Nov 11, 2026, a move stamped 05:25:52 and 8 waiver claims stamped 09:32:37 each
 * appeared in Yahoo's feed one second after that cutoff had passed them, so none
 * were posted.
 */
class TransactionSelectorTest {

    private val now = 1_794_390_000L
    private val initialized = setOf(INITIALIZED_MARKER)

    @Test
    fun `a move that shows up after its timestamp is still posted`() {
        // Stamped well before "now", as if Yahoo published it late.
        val late = parseAll(tx("470.l.1.tr.300", now - 60))

        val selection = TransactionSelector.select(late, initialized, null, now)

        assertEquals(listOf("470.l.1.tr.300"), selection.toPost.map { it.key })
    }

    @Test
    fun `every claim in a waiver batch sharing one timestamp is posted`() {
        val stamp = now - 1
        val batch = parseAll(*(1..8).map { tx("470.l.1.w.c.$it", stamp) }.toTypedArray())

        val selection = TransactionSelector.select(batch, initialized, null, now)

        assertEquals(8, selection.toPost.size)
    }

    @Test
    fun `a move already seen is not posted again`() {
        val feed = parseAll(tx("470.l.1.tr.300", now - 5), tx("470.l.1.tr.299", now - 10))

        val selection = TransactionSelector.select(feed, initialized + "470.l.1.tr.300", null, now)

        assertEquals(listOf("470.l.1.tr.299"), selection.toPost.map { it.key })
        assertTrue(selection.toSkip.isEmpty())
    }

    @Test
    fun `an unseen move older than the lookback is recorded without a post`() {
        val feed = parseAll(tx("470.l.1.tr.1", now - LOOKBACK_SECONDS - 1))

        val selection = TransactionSelector.select(feed, initialized, null, now)

        assertTrue(selection.toPost.isEmpty())
        assertEquals(listOf("470.l.1.tr.1"), selection.toSkip.map { it.key })
    }

    @Test
    fun `new moves are posted oldest first`() {
        val feed = parseAll(tx("c", now - 1), tx("b", now - 2), tx("a", now - 3))

        val selection = TransactionSelector.select(feed, initialized, null, now)

        assertEquals(listOf("a", "b", "c"), selection.toPost.map { it.key })
    }

    @Test
    fun `first run after upgrade posts only what the old cutoff had not reached`() {
        val oldCutoff = now - 30
        val feed = parseAll(tx("after", oldCutoff + 5), tx("at", oldCutoff), tx("before", oldCutoff - 5))

        val selection = TransactionSelector.select(feed, emptySet(), oldCutoff, now)

        assertTrue(selection.isFirstRun)
        assertEquals(listOf("at", "after"), selection.toPost.map { it.key })
        assertEquals(listOf("before"), selection.toSkip.map { it.key })
        assertEquals(setOf("at", "after", "before", INITIALIZED_MARKER), selection.keysToMarkSeen().keys)
    }

    @Test
    fun `first run on a fresh install records history without posting`() {
        val feed = parseAll(tx("a", now - 5), tx("b", now - 500))

        val selection = TransactionSelector.select(feed, emptySet(), null, now)

        assertTrue(selection.toPost.isEmpty())
        assertEquals(setOf("a", "b", INITIALIZED_MARKER), selection.keysToMarkSeen().keys)
    }

    @Test
    fun `a league with no moves yet still finishes its first run`() {
        val selection = TransactionSelector.select(emptyList(), emptySet(), null, now)

        assertTrue(selection.isFirstRun)
        assertEquals(setOf(INITIALIZED_MARKER), selection.keysToMarkSeen().keys)

        // The league's first move, published late, is then posted.
        val next = TransactionSelector.select(parseAll(tx("first", now - 60)), initialized, null, now)
        assertEquals(listOf("first"), next.toPost.map { it.key })
        assertFalse(next.isFirstRun)
    }

    @Test
    fun `a catch-up after an outage posts only the newest few, oldest of those first`() {
        // Eight moves from while the bot was down, all over an hour old.
        val backlog = parseAll(*(8 downTo 1).map { tx("old.$it", now - 2 * 60 * 60 - (8 - it)) }.toTypedArray())

        val selection = TransactionSelector.select(backlog, initialized, null, now, catchUpLimit = 5)

        assertEquals(listOf("old.4", "old.5", "old.6", "old.7", "old.8"), selection.toPost.map { it.key })
        assertEquals(setOf("old.1", "old.2", "old.3"), selection.overCatchUpLimit.map { it.key }.toSet())
        assertTrue(selection.keysToMarkSeen().keys.containsAll(listOf("old.1", "old.2", "old.3")))
    }

    @Test
    fun `a waiver run bigger than the catch-up limit is never capped`() {
        val batch = parseAll(*(1..8).map { tx("470.l.1.w.c.$it", now - 1) }.toTypedArray())

        val selection = TransactionSelector.select(batch, initialized, null, now, catchUpLimit = 5)

        assertEquals(8, selection.toPost.size)
        assertTrue(selection.overCatchUpLimit.isEmpty())
    }

    @Test
    fun `the catch-up limit leaves moves that just happened alone`() {
        val feed = parseAll(
            tx("live", now - 30),
            *(1..3).map { tx("old.$it", now - 3 * 60 * 60 + it) }.toTypedArray()
        )

        val selection = TransactionSelector.select(feed, initialized, null, now, catchUpLimit = 2)

        assertEquals(listOf("old.2", "old.3", "live"), selection.toPost.map { it.key })
        assertEquals(listOf("old.1"), selection.overCatchUpLimit.map { it.key })
    }

    @Test
    fun `a backfill posts every lost move at the listed timestamps, oldest first`() {
        // Nov 11: one move at 05:25:52 and eight waiver claims at 09:32:37 UTC, all already
        // recorded as seen by then, and more than a day old by the time this runs.
        val lostMove = 1_794_374_752L
        val waivers = 1_794_389_557L
        val feed = parseAll(
            tx("posted", 1_794_397_744L),
            *(1..8).map { tx("w.c.$it", waivers) }.toTypedArray(),
            tx("lost", lostMove),
            tx("unrelated", lostMove - 100)
        )
        val seen = initialized + feed.map { it.key }
        val later = waivers + 2 * LOOKBACK_SECONDS

        val backfill = TransactionSelector.selectBackfill(feed, seen, setOf(lostMove, waivers))

        assertEquals(listOf("lost") + (1..8).map { "w.c.$it" }, backfill.map { it.key })
        // Nothing about the normal path changes: none of these are posted as new.
        assertTrue(TransactionSelector.select(feed, seen, null, later).toPost.isEmpty())
    }

    @Test
    fun `a backfill posts once and not again after a restart`() {
        val feed = parseAll(tx("lost", now - 3 * LOOKBACK_SECONDS))
        val seen = initialized + "lost" + "${TransactionSelector.BACKFILL_PREFIX}lost"

        assertTrue(TransactionSelector.selectBackfill(feed, seen, setOf(now - 3 * LOOKBACK_SECONDS)).isEmpty())
    }

    @Test
    fun `a backfill leaves out a move that is already going out as new`() {
        val feed = parseAll(tx("new", now - 10))
        val posting = TransactionSelector.select(feed, initialized, null, now).toPost

        assertTrue(TransactionSelector.selectBackfill(feed, initialized, setOf(now - 10), posting).isEmpty())
    }

    @Test
    fun `backfill timestamps tolerate spaces and junk`() {
        assertEquals(setOf(1L, 2L), TransactionSelector.parseTimestamps(" 1, 2 ,x,,"))
        assertTrue(TransactionSelector.parseTimestamps(null).isEmpty())
    }

    @Test
    fun `a Yahoo error page is not read as an empty league`() {
        val error = xml("<yahoo:error><description>upstream connect error</description></yahoo:error>")

        assertNull(TransactionSelector.parse(error))
    }

    @Test
    fun `parse reads key and timestamp, not the player-level type fields`() {
        val parsed = TransactionSelector.parse(document(tx("470.l.1.tr.9", 1600013435)))!!

        assertEquals(1, parsed.size)
        assertEquals("470.l.1.tr.9", parsed[0].key)
        assertEquals(1600013435L, parsed[0].timestamp)
    }

    private fun parseAll(vararg transactions: String): List<YahooTransaction> =
        TransactionSelector.parse(document(*transactions))!!

    private fun document(vararg transactions: String): Document = xml(
        "<fantasy_content><league><transactions count=\"${transactions.size}\">" +
                transactions.joinToString("") +
                "</transactions></league></fantasy_content>"
    )

    private fun xml(body: String): Document =
        Jsoup.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>$body", "", Parser.xmlParser())

    private fun tx(key: String, timestamp: Long) = """
        <transaction>
          <transaction_key>$key</transaction_key>
          <type>add</type>
          <status>successful</status>
          <timestamp>$timestamp</timestamp>
          <players count="1"><player>
            <name><full>Chris Boswell</full></name>
            <transaction_data><type>add</type><destination_team_name>Women</destination_team_name></transaction_data>
          </player></players>
        </transaction>
    """.trimIndent()
}
