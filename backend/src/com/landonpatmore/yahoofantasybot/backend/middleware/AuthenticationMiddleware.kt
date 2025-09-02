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

package com.landonpatmore.yahoofantasybot.backend.middleware

import com.landonpatmore.yahoofantasybot.shared.database.Db
import com.landonpatmore.yahoofantasybot.shared.utils.models.EnvVariable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import org.koin.ktor.ext.inject

/**
 * Session data for authenticated users
 */
data class UserSession(
    val isAuthenticated: Boolean = false,
    val yahooUserId: String? = null,
    val sessionCreatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if session is expired (24 hours by default)
     */
    fun isExpired(maxAgeHours: Int = 24): Boolean {
        val maxAgeMs = maxAgeHours * 60 * 60 * 1000L
        return System.currentTimeMillis() - sessionCreatedAt > maxAgeMs
    }
}

/**
 * Authentication middleware that protects routes based on configuration
 */
class AuthenticationMiddleware {
    
    companion object {
        // Routes that don't require authentication
        private val PUBLIC_ROUTES = setOf(
            "/authenticate",
            "/auth",
            "/checkAuth",
            "/health", // Health check for Railway
            "/favicon.ico"
        )
        
        // Static file patterns that are public
        private val PUBLIC_PATTERNS = listOf(
            Regex("/static/.*"),
            Regex("/assets/.*"),
            Regex(".*\\.(css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|otf)$")
        )
        
        /**
         * Check if authentication is required for Railway deployment
         */
        fun isAuthenticationRequired(): Boolean {
            // Check environment variables to determine if we're in production
            val railwayEnv = System.getenv("RAILWAY_ENVIRONMENT")
            val isDevelopment = System.getenv("NODE_ENV") == "development"
            val forceAuth = EnvVariable.Str.ForceAuthentication.variable.equals("true", ignoreCase = true)
            
            // Require auth if:
            // 1. We're on Railway (production)
            // 2. OR force auth is enabled
            // 3. AND we're not in development mode
            return (railwayEnv != null || forceAuth) && !isDevelopment
        }
        
        /**
         * Check if a route is public (doesn't require authentication)
         */
        fun isPublicRoute(path: String): Boolean {
            // Check exact matches
            if (PUBLIC_ROUTES.contains(path)) {
                return true
            }
            
            // Check pattern matches
            return PUBLIC_PATTERNS.any { it.matches(path) }
        }
    }
}

/**
 * Simple authentication checker function for routes
 */
suspend fun ApplicationCall.checkAuthentication(database: Db): Boolean {
    val path = request.local.uri
    
    // Skip authentication if not required or route is public
    if (!AuthenticationMiddleware.isAuthenticationRequired() || 
        AuthenticationMiddleware.isPublicRoute(path)) {
        return true
    }
    
    // Get current session
    val session = sessions.get<UserSession>()
    
    // Check if user is authenticated
    val isAuthenticated = when {
        session == null -> false
        session.isExpired() -> {
            // Clear expired session
            sessions.clear<UserSession>()
            false
        }
        !session.isAuthenticated -> false
        // Additional check: verify token still exists in database
        database.getLatestTokenData() == null -> {
            sessions.clear<UserSession>()
            false
        }
        else -> true
    }
    
    if (!isAuthenticated) {
        // For API routes, return JSON error
        if (path.startsWith("/api/")) {
            respond(HttpStatusCode.Unauthorized, mapOf(
                "error" to "Authentication required",
                "message" to "Please authenticate with Yahoo to access this resource"
            ))
            return false
        }
        
        // For web routes, redirect to authentication
        respondRedirect("/authenticate")
        return false
    }
    
    return true
}

/**
 * Session configuration for Ktor
 */
fun Application.configureSession() {
    install(Sessions) {
        cookie<UserSession>("yahoo_fantasy_session") {
            // Use secure settings for production
            val isProduction = AuthenticationMiddleware.isAuthenticationRequired()
            
            cookie.path = "/"
            cookie.maxAgeInSeconds = 24 * 60 * 60 // 24 hours
            cookie.httpOnly = true
            cookie.secure = isProduction // HTTPS only in production
            cookie.extensions["SameSite"] = if (isProduction) "Strict" else "Lax"
            
            // Use a more secure key in production
            val sessionKey = EnvVariable.Str.SessionSecretKey.variable.ifEmpty {
                if (isProduction) {
                    throw IllegalStateException("SESSION_SECRET_KEY environment variable must be set in production")
                } else {
                    "development-key-not-secure-change-in-production"
                }
            }
            
            transform(SessionTransportTransformerMessageAuthentication(hex(sessionKey)))
        }
    }
}

/**
 * Updates authentication routes with session support
 */
fun Route.configureAuthenticationRoutes(database: Db) {
    // Enhanced checkAuth with session support
    get("/checkAuth") {
        val session = call.sessions.get<UserSession>()
        val hasToken = database.getLatestTokenData() != null
        
        val isAuthenticated = when {
            !AuthenticationMiddleware.isAuthenticationRequired() -> hasToken
            session?.isAuthenticated == true && !session.isExpired() && hasToken -> true
            else -> false
        }
        
        call.respond(mapOf(
            "authenticated" to isAuthenticated,
            "authRequired" to AuthenticationMiddleware.isAuthenticationRequired(),
            "environment" to (System.getenv("RAILWAY_ENVIRONMENT") ?: "development")
        ))
    }
    
    // Enhanced auth callback with session creation
    get("/auth") {
        try {
            val code = call.request.queryParameters["code"]
            if (code != null) {
                // This would be handled by the existing OAuth service
                // For now, we'll create a session when token is saved
                database.getLatestTokenData()?.let {
                    // Create authenticated session
                    call.sessions.set(UserSession(
                        isAuthenticated = true,
                        yahooUserId = "yahoo_user", // Could extract from token
                        sessionCreatedAt = System.currentTimeMillis()
                    ))
                }
            }
            call.respondRedirect("/")
        } catch (e: Exception) {
            println("Authentication error: ${e.message}")
            call.respondRedirect("/authenticate?error=auth_failed")
        }
    }
    
    // Add logout endpoint
    get("/logout") {
        call.sessions.clear<UserSession>()
        // Optionally clear the database token as well
        // database.clearTokens() // If this method exists
        call.respondRedirect("/?logged_out=true")
    }
}
