# Railway Migration Checklist

## Pre-Migration

- [ ] **Backup Current Data**
  - [ ] Export Heroku environment variables: `heroku config -a your-app > .env.backup`
  - [ ] Create database backup: `heroku pg:backups:capture -a your-app`
  - [ ] Download backup: `heroku pg:backups:download -a your-app`
  - [ ] Note current domain/URL for OAuth redirect updates

- [ ] **Prepare Railway Account**
  - [ ] Create Railway account at [railway.app](https://railway.app)
  - [ ] Install Railway CLI: `npm install -g @railway/cli`
  - [ ] Login: `railway login`

## Migration Steps

### 1. Deploy to Railway

- [ ] **Create Railway Project**
  ```bash
  railway create yahoo-fantasy-bot
  railway link
  ```

- [ ] **Add PostgreSQL Database**
  ```bash
  railway add postgresql
  ```

- [ ] **Deploy Application**
  ```bash
  railway up
  ```

### 2. Configure Services

- [ ] **Set Environment Variables**
  - [ ] `YAHOO_CLIENT_ID`
  - [ ] `YAHOO_CLIENT_SECRET` 
  - [ ] `YAHOO_GAME_KEY`
  - [ ] `YAHOO_LEAGUE_ID`
  - [ ] `GROUP_ME_BOT_ID` (if using GroupMe)
  - [ ] `DISCORD_WEBHOOK_URL` (if using Discord)
  - [ ] `SLACK_WEBHOOK_URL` (if using Slack)

- [ ] **Create Worker Service**
  - [ ] Add new service in Railway dashboard
  - [ ] Set start command: `java -jar build/libs/bot.jar`
  - [ ] Copy all environment variables from web service

### 3. Update External Services

- [ ] **Update Yahoo OAuth Redirect**
  - [ ] Go to [Yahoo Developer Console](https://developer.yahoo.com/apps/)
  - [ ] Update redirect URI to: `https://your-app.railway.app/auth`

- [ ] **Update Webhook URLs** (if applicable)
  - [ ] Discord webhook URLs
  - [ ] Slack webhook URLs
  - [ ] Any other external service configurations

### 4. Data Migration (if needed)

- [ ] **Import Database Data**
  - [ ] Connect to Railway PostgreSQL
  - [ ] Import backed up data
  - [ ] Verify data integrity

### 5. Testing

- [ ] **Basic Functionality**
  - [ ] Web service responds on Railway URL
  - [ ] Yahoo OAuth authentication works
  - [ ] Database connections successful
  - [ ] Worker service running and processing

- [ ] **Integration Testing**
  - [ ] Test transaction notifications
  - [ ] Verify messaging service integrations
  - [ ] Test alert scheduling functionality
  - [ ] Verify frontend configuration UI

- [ ] **Load Testing**
  - [ ] Monitor response times
  - [ ] Check memory usage
  - [ ] Verify no errors in logs

## Post-Migration

### 6. Cleanup

- [ ] **Monitor for 24-48 hours**
  - [ ] Check logs for errors: `railway logs`
  - [ ] Monitor database performance
  - [ ] Verify all scheduled jobs running

- [ ] **Update Documentation**
  - [ ] Update any internal documentation with new URLs
  - [ ] Update monitoring/alerting configurations
  - [ ] Notify users of new domain (if applicable)

- [ ] **Heroku Cleanup** (after confirming Railway works)
  - [ ] Scale down Heroku dynos
  - [ ] Cancel Heroku addons
  - [ ] Delete Heroku app (when confident)

### 7. Optimization

- [ ] **Performance Tuning**
  - [ ] Adjust Railway service resources if needed
  - [ ] Optimize database queries if performance issues
  - [ ] Configure logging levels

- [ ] **Cost Monitoring**
  - [ ] Check Railway usage dashboard
  - [ ] Set up billing alerts if needed
  - [ ] Compare costs vs Heroku

## Rollback Plan

If issues arise:

1. **Quick Rollback**
   - [ ] Scale up Heroku dynos
   - [ ] Revert OAuth redirect URLs
   - [ ] Update webhook URLs back to Heroku

2. **Data Rollback**
   - [ ] Restore database from backup if needed
   - [ ] Verify data consistency

## Estimated Timeline

- **Preparation**: 30 minutes
- **Migration**: 1-2 hours  
- **Testing**: 2-4 hours
- **Monitoring**: 24-48 hours
- **Total**: 1-3 days

## Support

- Railway Docs: https://docs.railway.app
- Railway Discord: https://discord.gg/railway
- GitHub Issues: [Create issue if problems arise]

## Success Criteria

✅ **Migration is successful when:**
- [ ] Web service accessible on Railway URL
- [ ] Worker service processing transactions
- [ ] All messaging integrations working
- [ ] No data loss
- [ ] Performance equal or better than Heroku
- [ ] Costs reduced compared to Heroku
