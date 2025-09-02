#!/bin/bash

# Test Transaction Processing
# This script manually triggers a transaction check to test the Yahoo API connection

echo "=== Yahoo Fantasy Bot Transaction Test ==="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ ERROR: .env file not found!"
    exit 1
fi

# Source the .env file
set -a  # automatically export all variables
source .env
set +a

# Handle Railway template variables
if [[ "$JDBC_DATABASE_URL" == *"{{"*"}}"* ]] && [ -n "$DATABASE_URL" ]; then
    echo "Using DATABASE_URL for database connection"
    JDBC_DATABASE_URL="$DATABASE_URL"
fi

# Create a temporary Kotlin script to test transaction fetching
cat > /tmp/test_transactions.kt << 'EOF'
@file:DependsOn("com.github.scribejava:scribejava-apis:8.3.3")
@file:DependsOn("com.github.scribejava:scribejava-core:8.3.3")
@file:DependsOn("org.jsoup:jsoup:1.14.3")
@file:DependsOn("org.postgresql:postgresql:42.3.3")

import com.github.scribejava.apis.YahooApi20
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuthService
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.sql.DriverManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() {
    val clientId = System.getenv("YAHOO_CLIENT_ID")
    val clientSecret = System.getenv("YAHOO_CLIENT_SECRET")
    val gameKey = System.getenv("YAHOO_GAME_KEY")
    val leagueId = System.getenv("YAHOO_LEAGUE_ID")
    val jdbcUrl = System.getenv("JDBC_DATABASE_URL")
    
    if (clientId.isNullOrEmpty() || clientSecret.isNullOrEmpty()) {
        println("❌ Yahoo API credentials not configured!")
        return
    }
    
    println("1. Checking database for OAuth token...")
    
    try {
        val connection = DriverManager.getConnection(jdbcUrl)
        
        // Get the latest token
        val tokenQuery = """
            SELECT retrieved, access_token, refresh_token, expires_in 
            FROM token 
            ORDER BY retrieved DESC 
            LIMIT 1
        """.trimIndent()
        
        val tokenStmt = connection.prepareStatement(tokenQuery)
        val tokenRs = tokenStmt.executeQuery()
        
        if (!tokenRs.next()) {
            println("❌ No OAuth token found in database!")
            println("Please authenticate with Yahoo first at http://localhost:8080")
            return
        }
        
        val retrieved = tokenRs.getLong("retrieved")
        val accessToken = tokenRs.getString("access_token")
        val refreshToken = tokenRs.getString("refresh_token")
        val expiresIn = tokenRs.getInt("expires_in")
        
        println("✅ Found OAuth token")
        
        // Check if token is expired
        val retrievedInstant = Instant.ofEpochMilli(retrieved)
        val expiryInstant = retrievedInstant.plusSeconds(expiresIn.toLong())
        val now = Instant.now()
        
        if (now.isAfter(expiryInstant)) {
            println("⚠️  Token is expired, would need refresh in production")
        } else {
            println("✅ Token is still valid")
        }
        
        println("\n2. Testing Yahoo API connection...")
        
        // Create OAuth service
        val oauthService = ServiceBuilder(clientId)
            .apiSecret(clientSecret)
            .callback("oob")
            .build(YahooApi20.instance())
        
        val token = OAuth2AccessToken(accessToken, "bearer", expiresIn, refreshToken, null, null)
        
        // Test transaction endpoint
        val url = "https://fantasysports.yahooapis.com/fantasy/v2/league/$gameKey.l.$leagueId/transactions"
        println("Fetching from: $url")
        
        val request = OAuthRequest(Verb.GET, url)
        oauthService.signRequest(token, request)
        
        println("Executing request...")
        val response = oauthService.execute(request)
        
        println("\n3. Response Details:")
        println("Status Code: ${response.code}")
        println("Response Length: ${response.body?.length ?: 0} characters")
        
        if (response.code == 200) {
            println("✅ Successfully connected to Yahoo API!")
            
            // Parse the XML
            val document = Jsoup.parse(response.body, "", Parser.xmlParser())
            val transactions = document.select("transaction")
            
            println("\n4. Transaction Data:")
            println("Total transactions found: ${transactions.size}")
            
            if (transactions.isNotEmpty()) {
                println("\nRecent transactions (last 5):")
                transactions.take(5).forEachIndexed { index, transaction ->
                    val type = transaction.select("type").text()
                    val timestamp = transaction.select("timestamp").text()
                    val timestampLong = timestamp.toLongOrNull() ?: 0L
                    val date = Instant.ofEpochSecond(timestampLong)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    
                    println("${index + 1}. Type: $type, Date: $date")
                    
                    // Show details based on type
                    when (type) {
                        "add" -> {
                            val team = transaction.select("destination_team_name").text()
                            val player = transaction.select("player full").text()
                            println("   $team added $player")
                        }
                        "drop" -> {
                            val team = transaction.select("source_team_name").text()
                            val player = transaction.select("player full").text()
                            println("   $team dropped $player")
                        }
                        "trade" -> {
                            println("   Trade transaction")
                        }
                    }
                }
                
                // Check latest transaction time vs last checked time
                val latestQuery = "SELECT time FROM latest_time_checked ORDER BY time DESC LIMIT 1"
                val latestStmt = connection.prepareStatement(latestQuery)
                val latestRs = latestStmt.executeQuery()
                
                if (latestRs.next()) {
                    val lastChecked = latestRs.getLong("time") / 1000 // Convert to seconds
                    val newestTransaction = transactions.first().select("timestamp").text().toLongOrNull() ?: 0L
                    
                    println("\n5. Processing Status:")
                    println("Last checked timestamp: $lastChecked")
                    println("Newest transaction timestamp: $newestTransaction")
                    
                    if (newestTransaction > lastChecked) {
                        println("⚠️  There are NEW transactions that haven't been processed!")
                    } else {
                        println("✅ All transactions have been processed")
                    }
                }
            }
            
        } else {
            println("❌ Failed to connect to Yahoo API")
            println("Response: ${response.body}")
        }
        
        connection.close()
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}

main()
EOF

echo "Running transaction test..."
echo ""

# Check if kotlin script runner is available
if command -v kotlin &> /dev/null; then
    kotlin /tmp/test_transactions.kt
    rm /tmp/test_transactions.kt
else
    echo "⚠️  Kotlin script runner not found"
    echo "Installing it with: sdk install kotlin"
    echo ""
    echo "Alternatively, you can run this SQL query to check transaction status:"
    echo ""
    cat scripts/check-transactions.sql
fi
