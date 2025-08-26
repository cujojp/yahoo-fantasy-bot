/*
 * MIT License
 *
 * Copyright (c) 2020 Landon Patmore
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

package com.landonpatmore.yahoofantasybot.backend

import com.landonpatmore.yahoofantasybot.backend.routes.getRoutes
import com.landonpatmore.yahoofantasybot.backend.routes.putRoutes
import com.landonpatmore.yahoofantasybot.backend.routes.serveFrontend
import io.ktor.server.application.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.compression.*
// import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.defaultheaders.*
import org.koin.core.context.startKoin
import com.landonpatmore.yahoofantasybot.shared.database.Db
import org.koin.core.context.GlobalContext
import com.landonpatmore.yahoofantasybot.shared.modules.sharedModule
import io.ktor.http.*

fun main(args: Array<String>): Unit {
    startKoin {
        modules(sharedModule)
    }
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val db: Db = GlobalContext.get().get()

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
            serializeNulls()
        }
    }
    install(Compression)
    // install(CallLogging) // TODO: Fix import issue
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
    // TODO: Will move to locations later

    getRoutes(db, getCurrentVersion(this.javaClass.classLoader))
    putRoutes(db)
    serveFrontend()
}

private fun getCurrentVersion(classLoader: ClassLoader) : String? {
    return try {
        classLoader.getResource("VERSION")?.readText()?.trim()
    } catch (e: Exception) {
        println("Warning: Could not read VERSION file: ${e.message}")
        null
    }
}

