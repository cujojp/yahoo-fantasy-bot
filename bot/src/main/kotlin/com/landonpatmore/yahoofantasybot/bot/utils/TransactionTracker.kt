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

package com.landonpatmore.yahoofantasybot.bot.utils

import com.landonpatmore.yahoofantasybot.shared.database.Db
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks transaction processing to ensure we only update latesttimes
 * after transactions are successfully sent
 */
class TransactionTracker(private val database: Db) {
    private val pendingTransactions = AtomicInteger(0)
    private val lastProcessingTime = AtomicLong(0)
    
    /**
     * Called when starting to process a batch of transactions
     */
    fun startProcessing(transactionCount: Int, processingTime: Long) {
        println("[TransactionTracker] Starting to process $transactionCount transactions")
        pendingTransactions.set(transactionCount)
        lastProcessingTime.set(processingTime)
    }
    
    /**
     * Called when a transaction is successfully sent
     */
    fun onTransactionSent(success: Boolean) {
        val remaining = pendingTransactions.decrementAndGet()
        println("[TransactionTracker] Transaction sent (success=$success). Remaining: $remaining")
        
        // If all transactions have been processed, update the latest check time
        if (remaining <= 0 && lastProcessingTime.get() > 0) {
            println("[TransactionTracker] All transactions processed. Updating latest check time to: ${lastProcessingTime.get()}")
            database.saveLatestTimeChecked(lastProcessingTime.get())
            lastProcessingTime.set(0) // Reset
        }
    }
    
    /**
     * Called if processing fails completely
     */
    fun onProcessingFailed() {
        println("[TransactionTracker] Processing failed. Not updating latest check time.")
        pendingTransactions.set(0)
        lastProcessingTime.set(0)
    }
}
