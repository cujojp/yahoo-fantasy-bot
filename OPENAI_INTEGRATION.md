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

A dated fact older than ten days is dropped. ESPN's injury feed keeps a row indefinitely, so
a player who tweaked a hamstring in training camp still reads as news in November, and that
is where the stale posts came from. Ten days is a little over a game week, which keeps last
Sunday in and everything before it out. Yahoo's and Sleeper's injury designations carry no
date and are never dropped, because they are readings of the player's current state rather
than reports filed on a particular day. What survives is sorted newest first, and the model
is told to lead with the first entry.

Some ESPN endpoints look like a better fit but do not work, and are not worth retrying:
the news feed silently ignores an `athlete` parameter and returns unrelated league news,
the per-team injuries route returns an empty object, and the core API's per-athlete
injuries route 404s.

## How a post is built

1. `PlayerNewsService` collects up to six recent, dated, attributed facts for the players
   in the transaction, newest first.
2. `SchefterPrompt` builds the prompt: today's date, the verified week, the transaction, a
   PLAYERS block naming each player the way a reporter would, and a numbered FACTS block.
   The model is told it may assert nothing outside those blocks, and that if FACTS is empty
   it should state the roster move without adding any colour about the player.
3. `TweetFactChecker` reads the generated post back against the evidence. Any week number,
   statistic, percentage, ranking or quote that is not traceable to the evidence drops the
   post, and the alert goes out as the plain roster move.
4. `PostStyle.enforce` caps what the model overdoes on its own: more than one emoji, more
   than one hashtag, a run of exclamation points, and a leading `TRANSACTION:` label.
5. The attribution line (`via ESPN, Sep 9`) is appended so an ungrounded post is visibly
   missing one.

Temperature is 0.4. The reporting half of the post is not a creative task, and the gear is a
choice rather than an invention.

## The voice

Grounding fixed the accuracy problem and left a style one. Every post came out shaped the
same way, leading with the fantasy manager in the present tense with the news demoted to a
relative clause:

```
Danimals adds Xavier Worthy, wide receiver for Kansas City, to their roster.
Dick Chubb adds the Eagles defense and drops Jakobi Meyers, who is practicing in a
non-contact jersey due to a hand issue.
```

Schefter does the opposite. He leads with the news, names a player as team plus position
plus name, puts events in the past tense, anchors them to a day, and lands the attribution
inside the sentence. The prompt now shows three of his actual sentences before it describes
any of that, because naming a writer gets you an impression of one and showing the sentences
gets you the sentences.

Three things the model could not do before are supplied rather than left to it:

- **Today's date.** Nothing in the old prompt said what day it was, so the model could not
  write "Sunday's win" and defaulted to a timeless present tense.
- **Each fact's age**, rendered on its line as `[ESPN, Wed Sep 9, 5 days ago]`. A date alone
  does not tell the model that a practice note is a week old.
- **The expanded team and position** for every player, via `NflTeams`. Getting from Yahoo's
  `(Jax, RB)` to "Jaguars running back" is the one place the model would otherwise have to
  supply a fact of its own, and a wrong team is invisible to the fact checker because it is
  neither a number nor a quote. So we look it up.

Dates are resolved in `America/New_York`. UTC rolls over mid-evening on the east coast,
which files a Sunday night game under Monday and has us calling it the wrong day.

## The two gears

Straight Schefter turned out to be too straight. The posts came back correct, well shaped
and slightly airless, filed in a wire-service voice about a league whose teams are called
"My Nix in a Box" and "Njigbas in Paris". So the reporting half stays in his construction
and the post is landed in one of two gears, whichever the news deserves.

Deadpan, for a small item or a silly move:

```
Jaguars running back Chris Rodriguez Jr. rushed six times for 23 yards in Sunday's win
over the Browns. Danimals has seen enough.
```

Sold, for news that is actually big:

