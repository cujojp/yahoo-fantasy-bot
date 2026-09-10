# OpenAI Integration for Yahoo Fantasy Bot

The bot posts a short insider-style note alongside each transaction alert. The model
writes it, but the model is never the source. Facts are gathered from real feeds first,
handed to the model as an explicit evidence block, and the finished post is checked back
against that evidence before anything is sent.

## Setup

1. Get a key from the [OpenAI Platform](https://platform.openai.com/api-keys).
2. Set it in the environment:
   ```bash
   OPENAI_API_KEY=sk-your-api-key-here
   ```
3. Restart the bot. It picks the key up on startup.

Without a key the bot still works and just sends the plain roster move.

## Configuration

| Variable | Default | What it does |
|---|---|---|
| `OPENAI_API_KEY` | unset | Enables generation. Unset means plain alerts only. |
| `OPENAI_MODEL` | `gpt-4o` | Override to move models without a code change. |
| `SLEEPER_PLAYER_MAP` | `true` | Set to `false` to skip Sleeper's player table. It is about 15 MB, so turn it off on a small container. |

## Where the facts come from

Everything the model is allowed to say comes from one of these, and each fact carries a
source and a date. Nothing is inferred.

- **ESPN player report** (`site.api.espn.com/.../nfl/injuries`). Roughly 800 entries across
  all 32 teams. Despite the name it covers general player news too, each with a dated
  beat-writer comment. This is the primary source.
- **ESPN headlines** (`.../nfl/news?limit=50`). Only articles ESPN has explicitly tagged
  with an athlete are used. Matching on a name appearing in the body attaches the wrong
  player's news to a move, so we do not do it.
- **Yahoo Fantasy player resource**. Injury designation and status for the player.
- **Sleeper** (`api.sleeper.app`). Authoritative week and season type, plus a player table
  keyed by Yahoo's own player id.

All of it is cached: ESPN for 15 minutes, Sleeper's week for 30 minutes, Sleeper's player
table for 24 hours. Only ESPN and Sleeper need no authentication, so a Yahoo outage costs
one source rather than all of them.

Some ESPN endpoints look like a better fit but do not work, and are not worth retrying:
the news feed silently ignores an `athlete` parameter and returns unrelated league news,
the per-team injuries route returns an empty object, and the core API's per-athlete
injuries route 404s.

## How a post is built

1. `PlayerNewsService` collects up to six dated, attributed facts for the players in the
   transaction.
2. `SchefterPrompt` builds the prompt: the transaction, the verified week, and a numbered
   FACTS block. The model is told it may assert nothing outside that block, and that if
   FACTS is empty it should write one plain sentence about the roster move and stop.
3. `TweetFactChecker` reads the generated post back against the evidence. Any week number,
   statistic, percentage, ranking or quote that is not traceable to the evidence drops the
   post, and the alert goes out as the plain roster move.
4. The attribution line (`via ESPN, Sep 9`) is appended so an ungrounded post is visibly
   missing one.

Temperature is 0.4. This is a reporting task, not a creative one.

## Example output

```
My Nix in a Box
Added: Jaxson Dart (NYG, QB)
Dropped: Alvin Kamara (NO, RB)

My Nix in a Box swaps Alvin Kamara for Jaxson Dart. Kamara is questionable after
returning to practice Wednesday with a knee issue.
via ESPN, Sep 9
```

When nothing can be sourced, the post is one plain line about the move, or absent
entirely. That is the intended behaviour. A quiet alert beats an invented one.

## Verifying it

```bash
./gradlew :shared:test :bot:test
NEWS_LIVE_CHECK=1 ./gradlew :shared:test --tests '*LiveNewsCheck*' -i
```

The first runs the unit tests, including the fact checker's cases. The second hits the
live feeds and prints the exact prompt the model would receive, which is the fastest way
to see why a player came back with no facts.

## Cost

Each transaction is one call of roughly 600 to 900 tokens, most of it the evidence block.
For a league running 50 to 100 transactions a season this stays well under a dollar.
