# Yahoo Fantasy Bot - Secure Railway Deployment

This guide covers deploying the Yahoo Fantasy Bot to Railway with enhanced security through Yahoo OAuth authentication.

## 🔒 Security Features

The Railway deployment includes enhanced security features:

- **Yahoo OAuth Authentication**: Required for all web interface access in production
- **Session Management**: Secure cookie-based sessions with configurable expiration
- **HTTPS Enforcement**: Automatic redirect to HTTPS in production
- **Security Headers**: Production security headers (HSTS, XSS protection, etc.)
- **Route Protection**: API and web routes protected by authentication middleware
- **Environment-based Configuration**: Different security levels for development vs production

## 🚀 Quick Setup

### 1. Create Railway Project

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login to Railway
railway login

# Create new project
railway new
cd yahoo-fantasy-bot
```

### 2. Configure Environment Variables

Set these environment variables in your Railway project:

#### **Required Variables**
```bash
# Yahoo API Configuration
YAHOO_CLIENT_ID=your_yahoo_client_id
YAHOO_CLIENT_SECRET=your_yahoo_client_secret
YAHOO_GAME_KEY=nfl
YAHOO_LEAGUE_ID=your_league_id

# Database (automatically set by Railway Postgres addon)
DATABASE_URL=postgresql://user:pass@host:port/db
JDBC_DATABASE_URL=jdbc:postgresql://host:port/db?user=user&password=pass

# Security (REQUIRED for production)
SESSION_SECRET_KEY=your-long-random-secret-key-minimum-32-characters
```

#### **Optional Variables**
```bash
# Messaging Services (at least one recommended)
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
GROUP_ME_BOT_ID=your_groupme_bot_id

# OpenAI Integration
OPENAI_API_KEY=sk-your-openai-api-key

# Force authentication in development
FORCE_AUTHENTICATION=true
```

### 3. Generate Session Secret Key

```bash
# Generate a secure random key (32+ characters)
openssl rand -hex 32
# OR
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

### 4. Deploy Services

#### Option A: Automatic Deployment (Recommended)
```bash
# Connect your GitHub repository to Railway
railway github

# Deploy with Railway
railway up
```

#### Option B: Manual Deployment
```bash
# Build and deploy
railway login
railway link [your-project-id]
railway up
```

## 🏗️ Service Architecture

Your Railway project should have these services:

### 1. **Web Service** (`yahoo-fantasy-bot-web`)
- **Purpose**: Serves the web interface and API
- **Port**: 8080
- **URL**: Your Railway-provided domain
- **Health Check**: `/health`

### 2. **Bot Service** (`yahoo-fantasy-bot`)
- **Purpose**: Runs the transaction monitoring bot
- **Type**: Worker service (no web interface)
- **Logs**: Check Railway logs for transaction processing

### 3. **PostgreSQL Database**
- **Purpose**: Stores configuration, tokens, and message history
- **Managed**: Automatically managed by Railway
- **Backups**: Automatic Railway backups

## 🔐 Authentication Flow

### Production Flow (Railway)
1. User visits your Railway URL
2. **Authentication middleware** checks for valid session
3. If not authenticated, redirects to `/authenticate`
4. User authorizes with Yahoo OAuth
5. App receives callback at `/auth` with authorization code
6. App exchanges code for access token and saves to database
7. **Secure session** created and stored in encrypted cookie
8. User can access all protected routes

### Development Flow (Local)
- Authentication **optional** unless `FORCE_AUTHENTICATION=true`
- Same OAuth flow available for testing

## 🛡️ Security Configuration

### Automatic Security Features

The app automatically enables these security features in production (Railway):

- **HTTPS Redirect**: All HTTP traffic redirected to HTTPS
- **Secure Cookies**: Session cookies use Secure, HttpOnly, and SameSite flags
- **Security Headers**: HSTS, XSS protection, content type sniffing protection
- **CORS Restrictions**: Only allows your Railway domain in production
- **Session Expiration**: 24-hour session timeout with automatic cleanup

### Protected Routes

These routes require authentication in production:
- `/` - Main dashboard
- `/api/*` - All API endpoints
- `/testMessage` - Test message functionality
- All static assets and frontend pages

