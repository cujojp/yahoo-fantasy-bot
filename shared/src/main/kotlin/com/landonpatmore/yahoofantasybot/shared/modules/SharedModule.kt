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

package com.landonpatmore.yahoofantasybot.shared.modules

import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.utils.EnvVariablesChecker
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import org.koin.dsl.module

val sharedModule = module {
    single { EnvVariablesChecker() }
    single { 
        // Get the database URL at runtime, not at class initialization
        val jdbcUrl = System.getenv("JDBC_DATABASE_URL")
        val dbUrl = System.getenv("DATABASE_URL")
        
        println("SharedModule - Creating Db instance:")
        println("  JDBC_DATABASE_URL: ${jdbcUrl ?: "not set"}")
        println("  DATABASE_URL: ${dbUrl ?: "not set"}")
        
        var urlToUse = when {
            !jdbcUrl.isNullOrEmpty() && jdbcUrl != "\$DATABASE_URL" -> jdbcUrl
            !dbUrl.isNullOrEmpty() -> dbUrl
            else -> {
                println("ERROR: No database URL found in environment variables!")
                println("Available env vars: ${System.getenv().keys.sorted().joinToString(", ")}")
                throw IllegalStateException("Database URL not configured. Please set JDBC_DATABASE_URL or DATABASE_URL in Railway environment variables.")
            }
        }
        
        // Railway workaround: If the URL contains "railway" as the database name,
        // but that database doesn't exist, try using "postgres" instead
        if (urlToUse.contains("/railway") && urlToUse.contains(".railway.internal")) {
            println("  Railway environment detected")
            println("  Original URL uses 'railway' database")
            println("  Note: If connection fails, Railway might be using 'postgres' as the database name")
        }
        
        println("  Using URL: $urlToUse")
        Db(urlToUse)
    }
}