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

    /**
     * An unseen move stamped longer ago than this is a catch-up (the bot was down or
     * hung when it happened), not something that just happened.
     */
    const val CATCH_UP_AGE_SECONDS = 60 * 60L

    /**
     * The most catch-up moves one check will post. Each post needs its own news lookup
     * and OpenAI call, so after an outage only the newest are posted and the rest are
     * recorded without a post. Moves that just happened are never capped: a waiver run
     * can produce a dozen claims at once, and every one of them should post.
     * Overridable without a deploy.
     */
    val CATCH_UP_LIMIT: Int =
        System.getenv("TRANSACTION_CATCH_UP_LIMIT")?.toIntOrNull()?.takeIf { it > 0 } ?: 5

    /**
     * Stored as "backfill:<transaction_key>" once a backfilled move has been posted, kept
     * apart from its seen key because a backfilled move was already seen.
     */
    const val BACKFILL_PREFIX = "backfill:"

    /**
     * One-off repair: Yahoo timestamps (seconds), comma-separated, of moves that were lost
     * and should be posted now. Every move at those timestamps posts once, ignoring the
     * lookback and the catch-up limit. Remove the variable once they have gone out.
     */
    val BACKFILL_TIMESTAMPS: Set<Long> = parseTimestamps(System.getenv("TRANSACTION_BACKFILL_TIMESTAMPS"))

    fun parseTimestamps(raw: String?): Set<Long> =
        raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()

    /**
     * The moves at [timestamps] that haven't been backfilled yet. Moves already in
     * [posting] are left out, since they are going out anyway.
     */
    fun selectBackfill(
        transactions: List<YahooTransaction>,
        seenKeys: Set<String>,
        timestamps: Set<Long>,
        posting: List<YahooTransaction> = emptyList()
    ): List<YahooTransaction> {
        if (timestamps.isEmpty()) return emptyList()
        val postingKeys = posting.map { it.key }.toSet()
        return transactions
            .filter { it.timestamp in timestamps }
            .filter { "$BACKFILL_PREFIX${it.key}" !in seenKeys && it.key !in postingKeys }
            .sortedBy { it.timestamp }
    }

    data class Selection(
        val toPost: List<YahooTransaction>,
        val toSkip: List<YahooTransaction>,
        val isFirstRun: Boolean,
        /** Catch-up moves over the limit, recorded without a post. */
        val overCatchUpLimit: List<YahooTransaction> = emptyList()
    ) {
        /** Everything to record as seen, including the marker on a first run. */
        fun keysToMarkSeen(): Map<String, Long> {
            val keys = (toPost + toSkip + overCatchUpLimit).associate { it.key to it.timestamp }
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
        nowSeconds: Long,
        catchUpLimit: Int = CATCH_UP_LIMIT
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

        val (live, catchUp) = toPost.partition { it.timestamp >= nowSeconds - CATCH_UP_AGE_SECONDS }
        val catchUpNewestFirst = catchUp.sortedByDescending { it.timestamp }
        val catchUpPosted = catchUpNewestFirst.take(catchUpLimit)
        val overLimit = catchUpNewestFirst.drop(catchUpLimit)

        // Yahoo lists the newest first; post in the order the moves happened.
        return Selection(
            toPost = (catchUpPosted + live).sortedBy { it.timestamp },
            toSkip = toSkip,
            isFirstRun = isFirstRun,
            overCatchUpLimit = overLimit
        )
    }
}
