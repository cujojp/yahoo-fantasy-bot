#!/bin/bash

# Script to run the backend with HTTPS enabled

# Load environment variables from .env file
if [ -f .env ]; then
    echo "📝 Loading environment variables from .env file..."
    export $(grep -v '^#' .env | xargs)
else
    echo "❌ .env file not found. Please create one from env.example"
    echo "   cp env.example .env"
    echo "   Then edit .env with your Yahoo API credentials"
    exit 1
fi

# Check required environment variables
if [ -z "$YAHOO_CLIENT_ID" ] || [ -z "$YAHOO_CLIENT_SECRET" ] || [ -z "$YAHOO_LEAGUE_ID" ]; then
    echo "❌ Missing required environment variables!"
    echo "   Please set YAHOO_CLIENT_ID, YAHOO_CLIENT_SECRET, and YAHOO_LEAGUE_ID in .env file"
    exit 1
fi

# Check if certificates exist
if [ ! -f "certificates/keystore.p12" ]; then
    echo "❌ SSL certificate not found!"
    echo "   Please run: npm run dev:https:setup first"
    exit 1
fi

echo "🔒 Starting Yahoo Fantasy Bot Backend with HTTPS support..."
echo "   HTTP Port: ${PORT:-8080}"
echo "   HTTPS Port: 8443"
echo "   Database: $JDBC_DATABASE_URL"
echo ""

# Run the HTTPS-enabled backend
java -cp "backend/build/libs/backend.jar" com.landonpatmore.yahoofantasybot.backend.HttpsApplicationKt
