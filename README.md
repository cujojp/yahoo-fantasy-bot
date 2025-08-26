# Yahoo Fantasy Bot

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/template/railway-template)

**🚀 Migrated from Heroku to Railway for better performance and cost-effectiveness!**

A powerful automation bot that sends real-time alerts about Yahoo Fantasy Football league activities to GroupMe, Slack, and Discord. Built with Kotlin and designed for easy deployment on Railway with PostgreSQL.

![Yahoo Fantasy Bot Screenshot](https://i.imgur.com/1Ol63Al.png)

## ✨ Features

### 🏈 League Transaction Alerts
- **ADD** - Player additions to rosters
- **DROP** - Player releases from rosters  
- **ADD/DROP** - Combined roster moves
- **TRADE** - Player trades between teams
- **COMMISH CHANGES** - Commissioner actions

### 🎛️ Customization
- Configure alerts with custom schedules
- Multiple messaging service support
- Web interface for easy configuration
- Real-time notifications

### 🏗️ Architecture
- **Backend**: Ktor web server with REST API
- **Bot**: Scheduled job processor for Yahoo API integration
- **Frontend**: React web interface for configuration
- **Database**: PostgreSQL for data persistence
- **Deployment**: Railway with Docker support

## 📋 Current Roadmap

- [x] Kotlin implementation
- [x] Reactive X integration
- [x] Docker support (local development)
- [x] Railway deployment
- [x] In-depth message customization
- [x] Web interface for configuration
- [ ] Chat command responses
- [ ] Code cleanup and optimization
- [ ] Mobile app companion

## 🚀 Quick Start

Choose your deployment method:

### Option 1: Railway Deployment (Recommended)
**Perfect for production use**

1. Click the **Deploy on Railway** button above
2. Follow the detailed guide: **[📖 Railway Setup Guide](RAILWAY_SETUP.md)**
3. Configure your Yahoo API credentials
4. Set up messaging services (optional)

### Option 2: Local Development
**Perfect for testing and development**

1. Follow the comprehensive guide: **[💻 Local Development Guide](LOCAL_DEVELOPMENT.md)**
2. Quick setup: `npm run dev:setup`
3. Configure your `.env` file
4. Start services: `npm run dev:backend`

## 📚 Documentation

| Guide | Description | Best For |
|-------|-------------|----------|
| **[🚀 Railway Setup](RAILWAY_SETUP.md)** | Complete Railway deployment guide | Production deployment |
| **[💻 Local Development](LOCAL_DEVELOPMENT.md)** | Local environment setup | Development & testing |
| **[📋 Migration Checklist](MIGRATION_CHECKLIST.md)** | Heroku to Railway migration | Existing users |

## 🔑 Yahoo API Setup

Before using the bot, you'll need Yahoo Fantasy Sports API credentials:

### 1. Create Yahoo Developer App

1. Go to [Yahoo Developer Console](https://developer.yahoo.com/apps/)
2. Click **"Create an App"**

![Create App](https://imgur.com/VDgZ1Ze.png)

### 2. Configure Your App

- **Application Name**: Choose any name
- **Application Type**: Select "Installed Application"
- **Redirect URI**: `https://<your-app-name>.railway.app/auth`
- **Permissions**: Check "Fantasy Sports" → "Read"
- Click **"Create App"**

![App Configuration](https://imgur.com/VqctUfM.png)

### 3. Get Your Credentials

Save these for later configuration:
- **Yahoo Client ID**
- **Yahoo Client Secret**

![Credentials](https://imgur.com/NbUwOmD.png)

### 4. Find Your League ID

**Method 1: Website**
1. Go to Yahoo Fantasy Football
2. Click your league → Settings
3. Find "League ID" at the top

**Method 2: Mobile App**
1. Open Yahoo Fantasy app
2. League tab → Settings
3. Find "League ID#" at the top

## 🔗 Messaging Services Setup

Configure one or more messaging services (all optional):

<details>
<summary>📱 GroupMe Setup</summary>

1. Go to [GroupMe](https://www.groupme.com) and login
2. Create a group chat for your league (if needed)
3. Visit [GroupMe Developer](https://dev.groupme.com/session/new)
4. Click **"Create Bot"**
5. Fill out bot details and select your group
6. Save the **Bot ID** for configuration

![GroupMe Bot](https://i.imgur.com/k65EZFJ.png)

</details>

<details>
<summary>💬 Slack Setup</summary>

1. Go to your [Slack workspace](https://slack.com/signin)
2. Create a league channel (if needed)
3. Visit [Slack API](https://api.slack.com/apps/new)
4. Create new app for your workspace
5. Enable **Incoming Webhooks**
6. Add webhook to your channel
7. Save the **Webhook URL**

![Slack Webhook](https://i.imgur.com/mmzhDS0.png)

</details>

<details>
<summary>🎮 Discord Setup</summary>

1. Open your Discord server
2. Server Settings → Webhooks
3. Create new webhook
4. Choose channel and name
5. Save the **Webhook URL**

![Discord Webhook](https://i.imgur.com/U4MKZSY.png)

</details>

## 🛠️ Development

### Prerequisites
- Java 17+
- Node.js 16+
- Docker & Docker Compose
- Yahoo API credentials

### Quick Development Setup
```bash
# Clone and setup
git clone <your-repo>
cd yahoo-fantasy-bot

# Start local environment
npm run dev:setup

# Edit environment variables
# cp env.example .env
# (Edit .env with your credentials)

# Build and start
npm run dev:build
npm run dev:backend
```

Visit http://localhost:8080 for the web interface.

### Available Commands
```bash
npm run dev:setup          # Complete local setup
npm run dev:build          # Build all services
npm run dev:backend        # Start web service
npm run dev:bot            # Start bot service
npm run dev:frontend       # Frontend dev server
npm run dev:db:start       # Start database
npm run dev:db:stop        # Stop database
```

## 🔧 Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `YAHOO_CLIENT_ID` | ✅ | Yahoo API Client ID | `dj0yJmk9...` |
| `YAHOO_CLIENT_SECRET` | ✅ | Yahoo API Client Secret | `abcd1234...` |
| `YAHOO_LEAGUE_ID` | ✅ | Your Fantasy League ID | `123456` |
| `YAHOO_GAME_KEY` | ✅ | Sport key (NFL=423) | `423` |
| `JDBC_DATABASE_URL` | ✅ | PostgreSQL connection | Auto-set by Railway |
| `GROUP_ME_BOT_ID` | ❌ | GroupMe Bot ID | `abc123...` |
| `DISCORD_WEBHOOK_URL` | ❌ | Discord webhook | `https://discord.com/api/webhooks/...` |
| `SLACK_WEBHOOK_URL` | ❌ | Slack webhook | `https://hooks.slack.com/services/...` |
| `PORT` | ❌ | Web server port | `8080` |

### Yahoo Game Keys
- **NFL**: 423
- **NBA**: 428
- **MLB**: 431
- **NHL**: 427

## 🐛 Troubleshooting

### Common Issues

**Database Connection Failed**
- Verify `JDBC_DATABASE_URL` is correct
- Check PostgreSQL service status
- Review Railway service logs

**Yahoo API Errors**
- Verify Client ID and Secret
- Check redirect URI matches exactly
- Ensure Fantasy Sports permissions enabled

**No Notifications**
- Verify at least one messaging service is configured
- Check webhook URLs are valid
- Review bot service logs

**Web Interface Issues**
- Clear browser cache
- Check if PORT environment variable is set
- Verify frontend build completed successfully

### Getting Help

1. Check the [Local Development Guide](LOCAL_DEVELOPMENT.md) for detailed troubleshooting
2. Review the [Railway Setup Guide](RAILWAY_SETUP.md) for deployment issues
3. Open an issue on GitHub with logs and error messages

## 🤝 Contributing

We welcome contributions! Please feel free to:

1. Report bugs and issues
2. Suggest new features
3. Submit pull requests
4. Improve documentation

### Development Workflow
1. Fork the repository
2. Create a feature branch
3. Follow the [Local Development Guide](LOCAL_DEVELOPMENT.md)
4. Test your changes locally
5. Submit a pull request

## 📄 License

```
MIT License

Copyright (c) 2018 Landon Patmore

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

**⚠️ Note**: Safari v3.0.0 compatibility issues with the frontend interface. Please use Chrome, Firefox, or newer Safari versions.

**📢 Auto-deploys**: Manual deployment required. Click "Deploy" in Railway dashboard to get latest updates. Follow setup steps again after deployment.