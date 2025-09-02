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

package com.landonpatmore.yahoofantasybot.backend.routes

import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.oauth.OAuth20Service
import com.landonpatmore.yahoofantasybot.backend.models.Authentication
import com.landonpatmore.yahoofantasybot.backend.models.ReleaseInformation
import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.serialization.gson.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private var service: OAuth20Service? = null

fun Application.getRoutes(db: Db, currentVersion: String?) {
    routing {
        getMessagingServices(db)
        getLeagues(db)
        getAlerts(db)
        checkAuth(db)
        getMessageType(db)
        getReleaseInformation(currentVersion)
        authenticate(db)
        auth(db)
    }
}

private fun Route.getMessagingServices(db: Db) {
    get("/messagingServices") {
        call.respond(db.getMessagingServices())
    }
}

private fun Route.getLeagues(db: Db) {
    get("/leagues") {
        call.respond(db.getLeagues())
    }
}

private fun Route.getAlerts(db: Db) {
    get("/alerts") {
        call.respond(db.getAlerts())
    }
}

private fun Route.getMessageType(db: Db) {
    get("/messageType") {
        call.respond(db.getMessageType())
    }
}

private fun Route.getReleaseInformation(currentVersion: String?) {
    get("/releaseInformation") {
        try {
            val release = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    gson()
                }
            }.use { client ->
                client.get(ReleaseInformation.URL).body<ReleaseInformation>()
            }.apply {
                this.currentVersion = currentVersion ?: "0.0.0"
                // Check if latestVersion is null before using it
                if (latestVersion != null) {
                    upgrade = versionChecker(this.currentVersion, latestVersion)
                    if (!upgrade) {
                        changelog = null
                    }
                } else {
                    upgrade = false
                    changelog = null
                }
            }
            call.respond(release)
        } catch (e: Exception) {
            println("Error fetching release information: ${e.message}")
            // Return a default response on error
            call.respond(ReleaseInformation(
                changelog = null,
                latestVersion = currentVersion ?: "0.0.0",
                currentVersion = currentVersion ?: "0.0.0",
                upgrade = false
            ))
        }
    }
}

fun Route.authenticate(db: Db) {
    get("/authenticate") {
        val forceReauth = call.request.queryParameters["force"] == "true"
        val tokenData = db.getLatestTokenData()
        val needsAuth = if (forceReauth || tokenData == null) {
            true
        } else {
            val (retrieved, token) = tokenData
            val expiryTime = retrieved + (token.expiresIn * 1000L)
            val isExpired = expiryTime <= System.currentTimeMillis()
            isExpired // Need auth if token is expired
        }
        
        if (needsAuth) {
            authenticationUrl("https://${call.request.headers["Host"] ?: "localhost:8080"}")?.let {
                call.respondRedirect(it)
            }
        } else {
            call.respondRedirect("/")
        }
    }
}

fun Route.checkAuth(db: Db) {
    get("/checkAuth") {
        if (db.getLatestTokenData() == null) {
            call.respond(Authentication(false))
        } else {
            call.respond(Authentication(true))
        }
    }
}

fun Route.auth(db: Db) {
    get("/auth") {
        @Suppress("BlockingMethodInNonBlockingContext")
        service?.getAccessToken(call.request.queryParameters["code"])?.let {
            db.saveToken(it)
        }
        call.respondRedirect("/")
    }
}

private fun versionChecker(
    currentVersion: String?,
    tagName: String?
): Boolean {
    if (currentVersion == null || tagName == null) {
        return false // since we cannot determine versions
    }
    
    val currentVersionSplit = currentVersion.split(".")
    val latestVersion = tagName.split(".")

    if (latestVersion.size > currentVersionSplit.size) {
        return true
    } else {
        for ((i, latest) in latestVersion.withIndex()) {
            when {
                latest.toInt() == currentVersionSplit[i].toInt() -> continue
                latest.toInt() < currentVersionSplit[i].toInt() -> return false
                latest.toInt() > currentVersionSplit[i].toInt() -> return true
            }
        }
        return false
    }
}

private fun authenticationUrl(url: String): String? {
    service = ServiceBuilder(EnvVariable.Str.YahooClientId.variable)
        .apiSecret(EnvVariable.Str.YahooClientSecret.variable)
        .callback("$url/auth")
        .defaultScope("fspt-r")
        .build(YahooApi20.instance())

    return service?.authorizationUrl
}

