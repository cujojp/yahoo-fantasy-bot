# Local Development Setup Guide

This guide will help you set up the Yahoo Fantasy Bot for local development and testing.

## Prerequisites

- **Java 17** or higher
- **Node.js 16** or higher
- **Docker** and **Docker Compose**
- **Yahoo Developer Account** with API credentials

## Quick Setup

### 1. Initial Setup
```bash
# Run the automated setup script
npm run dev:setup
```

This script will:
- Create a `.env` file from the example
- Start a local PostgreSQL database
- Verify everything is working

### 2. Configure Environment Variables

Edit the `.env` file with your Yahoo API credentials:

```bash
# Required Yahoo API credentials
YAHOO_CLIENT_ID=your_yahoo_client_id_here
YAHOO_CLIENT_SECRET=your_yahoo_client_secret_here
YAHOO_LEAGUE_ID=your_league_id_here
```

#### Getting Yahoo API Credentials

1. Go to [Yahoo Developer Console](https://developer.yahoo.com/apps/)
2. Create a new app or use an existing one
3. Note your **Client ID** and **Client Secret**
4. Find your **League ID** from your Yahoo Fantasy league URL

#### Finding Your League ID

Your Yahoo Fantasy league URL looks like:
```
https://football.fantasysports.yahoo.com/f1/123456/...
```
The `123456` part is your League ID.

### 3. Build the Application

```bash
# Build all services
npm run dev:build
```

### 4. Start Services

#### Option A: Start Web Service Only
```bash
# Start the backend web service (includes frontend)
npm run dev:backend
```

Visit http://localhost:8080 to access the web interface.

#### Option B: Start Both Services
```bash
# Terminal 1: Start web service
npm run dev:backend

# Terminal 2: Start bot service (in a new terminal)
npm run dev:bot
```

## Available Scripts

| Command | Description |
|---------|-------------|
| `npm run dev:setup` | Initial setup (database + env) |
| `npm run dev:build` | Build all services (fast, skip tests) |
| `npm run dev:build-full` | Build all services (with tests) |
| `npm run dev:backend` | Start web service |
| `npm run dev:bot` | Start bot service |
| `npm run dev:frontend` | Start frontend in development mode |
| `npm run dev:db:start` | Start PostgreSQL database |
| `npm run dev:db:stop` | Stop PostgreSQL database |
| `npm run dev:db:logs` | View database logs |

## Architecture

### Services

1. **Backend** (`backend.jar`)
   - Ktor web server
   - REST API endpoints
   - Serves React frontend
   - Port: 8080

2. **Bot** (`bot.jar`)
   - Scheduled jobs
   - Yahoo API integration
   - Messaging services (Discord, Slack, GroupMe)

3. **Frontend** (React App)
   - Web interface for configuration
   - Embedded in backend JAR
   - Development server: 3000

### Database

- **PostgreSQL** running in Docker
- Connection: `localhost:5432`
- Database: `yahoo_fantasy_bot`
- User: `dev_user` / Password: `dev_password`

## Development Workflow

### 1. Frontend Development
```bash
# Start frontend development server
npm run dev:frontend

# Backend must be running for API calls
npm run dev:backend
```

Frontend dev server (port 3000) will proxy API calls to backend (port 8080).

### 2. Backend Development
```bash
# After making changes to backend code
npm run dev:build
npm run dev:backend
```

### 3. Bot Development
```bash
# After making changes to bot code
npm run dev:build
npm run dev:bot
```

## Environment Variables Reference

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `YAHOO_CLIENT_ID` | Yahoo API Client ID | `dj0yJmk9...` |
| `YAHOO_CLIENT_SECRET` | Yahoo API Client Secret | `abcd1234...` |
| `YAHOO_LEAGUE_ID` | Your Fantasy League ID | `123456` |
| `YAHOO_GAME_KEY` | Game type key | `423` (NFL) |
| `JDBC_DATABASE_URL` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/...` |

### Optional Variables

| Variable | Description |
|----------|-------------|
| `GROUP_ME_BOT_ID` | GroupMe Bot ID for notifications |
| `DISCORD_WEBHOOK_URL` | Discord webhook for notifications |
| `SLACK_WEBHOOK_URL` | Slack webhook for notifications |
| `PORT` | Web server port (default: 8080) |

### Yahoo Game Keys

| Sport | Game Key |
|-------|----------|
| NFL | 423 |
| NBA | 428 |
| MLB | 431 |
| NHL | 427 |

## Troubleshooting

### Database Connection Issues
```bash
# Check if database is running
docker-compose ps

# View database logs
npm run dev:db:logs

# Restart database
npm run dev:db:stop
npm run dev:db:start
```

### Build Issues
```bash
# Clean and rebuild
./gradlew clean
npm run dev:build
```

### Application Errors
```bash
# Check if environment variables are set
cat .env

# Verify Yahoo API credentials are correct
# Check Yahoo Developer Console for any issues
```

### Port Already in Use
```bash
# Find what's using port 8080
lsof -i :8080

# Kill the process or change PORT in .env
PORT=8081
```

## Database Access

Connect to the local PostgreSQL database:

```bash
# Using Docker exec
docker-compose exec postgres psql -U dev_user -d yahoo_fantasy_bot

# Using external client
# Host: localhost
# Port: 5432
# Database: yahoo_fantasy_bot
# User: dev_user
# Password: dev_password
```

## Testing

### Unit Tests
```bash
# Run tests
npm run dev:build-full
```

### Manual Testing
1. Start the web service: `npm run dev:backend`
2. Visit http://localhost:8080
3. Configure your league settings through the web interface
4. Start the bot service: `npm run dev:bot`
5. Monitor logs for any errors

## Production Deployment

When ready to deploy to Railway:
1. Commit your changes
2. Push to your Git repository
3. Use the Railway setup guide in `RAILWAY_SETUP.md`

The local development environment closely mirrors the production setup on Railway.
