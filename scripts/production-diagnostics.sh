#!/bin/bash

# Production Diagnostics Script for Yahoo Fantasy Bot
# This script helps diagnose issues in production (Railway) deployments

echo "=== Yahoo Fantasy Bot Production Diagnostics ==="
echo "Generated at: $(date)"
echo ""

# Function to mask sensitive data
mask_sensitive() {
    local input=$1
    local length=${#input}
    if [ $length -le 8 ]; then
        echo "***"
    else
        echo "${input:0:3}...${input: -3}"
    fi
}

# Check environment
echo "1. Environment Check:"
echo "---------------------"
if [ -n "$RAILWAY_ENVIRONMENT" ]; then
    echo "✅ Running in Railway environment: $RAILWAY_ENVIRONMENT"
else
    echo "❌ Not running in Railway environment"
fi

echo ""
echo "2. Environment Variables Status:"
echo "--------------------------------"

# Check Yahoo API credentials
if [ -n "$YAHOO_CLIENT_ID" ]; then
    echo "✅ YAHOO_CLIENT_ID is set (masked: $(mask_sensitive "$YAHOO_CLIENT_ID"))"
else
    echo "❌ YAHOO_CLIENT_ID is not set"
fi

if [ -n "$YAHOO_CLIENT_SECRET" ]; then
    echo "✅ YAHOO_CLIENT_SECRET is set (masked: $(mask_sensitive "$YAHOO_CLIENT_SECRET"))"
else
    echo "❌ YAHOO_CLIENT_SECRET is not set"
fi

if [ -n "$YAHOO_GAME_KEY" ]; then
    echo "✅ YAHOO_GAME_KEY is set: $YAHOO_GAME_KEY"
else
    echo "❌ YAHOO_GAME_KEY is not set"
fi

if [ -n "$YAHOO_LEAGUE_ID" ]; then
    echo "✅ YAHOO_LEAGUE_ID is set: $YAHOO_LEAGUE_ID"
else
    echo "❌ YAHOO_LEAGUE_ID is not set"
fi

# Check database URL
echo ""
echo "3. Database Configuration:"
echo "--------------------------"
if [ -n "$DATABASE_URL" ]; then
    echo "✅ DATABASE_URL is set"
    # Parse database URL to show non-sensitive parts
    if [[ $DATABASE_URL =~ ^postgres(ql)?://([^:]+):([^@]+)@([^:/]+):?([0-9]*)/(.+)$ ]]; then
        DB_USER="${BASH_REMATCH[2]}"
        DB_HOST="${BASH_REMATCH[4]}"
        DB_PORT="${BASH_REMATCH[5]:-5432}"
        DB_NAME="${BASH_REMATCH[6]}"
        echo "   Host: $DB_HOST"
        echo "   Port: $DB_PORT"
        echo "   Database: $DB_NAME"
        echo "   User: $(mask_sensitive "$DB_USER")"
    fi
elif [ -n "$JDBC_DATABASE_URL" ]; then
    echo "✅ JDBC_DATABASE_URL is set"
    # Parse JDBC URL
    if [[ $JDBC_DATABASE_URL =~ jdbc:postgresql://([^:/]+):?([0-9]*)/([^?]+) ]]; then
        echo "   Host: ${BASH_REMATCH[1]}"
        echo "   Port: ${BASH_REMATCH[2]:-5432}"
        echo "   Database: ${BASH_REMATCH[3]}"
    fi
else
    echo "❌ No database URL found (DATABASE_URL or JDBC_DATABASE_URL)"
fi

# Check messaging services
echo ""
echo "4. Messaging Services:"
echo "----------------------"
services_configured=0

if [ -n "$GROUP_ME_BOT_ID" ]; then
    echo "✅ GroupMe is configured (ID: $(mask_sensitive "$GROUP_ME_BOT_ID"))"
    ((services_configured++))
else
    echo "⚠️  GroupMe not configured"
fi

if [ -n "$DISCORD_WEBHOOK_URL" ]; then
    echo "✅ Discord is configured"
    ((services_configured++))
else
    echo "⚠️  Discord not configured"
fi

if [ -n "$SLACK_WEBHOOK_URL" ]; then
    echo "✅ Slack is configured"
    ((services_configured++))
else
    echo "⚠️  Slack not configured"
fi

if [ $services_configured -eq 0 ]; then
    echo ""
    echo "❌ WARNING: No messaging services configured!"
    echo "   The bot won't be able to send notifications"
fi

# Check optional services
echo ""
echo "5. Optional Services:"
echo "---------------------"
if [ -n "$OPENAI_API_KEY" ]; then
    echo "✅ OpenAI is configured (for Schefter tweets)"
else
    echo "⚠️  OpenAI not configured (Schefter tweets disabled)"
fi

# Port configuration
echo ""
echo "6. Port Configuration:"
echo "----------------------"
if [ -n "$PORT" ]; then
    echo "✅ PORT is set: $PORT"
else
    echo "⚠️  PORT not set (will use default 8080)"
fi

# Create SQL diagnostic queries
echo ""
echo "7. Database Diagnostic Queries:"
echo "-------------------------------"
echo "Copy and run these in your Railway Postgres dashboard:"
echo ""

cat << 'EOF'
-- Check if tables exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Check OAuth token status
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN 'No OAuth token found'
        WHEN (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000 THEN 'Token is valid'
        ELSE 'Token is expired'
    END as token_status,
    CASE 
        WHEN COUNT(*) > 0 THEN 
            'Expires: ' || to_timestamp((retrieved + expires_in * 1000)/1000)::text
        ELSE 'No token'
    END as expiry
FROM token 
WHERE retrieved = (SELECT MAX(retrieved) FROM token);

-- Check last transaction check
SELECT 
    CASE 
        WHEN time = -1 THEN 'Error state'
        ELSE 'Last checked: ' || to_timestamp(time/1000)::text
    END as last_check
FROM latest_time_checked 
ORDER BY time DESC 
LIMIT 1;

-- Check recent messages (last 24h)
SELECT 
    COUNT(*) as total_messages,
    COUNT(CASE WHEN success = true THEN 1 END) as successful,
    COUNT(CASE WHEN success = false THEN 1 END) as failed
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000);

-- Check recent errors
SELECT 
    to_timestamp(timestamp/1000) as error_time,
    messaging_service,
    error_message
FROM message_history
WHERE success = false
  AND timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000)
ORDER BY timestamp DESC
LIMIT 5;
EOF

echo ""
echo "8. Health Check Endpoints:"
echo "--------------------------"
echo "If your backend is running, check these endpoints:"
echo ""
if [ -n "$RAILWAY_STATIC_URL" ]; then
    echo "Main page: https://$RAILWAY_STATIC_URL"
    echo "Health check: https://$RAILWAY_STATIC_URL/health"
else
    echo "Backend URL not detected. Check Railway dashboard for your app URL"
fi

echo ""
echo "9. Common Production Issues:"
echo "----------------------------"
echo ""
echo "Issue: 'No OAuth token found'"
echo "Solution: Visit your Railway app URL and authenticate with Yahoo"
echo ""
echo "Issue: 'Token is expired'"
echo "Solution: The bot should auto-refresh. If not, re-authenticate"
echo ""
echo "Issue: No transactions processing"
echo "Check: 1) OAuth token is valid"
echo "       2) YAHOO_GAME_KEY matches current season"
echo "       3) YAHOO_LEAGUE_ID is correct"
echo "       4) Check 'latest_time_checked' isn't -1"
echo ""
echo "Issue: Messages not sending"
echo "Check: 1) Messaging service credentials are correct"
echo "       2) Check message_history table for error messages"
echo ""

echo "=== Diagnostics Complete ==="
