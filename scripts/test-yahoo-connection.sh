#!/bin/bash

# Test Yahoo Fantasy API Connection
# This script helps diagnose Yahoo API connection issues

echo "=== Yahoo Fantasy Bot Connection Test ==="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ ERROR: .env file not found!"
    echo "Please copy env.example to .env and fill in your Yahoo API credentials"
    exit 1
fi

# Source the .env file
set -a  # automatically export all variables
source .env
set +a

# Check for Railway-style variables and actual DATABASE_URL
if [[ "$JDBC_DATABASE_URL" == *"{{"*"}}"* ]]; then
    echo "⚠️  Detected Railway template variable in JDBC_DATABASE_URL"
    # Try to use DATABASE_URL if it exists
    if [ -n "$DATABASE_URL" ]; then
        echo "Using DATABASE_URL environment variable instead"
        JDBC_DATABASE_URL="$DATABASE_URL"
    else
        echo "Looking for Railway environment variables..."
    fi
fi

# Function to check if a variable is set
check_var() {
    local var_name=$1
    local var_value=$2
    
    if [ -z "$var_value" ]; then
        echo "❌ $var_name is not set"
        return 1
    else
        echo "✅ $var_name is configured"
        return 0
    fi
}

echo "1. Checking Environment Variables:"
echo "----------------------------------"
all_vars_set=true

check_var "YAHOO_CLIENT_ID" "$YAHOO_CLIENT_ID" || all_vars_set=false
check_var "YAHOO_CLIENT_SECRET" "$YAHOO_CLIENT_SECRET" || all_vars_set=false
check_var "YAHOO_GAME_KEY" "$YAHOO_GAME_KEY" || all_vars_set=false
check_var "YAHOO_LEAGUE_ID" "$YAHOO_LEAGUE_ID" || all_vars_set=false
# Special handling for database URL
if [ -z "$JDBC_DATABASE_URL" ] || [[ "$JDBC_DATABASE_URL" == *"{{"*"}}"* ]]; then
    # Check if we're in Railway environment
    if [ -n "$DATABASE_URL" ]; then
        echo "✅ DATABASE_URL is configured (Railway environment)"
        JDBC_DATABASE_URL="$DATABASE_URL"
    elif [ -n "$RAILWAY_ENVIRONMENT" ]; then
        echo "❌ JDBC_DATABASE_URL not resolved in Railway environment"
        echo "   Make sure Postgres service is attached to your Railway project"
        all_vars_set=false
    else
        echo "❌ JDBC_DATABASE_URL is not set or contains template variable"
        echo "   For local development, update .env with:"
        echo "   JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/yahoo_fantasy_bot?user=postgres&password=postgres"
        all_vars_set=false
    fi
else
    check_var "JDBC_DATABASE_URL" "$JDBC_DATABASE_URL"
fi

if [ "$all_vars_set" = false ]; then
    echo ""
    echo "❌ Some required environment variables are missing!"
    echo "Please check your .env file"
    exit 1
fi

echo ""
echo "2. Checking Database Connection:"
echo "--------------------------------"

# Extract database info from JDBC URL
DB_HOST=$(echo $JDBC_DATABASE_URL | sed -n 's/.*\/\/\([^:/]*\).*/\1/p')
DB_PORT=$(echo $JDBC_DATABASE_URL | sed -n 's/.*:\([0-9]*\)\/.*/\1/p')
DB_NAME=$(echo $JDBC_DATABASE_URL | sed -n 's/.*\/\([^?]*\).*/\1/p')

echo "Database Host: $DB_HOST"
echo "Database Port: $DB_PORT"
echo "Database Name: $DB_NAME"

# Test database connection using psql if available
if command -v psql &> /dev/null; then
    echo ""
    echo "Testing database connection..."
    
    # For simple JDBC URLs without user/password
    if [[ ! "$JDBC_DATABASE_URL" =~ user= ]]; then
        # Extract just the database name from the JDBC URL
        if psql -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
            echo "✅ Database connection successful (using current user)"
        else
            echo "❌ Database connection failed"
            echo "Please check that:"
            echo "  1. PostgreSQL is running (pg_isready)"
            echo "  2. Database '$DB_NAME' exists (createdb $DB_NAME)"
        fi
    else
        # For JDBC URLs with user credentials, try a different approach
        # Extract user from the JDBC URL
        DB_USER=$(echo $JDBC_DATABASE_URL | sed -n 's/.*user=\([^&]*\).*/\1/p')
        DB_PASS=$(echo $JDBC_DATABASE_URL | sed -n 's/.*password=\([^&]*\).*/\1/p')
        
        if PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
            echo "✅ Database connection successful"
        else
            echo "❌ Database connection failed"
            echo "Please check your database credentials and ensure PostgreSQL is running"
            echo "You may need to create the user: CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';"
        fi
    fi
else
    echo "⚠️  psql not found - skipping database connection test"
fi

echo ""
echo "3. Checking OAuth Token Status:"
echo "-------------------------------"

