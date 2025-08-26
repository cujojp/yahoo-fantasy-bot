# OpenAI Integration for Test Messages

The Schefter-style tweet feature has been extended to work with test messages sent from the web interface.

## How It Works

When you send a test message from the Messaging Services page:

1. Click "Send Test Message" button
2. Optionally enter a custom message (or use the default)
3. The backend will:
   - Check if `OPENAI_API_KEY` is configured
   - Generate a Schefter-style tweet for the test message
   - Include both the original message and the tweet when sending to webhooks
4. The results modal will show:
   - Success/failure status for each service
   - A special indicator if a Schefter tweet was generated

## Example Test Message Output

**Without OpenAI:**
```
🤖 Test message from Yahoo Fantasy Bot! If you see this, your webhook is working correctly.
```

**With OpenAI:**
```
🤖 Test message from Yahoo Fantasy Bot! If you see this, your webhook is working correctly.

🏈 BREAKING: Yahoo Fantasy Bot systems are LIVE and operational. Sources confirm webhook connections are firing on all cylinders. League managers should expect real-time transaction alerts with championship-level reliability. The infrastructure is ready. 🚀
```

## Frontend Indicator

When a Schefter tweet is successfully generated, you'll see this in the results modal:

```
🏈 Schefter-style tweet was included with the message!
```

## Configuration

The same `OPENAI_API_KEY` environment variable is used for both:
- Transaction messages (in the bot)
- Test messages (in the backend)

This allows you to test the OpenAI integration without waiting for actual transactions to occur.
