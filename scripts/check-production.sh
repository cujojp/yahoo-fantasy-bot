#!/bin/bash

# Production Health Check Script for Yahoo Fantasy Bot
# This script checks your production deployment status

echo "=== Yahoo Fantasy Bot Production Health Check ==="
echo "Generated at: $(date)"
echo ""

# Function to check health endpoint
check_health_endpoint() {
    local url=$1
    echo "Checking health endpoint: $url/health"
    echo ""
    
    # Try to fetch health endpoint
    response=$(curl -s -w "\n%{http_code}" "$url/health" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    json_response=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "200" ]; then
        echo "✅ Health endpoint is accessible"
        echo ""
        echo "Health Status:"
        echo "$json_response" | python3 -m json.tool 2>/dev/null || echo "$json_response"
    else
        echo "❌ Health endpoint returned HTTP $http_code"
        echo "Response: $json_response"
    fi
    
    echo ""
    echo "Checking ping endpoint: $url/ping"
    ping_response=$(curl -s -w "\n%{http_code}" "$url/ping" 2>/dev/null)
    ping_code=$(echo "$ping_response" | tail -n1)
    ping_body=$(echo "$ping_response" | head -n-1)
    
    if [ "$ping_code" = "200" ] && [ "$ping_body" = "pong" ]; then
        echo "✅ Ping endpoint is working"
    else
        echo "❌ Ping endpoint issue - HTTP $ping_code"
    fi
}

# Check if URL is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <your-railway-app-url>"
    echo "Example: $0 https://your-app.railway.app"
    echo ""
    echo "You can find your Railway URL in the Railway dashboard"
    exit 1
fi

APP_URL=$1

# Remove trailing slash if present
APP_URL=${APP_URL%/}

echo "Checking production deployment at: $APP_URL"
echo "================================================"
echo ""

# Check health endpoint
check_health_endpoint "$APP_URL"

echo ""
echo "=== Database Diagnostic Queries ==="
echo "Run these in your Railway Postgres dashboard:"
echo ""

cat << 'EOF'
-- 1. Check OAuth Token Status
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN 'NO TOKEN - Need to authenticate!'
        WHEN (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000 THEN 'Token VALID'
        ELSE 'Token EXPIRED - Re-authenticate needed'
    END as status,
    CASE 
        WHEN COUNT(*) > 0 THEN 
            'Expires: ' || to_timestamp((retrieved + expires_in * 1000)/1000)::text
        ELSE 'No token found'
    END as details
FROM token 
WHERE retrieved = (SELECT MAX(retrieved) FROM token);

-- 2. Check Last Transaction Check
SELECT 
    CASE 
        WHEN time = -1 THEN 'ERROR STATE'
        WHEN time IS NULL THEN 'Never checked'
        ELSE 'Last check: ' || to_timestamp(time/1000)::text || 
             ' (' || EXTRACT(EPOCH FROM NOW() - to_timestamp(time/1000))::int || ' seconds ago)'
    END as status
FROM latest_time_checked 
ORDER BY time DESC 
LIMIT 1;

-- 3. Recent Activity (last 24 hours)
SELECT 
    'Last 24h Activity' as period,
    COUNT(*) as total_messages,
    COUNT(CASE WHEN success = true THEN 1 END) as sent_successfully,
    COUNT(CASE WHEN success = false THEN 1 END) as failed,
    COUNT(DISTINCT messaging_service) as services_used
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000);

-- 4. Recent Errors (last 10)
SELECT 
    to_timestamp(timestamp/1000) as error_time,
    messaging_service,
    transaction_type,
    LEFT(error_message, 100) as error_preview
FROM message_history
WHERE success = false
ORDER BY timestamp DESC
LIMIT 10;

-- 5. Transaction Types Processed (last 7 days)
SELECT 
    transaction_type,
    COUNT(*) as count,
    MAX(to_timestamp(timestamp/1000)) as most_recent
FROM message_history
WHERE message_type = 'TRANSACTION'
  AND timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000)
GROUP BY transaction_type
ORDER BY count DESC;
EOF

echo ""
echo "=== Common Issues & Solutions ==="
echo ""
echo "1. 'NO TOKEN' Status:"
echo "   → Visit $APP_URL and click 'Login with Yahoo'"
echo ""
echo "2. 'Token EXPIRED' Status:"
echo "   → The bot should auto-refresh, but if not working:"
echo "   → Re-authenticate at $APP_URL"
echo ""
echo "3. No recent messages but transactions exist:"
echo "   → Check messaging service credentials in Railway"
echo "   → Look at error_message in failed message_history entries"
echo ""
echo "4. 'ERROR STATE' in last check:"
echo "   → Database has -1 timestamp (error condition)"
echo "   → Check Railway logs for exceptions"
echo ""
echo "5. Health endpoint not accessible:"
echo "   → Ensure backend is deployed and running"
echo "   → Check Railway deployment logs"
echo ""

echo "=== Quick Railway Commands ==="
echo ""
echo "View logs:"
echo "  railway logs"
echo ""
echo "Check environment variables:"
echo "  railway variables"
echo ""
echo "Restart service:"
echo "  railway restart"
echo ""

echo "=== Complete ===