```
🚨 BREAKING: Saints running back Alvin Kamara was limited in practice Wednesday with a
knee issue and is listed as questionable. My Nix in a Box saw it coming and got out
first. Ruthless.
```

The gear is picked from the news, not from what we posted last, because each call is
independent and the model has no memory of the previous post. That means the variance is
only as good as the variance in the news. If everything starts coming out sold, the fix is
to feed recent posts in as context rather than to ask the prompt to remember something it
cannot. The prompt's counterweight for now is a prior: most moves are gear one, and a league
where every post is BREAKING has no BREAKING left.

## Volume is free, facts are not

This is the line that did not move, and it is worth being precise about because the first
version of this feature had personality and it looked like this:

```
🚨BREAKING: Samuel Lamar Jackson shakes up his roster! ADDS red-hot WR Kayshon Boutte
(HOU) and DROPS Jahan Dotson (ATL). Boutte's stock is on the rise folks, snag him while
he's still available! 🏈 #FantasyFootball #RosterMove
```

Two different things are wrong there and only one of them was ever a real problem. The siren
and the capitals are a voice, and a voice is allowed. "Red-hot" and "stock is on the rise"
are assertions about how a player is performing, we hold no performance data of any kind,
and neither phrase contains a digit for the fact checker's other rules to catch. That is
fabrication wearing enthusiasm as a costume.

So the split runs on the opinion-versus-claim line rather than on tone. Anything about the
move, the manager or this league is an opinion and needs no evidence: the team name and who
dropped whom are Yahoo's own data, and a read on somebody's judgment cannot be wrong.
Anything about a player, a team or a game is a claim and needs a source. The prompt states
it in one rule, including the tiebreaker: if you cannot tell which one you are writing, it
is a claim.

Because prompts leak, `TweetFactChecker` also blocks the specific phrases that are always a
form claim (`red-hot`, `on fire`, `breakout`, `stock is rising`, `league winner`,
`must-start`, `smash play`, `heating up`, and a few more). A post containing one that the
evidence does not support gets dropped like any other unsupported claim. The list is
deliberately short and unambiguous; "rolling" and "elite" were considered and left off,
because a false positive costs a good post.

`PostStyle.enforce` now only strips noise, not volume. Capitals, exclamation points and an
opening `BREAKING:` all survive. An emoji is capped at one and a hashtag at one, because the
model does not stop on its own and `#FantasyFootball #RosterMove` reads as filler rather
than enthusiasm. `TRANSACTION:` is still stripped, because that is a database field leaking
into prose rather than a register.

## Example output

```
My Nix in a Box
Added: Jaxson Dart (NYG, QB)
Dropped: Alvin Kamara (NO, RB)

Saints running back Alvin Kamara was limited in practice Wednesday with a knee issue
and is listed as questionable. My Nix in a Box did not wait to find out.
via ESPN, Sep 9
```

With nothing sourced, the NFL half goes away and the take is the whole post:

```
Danimals
Added: Xavier Worthy (KC, WR)

Danimals added Chiefs wide receiver Xavier Worthy. No further explanation was provided.
```

Note the missing attribution line on the second one. That is how you tell at a glance that
nothing behind it was sourced. A post can also come out as the plain roster move with no
take at all, either because the model had nothing worth saying or because the fact checker
dropped what it wrote. That is the intended behaviour. A quiet alert beats an invented one.

## Verifying it

```bash
./gradlew :shared:test :bot:test
NEWS_LIVE_CHECK=1 ./gradlew :shared:test --tests '*LiveNewsCheck*' -i
```

The first runs the unit tests, including the fact checker's cases. The second hits the
live feeds and prints the exact prompt the model would receive, which is the fastest way
to see why a player came back with no facts.

## Cost

Each transaction is one call of roughly 1,500 to 1,800 tokens. The voice section of the
system prompt accounts for most of the increase over the ungrounded version. For a league
running 50 to 100 transactions a season this still stays well under a dollar.
