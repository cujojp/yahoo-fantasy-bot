package com.landonpatmore.yahoofantasybot.shared.database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Every Yahoo transaction the bot has already handled, keyed by Yahoo's own
 * transaction_key. This replaced a "latest time checked" cutoff, which skipped
 * any transaction Yahoo published a moment after the timestamp it carries.
 */
object SeenTransactionsTable : Table("seen_transactions") {
    val transactionKey = varchar("transaction_key", 64)
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(transactionKey)
}
