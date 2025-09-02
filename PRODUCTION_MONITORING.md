# Production Monitoring Guide for Yahoo Fantasy Bot

## Overview

This guide explains how to monitor and diagnose your Yahoo Fantasy Bot deployment in production (Railway).

## Quick Health Check

### 1. Health Endpoint

Once deployed, you can check your bot's health at:

```
https://YOUR-RAILWAY-URL/health
```

This endpoint returns JSON with:

- Environment variables status
- Database connection status
- OAuth token validity
- Messaging services configuration
- Recent activity statistics

### 2. Ping Endpoint

For simple uptime monitoring:

```
https://YOUR-RAILWAY-URL/ping
```

Returns `pong` if the service is up.

## Production Diagnostics Script

Run this script locally to check your production environment:

```bash
./scripts/production-diagnostics.sh
```

This will show:

- Environment variables (masked for security)
- Database configuration
- Messaging services status
- SQL queries to run in Railway's database dashboard

## Railway Dashboard Monitoring

### 1. Logs

In your Railway dashboard:

1. Go to your service
2. Click on "Logs" tab
3. Look for these key log entries:

**Successful operation:**

```
[Arbiter] Running transaction check cycle...
[DataRetriever] Response code: 200
[TransactionTransformer] New transaction found - Type: add
```

**Authentication issues:**

```
There is currently no token data in the database
[DataRetriever] ERROR: Non-200 response code
```

### 2. Database Queries

In Railway's Postgres dashboard, run these queries:

**Check OAuth token:**

```sql
SELECT
    CASE
        WHEN (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000
        THEN 'Valid'
        ELSE 'Expired'
    END as status,
    to_timestamp((retrieved + expires_in * 1000)/1000) as expires_at
FROM token
ORDER BY retrieved DESC
LIMIT 1;
```

**Check recent activity:**

```sql
-- Last transaction check
SELECT to_timestamp(time/1000) as last_check
FROM latest_time_checked
ORDER BY time DESC LIMIT 1;

-- Recent messages (last 24h)
SELECT
    messaging_service,
    COUNT(*) as total,
    COUNT(CASE WHEN success = true THEN 1 END) as sent,
    COUNT(CASE WHEN success = false THEN 1 END) as failed
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000)
GROUP BY messaging_service;

-- Recent errors
SELECT
    to_timestamp(timestamp/1000) as time,
    messaging_service,
    transaction_type,
    error_message
FROM message_history
WHERE success = false
ORDER BY timestamp DESC
LIMIT 10;
```

## Environment Variables Checklist

Ensure these are set in Railway:

### Required:

- [ ] `YAHOO_CLIENT_ID`
- [ ] `YAHOO_CLIENT_SECRET`
- [ ] `YAHOO_GAME_KEY` (423 for NFL, 428 for NBA, etc.)
- [ ] `YAHOO_LEAGUE_ID`
- [ ] `DATABASE_URL` (auto-set by Railway Postgres)

### At least one messaging service:

- [ ] `GROUP_ME_BOT_ID`
- [ ] `DISCORD_WEBHOOK_URL`
- [ ] `SLACK_WEBHOOK_URL`

### Optional:

- [ ] `OPENAI_API_KEY` (for Schefter tweets)
- [ ] `PORT` (Railway sets this automatically)

## Common Production Issues

### Issue: Bot deployed but no messages

**Check:**

1. Visit `/health` endpoint - is OAuth valid?
2. Are messaging services configured?
3. Check `latest_time_checked` table - is it updating?
4. Review logs for errors

### Issue: OAuth token expired

**Solution:**

1. Visit your Railway app URL
2. Click "Login with Yahoo"
3. Complete authentication
4. Bot should auto-refresh going forward

### Issue: Database connection errors

**Check:**

1. Is Postgres service attached in Railway?
2. Check Railway logs for connection errors
3. Verify `DATABASE_URL` is set

### Issue: Transactions not processing

**Debug steps:**

1. Check `/health` endpoint
2. Verify `YAHOO_GAME_KEY` matches current season
3. Check if `latest_time_checked.time` = -1 (error state)
4. Look for API errors in logs

## Monitoring Best Practices

### 1. Set up External Monitoring

Use services like:

- UptimeRobot
- Pingdom
- Better Uptime

Monitor: `https://YOUR-RAILWAY-URL/ping`

### 2. Regular Health Checks

Create a scheduled job to check `/health` endpoint and alert on issues.

### 3. Log Aggregation

Consider using Railway's log export features to:

- Archive logs
- Set up alerts for errors
- Track transaction processing patterns

### 4. Database Maintenance

Periodically clean up old data:

```sql
-- Delete message history older than 30 days
DELETE FROM message_history
WHERE timestamp < (EXTRACT(EPOCH FROM NOW() - INTERVAL '30 days') * 1000);

-- Keep only latest 10 tokens
DELETE FROM token
WHERE retrieved NOT IN (
    SELECT retrieved FROM token
    ORDER BY retrieved DESC
    LIMIT 10
);
```

## Alerting

Set up alerts for:

1. Service downtime (ping endpoint fails)
2. OAuth token expiration
3. High error rate in message_history
4. No transaction checks for > 1 hour

## Security Notes

1. Never share your full environment variables
2. Use the production diagnostics script which masks sensitive data
3. Rotate OAuth tokens if compromised
4. Keep webhook URLs private
