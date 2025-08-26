# OpenAI Integration for Yahoo Fantasy Bot

This feature adds Adam Schefter-style tweet generation for all fantasy football transactions using OpenAI's GPT-4.

## Setup

1. **Get an OpenAI API Key**
   - Visit [OpenAI Platform](https://platform.openai.com/api-keys)
   - Create a new API key
   - Copy the key (you won't be able to see it again)

2. **Add the API Key to your environment**
   ```bash
   # Add to your .env file (local development)
   OPENAI_API_KEY=sk-your-api-key-here
   
   # Or set as environment variable (production)
   export OPENAI_API_KEY=sk-your-api-key-here
   ```

3. **Restart the bot**
   - The bot will automatically detect the API key and start generating Schefter-style tweets

## How it Works

When enabled, the bot will:
1. Process each transaction normally (Add, Drop, Add/Drop, Trade, Commissioner changes)
2. Send the transaction details to OpenAI's GPT-4
3. Generate a brief, punchy tweet in Adam Schefter's style
4. Include both the original message and the generated tweet in notifications

## Example Output

**Original Message:**
```
TeamName
Added: Patrick Mahomes (KC, QB)
```

**With Schefter Tweet:**
```
TeamName
Added: Patrick Mahomes (KC, QB)

🏈 BREAKING: TeamName makes power move, securing Patrick Mahomes. Fantasy managers take notice - KC's elite QB1 now anchored in a lineup poised for championship contention. The rich get richer. 🔥
```

## Transaction Types

- **ADD** - Player additions to rosters
- **DROP** - Player releases from rosters  
- **ADD/DROP** - Combined roster moves
- **TRADE** - Player trades between teams
- **COMMISH CHANGES** - Commissioner actions

## Notes

- The feature is optional - if no API key is provided, the bot works normally
- OpenAI API calls may incur costs based on usage
- Generated tweets are limited to ~280 characters for authenticity
- If the API call fails, the bot will send the original message without the tweet

## Cost Estimation

GPT-4 pricing (as of 2024):
- ~$0.03 per 1K tokens for GPT-4
- Each transaction uses approximately 200-300 tokens
- Estimated cost: ~$0.006-0.009 per transaction

For a typical league with 50-100 transactions per season, expect costs under $1.
