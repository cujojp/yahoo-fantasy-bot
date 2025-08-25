#!/bin/bash

# Development Setup Script for Yahoo Fantasy Bot
echo "🏈 Setting up Yahoo Fantasy Bot Local Development Environment"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    echo "   Visit: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Create .env file from example if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from example..."
    cp env.example .env
    echo "⚠️  Please edit .env file and add your Yahoo API credentials before running the application!"
    echo "   Required: YAHOO_CLIENT_ID, YAHOO_CLIENT_SECRET, YAHOO_LEAGUE_ID"
else
    echo "✅ .env file already exists"
fi

# Start PostgreSQL database
echo "🐘 Starting PostgreSQL database..."
docker-compose up -d postgres

# Wait for database to be ready
echo "⏳ Waiting for database to be ready..."
sleep 10

# Check if database is ready
if docker-compose exec postgres pg_isready -U dev_user -d yahoo_fantasy_bot; then
    echo "✅ Database is ready!"
else
    echo "❌ Database failed to start. Check Docker logs: docker-compose logs postgres"
    exit 1
fi

echo ""
echo "🎉 Local development environment is ready!"
echo ""
echo "Next steps:"
echo "1. Edit .env file with your Yahoo API credentials"
echo "2. Run 'npm run dev:backend' to start the web service"
echo "3. Run 'npm run dev:bot' to start the bot service (optional)"
echo "4. Visit http://localhost:8080 to access the web interface"
echo ""
echo "To stop the database: docker-compose down"