# Create a temporary Kotlin script to check token status
cat > /tmp/check_token.kt << 'EOF'
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() {
    val jdbcUrl = System.getenv("JDBC_DATABASE_URL")
    
    try {
        val connection = DriverManager.getConnection(jdbcUrl)
        
        // Check for OAuth tokens
        val tokenQuery = """
            SELECT retrieved, access_token, refresh_token, expires_in 
            FROM token 
            ORDER BY retrieved DESC 
            LIMIT 1
        """.trimIndent()
        
        val tokenStmt = connection.prepareStatement(tokenQuery)
        val tokenRs = tokenStmt.executeQuery()
        
        if (tokenRs.next()) {
            val retrieved = tokenRs.getLong("retrieved")
            val accessToken = tokenRs.getString("access_token")
            val refreshToken = tokenRs.getString("refresh_token")
            val expiresIn = tokenRs.getInt("expires_in")
            
            println("✅ OAuth token found in database")
            
            // Calculate token expiry
            val retrievedInstant = Instant.ofEpochMilli(retrieved)
            val expiryInstant = retrievedInstant.plusSeconds(expiresIn.toLong())
            val now = Instant.now()
            
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            
            println("Token retrieved: ${formatter.format(retrievedInstant)}")
            println("Token expires: ${formatter.format(expiryInstant)}")
            
            if (now.isBefore(expiryInstant)) {
                println("✅ Token is still valid")
            } else {
                println("❌ Token has expired and needs refresh")
            }
        } else {
            println("❌ No OAuth token found in database")
            println("You need to authenticate with Yahoo first!")
            println("Visit http://localhost:8080 and click 'Login with Yahoo'")
        }
        
        // Check latest time checked
        val timeQuery = "SELECT time FROM latest_time_checked ORDER BY time DESC LIMIT 1"
        val timeStmt = connection.prepareStatement(timeQuery)
        val timeRs = timeStmt.executeQuery()
        
        if (timeRs.next()) {
            val lastChecked = timeRs.getLong("time")
            val lastCheckedInstant = Instant.ofEpochMilli(lastChecked)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            println("\nLast transaction check: ${formatter.format(lastCheckedInstant)}")
        }
        
        connection.close()
    } catch (e: Exception) {
        println("❌ Error connecting to database: ${e.message}")
    }
}
EOF

# Compile and run the Kotlin script if kotlin is available
if command -v kotlin &> /dev/null; then
    kotlin /tmp/check_token.kt
    rm /tmp/check_token.kt
else
    echo "⚠️  Kotlin not found - creating SQL script instead"
    
    # Create SQL script for manual checking
    cat > /tmp/check_token.sql << 'EOF'
-- Check OAuth token status
SELECT 
    retrieved,
    access_token IS NOT NULL as has_access_token,
    refresh_token IS NOT NULL as has_refresh_token,
    expires_in,
    (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000 as is_valid
FROM token 
ORDER BY retrieved DESC 
LIMIT 1;

-- Check last transaction check time
SELECT 
    time,
    to_timestamp(time/1000) as last_checked
FROM latest_time_checked 
ORDER BY time DESC 
LIMIT 1;

-- Check recent message history
SELECT 
    timestamp,
    message_type,
    transaction_type,
    messaging_service,
    success,
    error_message
FROM message_history
ORDER BY timestamp DESC
LIMIT 10;
EOF
    
    echo "SQL script created at /tmp/check_token.sql"
    echo "Run: psql \"$JDBC_DATABASE_URL\" -f /tmp/check_token.sql"
fi

echo ""
echo "4. Bot Process Status:"
echo "---------------------"

# Check if bot is running
BOT_PID=$(pgrep -f "com.landonpatmore.yahoofantasybot.bot")
if [ -n "$BOT_PID" ]; then
    echo "✅ Bot is running (PID: $BOT_PID)"
else
    echo "❌ Bot is not running"
fi

# Check if backend is running
BACKEND_PID=$(pgrep -f "com.landonpatmore.yahoofantasybot.backend")
if [ -n "$BACKEND_PID" ]; then
    echo "✅ Backend is running (PID: $BACKEND_PID)"
else
    echo "❌ Backend is not running"
fi

echo ""
echo "5. Log Files:"
echo "-------------"

# Check for recent logs
if [ -f "bot.log" ]; then
    echo "Recent bot logs:"
    tail -20 bot.log | grep -E "(ERROR|WARN|Exception|Failed|Error)"
else
    echo "No bot.log file found"
fi

echo ""
echo "=== Test Complete ==="
echo ""
echo "Troubleshooting Tips:"
echo "1. If no OAuth token found: Visit http://localhost:8080 and authenticate with Yahoo"
echo "2. If token expired: The bot should auto-refresh, but check bot logs for errors"
echo "3. If bot not running: Run './gradlew :bot:run' to start it"
echo "4. Check bot console output for 'Grabbing Data...' messages every 15 seconds"
