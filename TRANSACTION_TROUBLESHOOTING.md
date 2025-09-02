# Yahoo Fantasy Bot Transaction Troubleshooting Guide

## Overview

This guide helps diagnose and fix issues when Yahoo Fantasy transactions are not being processed or logged properly.

## Quick Diagnostics

### 1. Run the Connection Test Script

```bash
./scripts/test-yahoo-connection.sh
```

This script will check:
- Environment variables configuration
- Database connection
- OAuth token status
- Bot process status
- Recent error logs

### 2. Test Transaction Fetching

```bash
./scripts/test-transactions.sh
```

This script will:
- Verify OAuth token validity
- Make a real API call to Yahoo
- Show recent transactions
- Compare with last processed timestamp

### 3. Check Database Status

```bash
psql "$JDBC_DATABASE_URL" -f scripts/check-transactions.sql
```

This will show:
- OAuth token status
- Last transaction check time
- Recent message history
- Any error messages

## Common Issues and Solutions

### Issue 1: No OAuth Token

**Symptoms:**
- Bot shows "There is currently no token data in the database"
- Scripts show "NO TOKEN FOUND"

**Solution:**
1. Start the backend: `./gradlew :backend:run`
2. Visit http://localhost:8080
3. Click "Login with Yahoo"
4. Complete OAuth flow
5. Restart the bot

### Issue 2: Expired Token

**Symptoms:**
- API calls fail with 401 error
- Token expiry time is in the past

**Solution:**
- The bot should auto-refresh tokens
- If not working, re-authenticate via web UI

### Issue 3: No Logs or Output

**Symptoms:**
- Bot runs but shows no transaction activity
- No error messages

**Causes & Solutions:**

1. **Wrong League/Game Key**
   - Verify `YAHOO_GAME_KEY` and `YAHOO_LEAGUE_ID` in `.env`
   - Game Keys: NFL=423, NBA=428, MLB=431, NHL=427

2. **Database Issues**
   - Check if `latest_time_checked` table has valid data
   - Look for -1 values which indicate errors

3. **No New Transactions**
   - The bot only processes NEW transactions
   - Check when transactions actually occurred in your league

### Issue 4: Enhanced Logging

After applying the updates in this PR, you should see detailed logs:

```
[Arbiter] Running transaction check cycle...
[Arbiter] Fetching transactions from Yahoo API...
[DataRetriever] Grabbing data from URL: https://fantasysports.yahooapis.com/...
[DataRetriever] Response code: 200
[Arbiter] Total transactions in response: 15
[TransactionTransformer] Processing transactions document
[TransactionTransformer] Found 15 transactions in document
[TransactionTransformer] Transaction timestamp: 1703001234, check time: 1703000000, is new: true
[TransactionTransformer] New transaction found - Type: add
```

### Issue 5: Messages Not Being Sent

**Check Message History:**
```sql
SELECT * FROM message_history 
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000)
ORDER BY timestamp DESC;
```

**Verify Messaging Services:**
- Ensure `GROUP_ME_BOT_ID`, `DISCORD_WEBHOOK_URL`, or `SLACK_WEBHOOK_URL` are set
- Check `error_message` column in message_history for failures

## Debug Mode

To see all transaction processing in real-time:

1. Stop the bot if running
2. Run with enhanced logging:
   ```bash
   ./gradlew :bot:run
   ```
3. Watch the console output for the log messages

## Manual Transaction Check

To manually trigger a transaction check without waiting 15 seconds:

1. Connect to database:
   ```bash
   psql "$JDBC_DATABASE_URL"
   ```

2. Set last check time to past:
   ```sql
   UPDATE latest_time_checked 
   SET time = (EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000)::bigint;
   ```

3. The next bot cycle will process all transactions from the last hour

## API Response Validation

To see raw Yahoo API responses:

1. Run the test script: `./scripts/test-transactions.sh`
2. Check the XML structure
3. Verify transaction elements exist

## Contact Support

If issues persist:
1. Collect all log output
2. Run all diagnostic scripts
3. Check https://github.com/cujojp/yahoo-fantasy-bot/issues
4. Include your diagnostic results when reporting issues