### Public Routes (No Authentication Required)
- `/authenticate` - OAuth initiation
- `/auth` - OAuth callback  
- `/checkAuth` - Authentication status check
- `/health` - Health check for Railway
- `/logout` - Session termination

## 📱 Frontend Integration

The frontend automatically adapts to the authentication requirements:

```javascript
// Authentication check (enhanced)
fetch("/checkAuth")
  .then(res => res.json())
  .then(result => {
    console.log("Authenticated:", result.authenticated)
    console.log("Auth required:", result.authRequired)
    console.log("Environment:", result.environment)
  })
```

The UI shows appropriate login/logout options based on the environment and authentication status.

## 🔧 Configuration Options

### Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `RAILWAY_ENVIRONMENT` | Auto-set | - | Set by Railway to enable production mode |
| `SESSION_SECRET_KEY` | **Required** | - | Secret key for session encryption (32+ chars) |
| `FORCE_AUTHENTICATION` | Optional | `false` | Force auth even in development |
| `NODE_ENV` | Optional | - | Set to `development` to disable some security |
| `YAHOO_CLIENT_ID` | **Required** | - | Yahoo Developer App Client ID |
| `YAHOO_CLIENT_SECRET` | **Required** | - | Yahoo Developer App Client Secret |

### Advanced Security Options

```bash
# Force HTTPS redirect even in development
FORCE_HTTPS=true

# Custom session timeout (hours)
SESSION_TIMEOUT_HOURS=48

# Custom Railway domain (for CORS)
RAILWAY_PUBLIC_DOMAIN=your-app.railway.app
```

## 🚨 Troubleshooting

### Common Issues

#### 1. "Authentication Required" Error
```bash
# Check if SESSION_SECRET_KEY is set
railway variables get SESSION_SECRET_KEY

# Check authentication status
curl https://your-app.railway.app/checkAuth
```

#### 2. OAuth Callback Not Working
- Verify `YAHOO_CLIENT_ID` and `YAHOO_CLIENT_SECRET` are set
- Check Yahoo Developer Console redirect URLs include:
  - `https://your-app.railway.app/auth`
  - `http://localhost:8080/auth` (for development)

#### 3. Session Not Persisting
- Ensure `SESSION_SECRET_KEY` is at least 32 characters
- Check that cookies are enabled in browser
- Verify HTTPS is working (sessions require secure cookies in production)

### Debug Commands

```bash
# Check service status
railway status

# View live logs
railway logs --follow

# Check environment variables
railway variables

# Test health endpoint
curl https://your-app.railway.app/health
```

## 📊 Monitoring

### Health Check
```bash
GET /health
Response: {
  "status": "healthy",
  "timestamp": 1640995200000,
  "version": "1.0.0"
}
```

### Authentication Status
```bash
GET /checkAuth
Response: {
  "authenticated": true,
  "authRequired": true,
  "environment": "production"
}
```

### Message History
The new message history feature provides:
- All sent messages with timestamps
- Success/failure status
- Schefter tweet generation tracking
- Player involvement tracking
- Service-specific filtering

Access at: `https://your-app.railway.app` → Dashboard → Message History

## 🔄 Updates and Maintenance

### Deploying Updates
```bash
# Update from git
railway up

# Or use GitHub integration for automatic deploys
```

### Database Migrations
The app automatically creates new tables on startup, including the new message history table.

### Session Management
- Sessions expire after 24 hours
- Users can logout manually at `/logout`
- Sessions are automatically cleared on token expiration

## 🎯 Best Practices

1. **Keep SECRET_KEY Secure**: Never commit session keys to git
2. **Monitor Authentication**: Check logs for failed auth attempts
3. **Regular Updates**: Keep dependencies updated for security
4. **Backup Strategy**: Railway handles database backups automatically
5. **Monitor Usage**: Use Railway metrics to monitor application performance

## 📋 Post-Deployment Checklist

- [ ] All environment variables set correctly
- [ ] Yahoo OAuth redirect URLs updated
- [ ] HTTPS working (green lock in browser)
- [ ] Authentication flow working
- [ ] Message history tracking functional
- [ ] Bot service processing transactions
- [ ] Database connection healthy
- [ ] Health check responding
- [ ] Messaging services configured and working

Your Yahoo Fantasy Bot is now securely deployed on Railway with professional-grade authentication! 🎉
