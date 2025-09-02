# Production Monitoring Guide

This guide helps you monitor and troubleshoot your Yahoo Fantasy Bot in production.

## Quick Start

### 1. Check Production Health

Once deployed to Railway, check your bot's health:

```bash
# Using the script
./scripts/check-production.sh https://your-app.railway.app

# Or directly with curl
curl https://your-app.railway.app/health | python3 -m json.tool
```

### 2. Health Endpoints

- **`/health`** - Comprehensive health check (JSON)
- **`/ping`** - Simple uptime check (returns "pong")

## Understanding the Health Check

The `/health` endpoint returns:

```json
{
  "status": "ok",
  "timestamp": "2024-01-15 10:30:45",
  "environment": {
    "yahooClientId": true,
    "yahooClientSecret": true,
    "yahooGameKey": "423",
    "yahooLeagueId": "123456",
    "railwayEnvironment": "production"
  },
  "database": {
    "connected": true,
    "tablesExist": true
  },
  "oauth": {
    "hasToken": true,
    "isValid": true,
    "expiresAt": "2024-01-15 12:30:45"
  },
  "messaging": {
    "groupMe": false,
    "discord": true,
    "slack": false,
    "totalConfigured": 1
  },
  "lastActivity": {
    "lastTransactionCheck": "2024-01-15 10:29:30",
    "recentMessages24h": 15,
    "recentErrors24h": 0
  }
}
```

## What to Look For

### ✅ Healthy Status
- `oauth.isValid` = true
- `database.connected` = true
- `messaging.totalConfigured` > 0
- `lastActivity.lastTransactionCheck` updated recently (within 30 seconds)

### ❌ Problem Indicators
- `oauth.hasToken` = false → Need to authenticate
- `oauth.isValid` = false → Token expired
- `messaging.totalConfigured` = 0 → No messaging services configured
- `lastActivity.recentErrors24h` > 0 → Check error details

## Enhanced Logging

The bot now includes detailed logging to help diagnose issues:

### Log Patterns to Watch

```
[Arbiter] Running transaction check cycle...
[Arbiter] Fetching transactions from Yahoo API...
[DataRetriever] Response code: 200
[Arbiter] Total transactions in response: 15
[TransactionTransformer] Found 15 transactions in document
[TransactionTransformer] New transaction found - Type: add
```

### Error Patterns

```
[DataRetriever] ERROR: Non-200 response code
[Arbiter] ERROR during transaction check: <error message>
```

## Railway Dashboard Monitoring

### 1. View Logs

In Railway dashboard:
1. Select your service
2. Click "Logs" tab
3. Filter by "[Arbiter]", "[DataRetriever]", or "[TransactionTransformer]"

### 2. Database Queries

Access Railway's Postgres dashboard and run:

```sql
-- Check OAuth token
SELECT 
    CASE 
        WHEN (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000 
        THEN 'Valid until ' || to_timestamp((retrieved + expires_in * 1000)/1000)::text
        ELSE 'EXPIRED'
    END as token_status
FROM token ORDER BY retrieved DESC LIMIT 1;

-- Check last activity
SELECT 
    to_timestamp(time/1000) as last_check,
    NOW() - to_timestamp(time/1000) as time_since_check
FROM latest_time_checked ORDER BY time DESC LIMIT 1;

-- Recent messages
SELECT 
    COUNT(*) as total,
    COUNT(CASE WHEN success = true THEN 1 END) as sent,
    COUNT(CASE WHEN success = false THEN 1 END) as failed
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000);
```

## Troubleshooting Common Issues

### Issue: No OAuth Token

**Symptoms:**
- Health check shows `oauth.hasToken = false`
- Logs show "There is currently no token data in the database"

**Solution:**
1. Visit `https://your-app.railway.app`
2. Click "Login with Yahoo"
3. Complete authentication

### Issue: Token Expired

**Symptoms:**
- Health check shows `oauth.isValid = false`
- API calls fail with 401 errors

**Solution:**
- Bot should auto-refresh, but if not:
- Re-authenticate via the web interface

### Issue: No Transactions Processing

**Symptoms:**
- Health check shows old `lastTransactionCheck`
- No new messages despite league activity

**Debug Steps:**
1. Check logs for error messages
2. Verify `YAHOO_GAME_KEY` matches current season (423 for NFL)
3. Check `latest_time_checked` table for -1 (error state)
4. Verify league ID is correct

### Issue: Messages Not Sending

**Symptoms:**
- Logs show transactions found but no messages sent
- `recentErrors24h` > 0 in health check

**Check:**
1. Messaging service credentials in Railway
2. Error details in `message_history` table
3. Webhook URLs are valid and accessible

## Setting Up Monitoring

### 1. External Monitoring

Use services like UptimeRobot to monitor:
- URL: `https://your-app.railway.app/ping`
- Expected response: `pong`
- Check interval: 5 minutes

### 2. Health Check Monitoring

Create a scheduled job to check `/health` and alert on:
- `oauth.isValid = false`
- `database.connected = false`
- `lastActivity.lastTransactionCheck` older than 5 minutes

### 3. Log Alerts

In Railway, set up alerts for:
- "[ERROR]" in logs
- "Non-200 response code"
- "Exception" or "Failed"

## Maintenance

### Clean Up Old Data

Periodically run in Postgres:

```sql
-- Delete old message history (keep 30 days)
DELETE FROM message_history 
WHERE timestamp < (EXTRACT(EPOCH FROM NOW() - INTERVAL '30 days') * 1000);

-- Keep only recent tokens
DELETE FROM token 
WHERE retrieved NOT IN (
    SELECT retrieved FROM token 
    ORDER BY retrieved DESC LIMIT 5
);
```

## Quick Reference

### Check Production Status
```bash
./scripts/check-production.sh https://your-app.railway.app
```

### View Recent Errors
```sql
SELECT * FROM message_history 
WHERE success = false 
ORDER BY timestamp DESC LIMIT 10;
```

### Force Transaction Check
```sql
-- Set last check to 1 hour ago
UPDATE latest_time_checked 
SET time = (EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000)::bigint;
```

### Railway CLI Commands
```bash
# View logs
railway logs

# Check environment variables
railway variables

# Restart service
railway restart
```