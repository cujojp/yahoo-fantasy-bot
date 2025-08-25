#!/bin/bash

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

echo "🤖 Starting Yahoo Fantasy Bot Service..."
echo "   Database: $JDBC_DATABASE_URL"
echo ""

# Run the bot JAR
java -jar build/libs/bot.jar
