# Railway Migration Guide

## Prerequisites

1. Create a Railway account at [railway.app](https://railway.app)
2. Install Railway CLI: `npm install -g @railway/cli`
3. Have your Yahoo API credentials ready

## Deployment Steps

### 1. Initial Setup

```bash
# Login to Railway
railway login

# Create a new project
railway create yahoo-fantasy-bot

# Link to your project
railway link
```

### 2. Add PostgreSQL Database

```bash
# Add PostgreSQL service
railway add postgresql
```

This will automatically create a PostgreSQL database and set the `DATABASE_URL` environment variable.

### 3. Configure Environment Variables

Set these environment variables in Railway dashboard or via CLI:

```bash
# Required Yahoo API credentials
railway variables set YAHOO_CLIENT_ID="your_yahoo_client_id"
railway variables set YAHOO_CLIENT_SECRET="your_yahoo_client_secret" 
railway variables set YAHOO_GAME_KEY="your_game_key"
railway variables set YAHOO_LEAGUE_ID="your_league_id"

# Optional messaging service credentials (set as needed)
railway variables set GROUP_ME_BOT_ID="your_groupme_bot_id"
railway variables set DISCORD_WEBHOOK_URL="your_discord_webhook_url"
railway variables set SLACK_WEBHOOK_URL="your_slack_webhook_url"

# Railway will automatically set DATABASE_URL for PostgreSQL
```

### 4. Deploy

```bash
# Deploy the application
railway up
```

### 5. Create Worker Service

Railway needs separate services for web and worker processes:

1. In Railway dashboard, go to your project
2. Click "New Service" → "Empty Service"
3. Connect it to the same GitHub repo
4. Set the start command to: `java -jar build/libs/bot.jar`
5. Copy all environment variables from the web service

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `YAHOO_CLIENT_ID` | Yes | Yahoo API Client ID |
| `YAHOO_CLIENT_SECRET` | Yes | Yahoo API Client Secret |
| `YAHOO_GAME_KEY` | Yes | Yahoo Game Key (NFL=423, NBA=428, MLB=431) |
| `YAHOO_LEAGUE_ID` | Yes | Your Yahoo Fantasy League ID |
| `GROUP_ME_BOT_ID` | No | GroupMe Bot ID for notifications |
| `DISCORD_WEBHOOK_URL` | No | Discord Webhook URL for notifications |
| `SLACK_WEBHOOK_URL` | No | Slack Webhook URL for notifications |
| `DATABASE_URL` | Auto | PostgreSQL connection string (auto-set by Railway) |
| `PORT` | Auto | Port for web service (auto-set by Railway) |

## Post-Deployment

1. **Update Yahoo OAuth Redirect URL**:
   - Go to [Yahoo Developer Console](https://developer.yahoo.com/apps/)
   - Update redirect URI to: `https://your-railway-app.railway.app/auth`

2. **Test the Application**:
   - Visit your Railway app URL
   - Complete Yahoo OAuth authentication
   - Configure alerts and messaging services

3. **Monitor Logs**:
   ```bash
   # View web service logs
   railway logs
   
   # View worker service logs (if separate)
   railway logs --service worker
   ```

## Troubleshooting

### Build Issues
- Ensure Java 17 is being used
- Check that `gradlew` is executable
- Verify all dependencies are properly resolved

### Runtime Issues
- Check environment variables are set correctly
- Verify PostgreSQL connection
- Ensure OAuth redirect URL matches Railway domain

### Database Issues
- Railway PostgreSQL should auto-connect via `DATABASE_URL`
- Check connection pool settings if needed
- Monitor database logs in Railway dashboard

## Cost Estimation

- **Web Service**: ~$5/month (512MB RAM, 1 vCPU)
- **Worker Service**: ~$5/month (512MB RAM, 1 vCPU) 
- **PostgreSQL**: Included in Railway Pro plan
- **Total**: ~$10/month

## Migration from Heroku

1. Export your Heroku environment variables:
   ```bash
   heroku config -a your-heroku-app > .env.backup
   ```

2. Export your database (if needed):
   ```bash
   heroku pg:backups:capture -a your-heroku-app
   heroku pg:backups:download -a your-heroku-app
   ```

3. Import data to Railway PostgreSQL if needed

4. Update DNS/domain settings to point to Railway
