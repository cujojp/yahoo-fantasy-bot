package com.landonpatmore.yahoofantasybot.bot.utils

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class YahooTransaction(val key: String, val timestamp: Long, val element: Element)

/**
 * Decides which transactions in a Yahoo /transactions response still need a post.
 *
 * A transaction counts as new when its transaction_key hasn't been seen yet. This
 * used to be decided by comparing timestamps against the time of the last check,
 * which missed anything Yahoo published a moment after the time it is stamped with.
 * Overnight waivers are the common case, because the whole batch shares one stamp.
 */
object TransactionSelector {

    /**
     * Stored with the seen keys once the table has been seeded. Without it, a table
     * that is merely empty (a league with no moves yet) would look like a first run.
     */
    const val INITIALIZED_MARKER = "__initialized__"

    /**
     * Unseen transactions older than this are recorded without a post. This covers a
     * new league or season, where every key is unseen, and a long outage. It is not
     * part of the normal path.
     */
    const val LOOKBACK_SECONDS = 24 * 60 * 60L

    data class Selection(
        val toPost: List<YahooTransaction>,
        val toSkip: List<YahooTransaction>,
        val isFirstRun: Boolean
    ) {
        /** Everything to record as seen, including the marker on a first run. */
        fun keysToMarkSeen(): Map<String, Long> {
            val keys = (toPost + toSkip).associate { it.key to it.timestamp }
            return if (isFirstRun) keys + (INITIALIZED_MARKER to 0L) else keys
        }
    }

    /**
     * Returns null when the document isn't a transactions response (for example a
     * Yahoo error page). An empty list then can't be mistaken for "no moves yet".
     */
    fun parse(document: Document): List<YahooTransaction>? {
        if (document.getElementsByTag("transactions").isEmpty()) return null

        return document.getElementsByTag("transaction").mapNotNull { element ->
            val key = element.getElementsByTag("transaction_key").firstOrNull()?.text()?.trim()
            val timestamp = element.getElementsByTag("timestamp").firstOrNull()?.text()?.trim()?.toLongOrNull()
            if (key.isNullOrEmpty() || timestamp == null) {
                println("[TransactionSelector] Ignoring a transaction with no key or timestamp")
                null
            } else {
                YahooTransaction(key, timestamp, element)
            }
        }.distinctBy { it.key }
    }

    /**
     * @param legacyCutoffSeconds the old "latest time checked" in seconds, if any. It
     *   is only used on the first run, so moves the old code already posted aren't
     *   posted again.
     */
    fun select(
        transactions: List<YahooTransaction>,
        seenKeys: Set<String>,
        legacyCutoffSeconds: Long?,
        nowSeconds: Long
    ): Selection {
        val isFirstRun = INITIALIZED_MARKER !in seenKeys
        val unseen = transactions.filter { it.key !in seenKeys }
        val oldest = nowSeconds - LOOKBACK_SECONDS

        val (toPost, toSkip) = unseen.partition { transaction ->
            val recentEnough = transaction.timestamp >= oldest
            if (isFirstRun) {
                // With no earlier cutoff (a fresh install), the league's history is recorded
                // without posts and only moves made from now on get posted.
                recentEnough && legacyCutoffSeconds != null && transaction.timestamp >= legacyCutoffSeconds
            } else {
                recentEnough
            }
        }

        // Yahoo lists the newest first; post in the order the moves happened.
        return Selection(toPost.sortedBy { it.timestamp }, toSkip, isFirstRun)
    }
}
