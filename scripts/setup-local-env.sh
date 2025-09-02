#!/bin/bash

# Setup Local Development Environment
# This script helps configure your local .env file for development

echo "=== Yahoo Fantasy Bot Local Environment Setup ==="
echo ""

# Check if .env exists
if [ -f .env ]; then
    echo "Found existing .env file"
    echo ""
    
    # Check current database URL
    current_db_url=$(grep "^JDBC_DATABASE_URL=" .env | cut -d'=' -f2-)
    
    if [[ "$current_db_url" == *"{{"*"}}"* ]]; then
        echo "❌ Your JDBC_DATABASE_URL contains Railway template variables:"
        echo "   $current_db_url"
        echo ""
        echo "For local development, you need to set an actual database URL."
        echo ""
        
        # Offer to update it
        read -p "Would you like to update it for local development? (y/n) " -n 1 -r
        echo ""
        
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo ""
            echo "Enter your local database URL"
            echo "Default: jdbc:postgresql://localhost:5432/yahoo_fantasy_bot?user=postgres&password=postgres"
            read -p "Database URL (press Enter for default): " db_url
            
            if [ -z "$db_url" ]; then
                db_url="jdbc:postgresql://localhost:5432/yahoo_fantasy_bot?user=postgres&password=postgres"
            fi
            
            # Create backup
            cp .env .env.backup
            
            # Update the file
            if [[ "$OSTYPE" == "darwin"* ]]; then
                # macOS
                sed -i '' "s|^JDBC_DATABASE_URL=.*|JDBC_DATABASE_URL=$db_url|" .env
            else
                # Linux
                sed -i "s|^JDBC_DATABASE_URL=.*|JDBC_DATABASE_URL=$db_url|" .env
            fi
            
            echo "✅ Updated JDBC_DATABASE_URL in .env"
            echo "   Backup saved as .env.backup"
        fi
    else
        echo "✅ JDBC_DATABASE_URL is already configured for local use"
    fi
else
    echo "❌ No .env file found!"
    echo "Creating from env.example..."
    cp env.example .env
    echo "✅ Created .env file"
    echo ""
    echo "Please edit .env and add your Yahoo API credentials and database URL"
fi

echo ""
echo "=== Current Environment Status ==="

# Source the updated env
set -a
source .env
set +a

# Check each variable
echo ""
echo "Yahoo API Configuration:"
[ -n "$YAHOO_CLIENT_ID" ] && [ "$YAHOO_CLIENT_ID" != "your_yahoo_client_id_here" ] && echo "✅ YAHOO_CLIENT_ID is set" || echo "❌ YAHOO_CLIENT_ID needs to be configured"
[ -n "$YAHOO_CLIENT_SECRET" ] && [ "$YAHOO_CLIENT_SECRET" != "your_yahoo_client_secret_here" ] && echo "✅ YAHOO_CLIENT_SECRET is set" || echo "❌ YAHOO_CLIENT_SECRET needs to be configured"
[ -n "$YAHOO_GAME_KEY" ] && echo "✅ YAHOO_GAME_KEY is set to: $YAHOO_GAME_KEY" || echo "❌ YAHOO_GAME_KEY needs to be configured"
[ -n "$YAHOO_LEAGUE_ID" ] && [ "$YAHOO_LEAGUE_ID" != "your_league_id_here" ] && echo "✅ YAHOO_LEAGUE_ID is set" || echo "❌ YAHOO_LEAGUE_ID needs to be configured"

echo ""
echo "Database Configuration:"
if [[ "$JDBC_DATABASE_URL" == *"{{"*"}}"* ]]; then
    echo "❌ JDBC_DATABASE_URL contains template variables (not suitable for local dev)"
else
    [ -n "$JDBC_DATABASE_URL" ] && echo "✅ JDBC_DATABASE_URL is configured" || echo "❌ JDBC_DATABASE_URL needs to be configured"
fi

echo ""
echo "Optional Services:"
[ -n "$GROUP_ME_BOT_ID" ] && echo "✅ GroupMe is configured" || echo "⚠️  GroupMe not configured (optional)"
[ -n "$DISCORD_WEBHOOK_URL" ] && echo "✅ Discord is configured" || echo "⚠️  Discord not configured (optional)"
[ -n "$SLACK_WEBHOOK_URL" ] && echo "✅ Slack is configured" || echo "⚠️  Slack not configured (optional)"
[ -n "$OPENAI_API_KEY" ] && echo "✅ OpenAI is configured" || echo "⚠️  OpenAI not configured (optional)"

echo ""
echo "=== Next Steps ==="
echo ""
echo "1. Make sure PostgreSQL is running locally"
echo "2. Create the database if needed:"
echo "   createdb yahoo_fantasy_bot"
echo "3. Run the connection test:"
echo "   ./scripts/test-yahoo-connection.sh"
echo "4. Start the backend and authenticate with Yahoo:"
echo "   ./gradlew :backend:run"
echo "5. Start the bot:"
echo "   ./gradlew :bot:run"
