package com.landonpatmore.yahoofantasybot.shared.services.news

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SchefterPromptTest {

    private val today = LocalDate.parse("2026-09-14")

    private val brief = NewsBrief(
        facts = listOf(
            NewsFact("Alvin Kamara", "Questionable (knee)", "ESPN", Instant.parse("2026-09-09T21:13:00Z"))
        ),
        week = 1,
        seasonType = "regular"
    )

    private val players = listOf(
        PlayerInfo("Jaxson Dart", "NYG", "QB"),
        PlayerInfo("Alvin Kamara", "NO", "RB")
    )

    @Test
    fun `the system prompt forbids inventing facts and week numbers`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("Never invent or estimate"))
        assertTrue(system.contains("Never mention a week number unless one is given"))
        assertTrue(system.contains("When FACTS says (none)"))
    }

    @Test
    fun `the system prompt requires the facts to actually be used`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("Build the post around entry 1"))
        assertTrue(system.contains("Restating only the roster move when facts were available is a failed post"))
    }

    @Test
    fun `the system prompt teaches the reporting half by example`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("Falcons quarterback Tua Tagovailoa sustained an oblique injury"))
        assertTrue(system.contains("Lead with the news, not with the fantasy manager"))
        assertTrue(system.contains("Introduce a player as team, position, then name"))
    }

    @Test
    fun `the system prompt sets up both registers before the rules`() {
        assertTrue(
            SchefterPrompt.system("ADD/DROP")
                .contains("somewhere between a deadpan beat writer and a fantasy account that is enjoying itself")
        )
    }

    @Test
    fun `a commissioner change is told it is all aside and no news`() {
        val system = SchefterPrompt.system("COMMISH CHANGES")
        assertTrue(system.contains("There is no NFL news behind a move like this"))
    }

    @Test
    fun `the system prompt offers both gears and tells the model to alternate`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("GEAR ONE: DEADPAN"))
        assertTrue(system.contains("GEAR TWO: SELL IT"))
        assertTrue(system.contains("Running the same gear on every post is the failure"))
        assertTrue(system.contains("Let the news pick the gear, not habit"))
        assertTrue(system.contains("a league where every post is BREAKING has no BREAKING left"))
    }

    @Test
    fun `the sell gear is allowed volume but not extra facts`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("capitals for emphasis, an opening BREAKING, one emoji"))
        assertTrue(system.contains("WHAT YOU MAY NEVER SELL WITH is a fact you do not have"))
        assertTrue(system.contains("red-hot"))
        assertTrue(system.contains("Snag him while you can"))
        assertTrue(system.contains("Not the manager's record"))
    }

    @Test
    fun `the system prompt protects the player and the friendship`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("never at the player, who did not do anything to anyone"))
        assertTrue(system.contains("Nothing genuinely mean"))
    }

    @Test
    fun `an opinion is exempt from the evidence rule but nothing else is`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("is not a factual claim and does not need evidence"))
        assertTrue(system.contains("If you cannot tell which one you are writing, it is a claim"))
        assertTrue(system.contains("Enthusiasm is free and facts are not"))
    }

    @Test
    fun `an empty facts block still allows the aside but no player color`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("No colour, no context, no reason about the player"))
        assertTrue(system.contains("You may still have a take on the move itself"))
    }

    @Test
    fun `the voice samples are fenced off so they cannot be mistaken for facts`() {
        val system = SchefterPrompt.system("ADD/DROP")
        assertTrue(system.contains("construction samples and nothing more"))
        assertTrue(system.contains("none of them may appear in your post"))
        assertTrue(system.contains("Same fence: those are shapes, not facts"))
    }

    @Test
    fun `the user prompt carries today, the transaction, the week and the facts`() {
        val user = SchefterPrompt.user("ADD/DROP", "Team added X and dropped Y", brief, players, today)
        assertTrue(user.contains("Monday, September 14, 2026"))
        assertTrue(user.contains("Detail: Team added X and dropped Y"))
        assertTrue(user.contains("Current NFL week: 1"))
        assertTrue(user.contains("1. [ESPN, Wed Sep 9, 5 days ago] Alvin Kamara: Questionable (knee)"))
    }

    @Test
    fun `the user prompt resolves each player's team and position`() {
        val user = SchefterPrompt.user("ADD/DROP", "Team added X and dropped Y", brief, players, today)
        assertTrue(user.contains("- Giants quarterback Jaxson Dart"))
        assertTrue(user.contains("- Saints running back Alvin Kamara"))
    }

    @Test
    fun `a transaction with no players has no players block`() {
        val user = SchefterPrompt.user("COMMISH CHANGES", "Settings changed", brief, emptyList(), today)
        assertFalse(user.contains("PLAYERS"))
    }

    @Test
    fun `an empty brief tells the model there are no facts`() {
        val user = SchefterPrompt.user("ADD", "Team added X", NewsBrief(emptyList()), players, today)
        assertTrue(user.contains("FACTS (newest first)\n(none)"))
    }

    @Test
    fun `evidence covers the transaction, the week and every fact`() {
        val evidence = SchefterPrompt.evidence("Team added X and dropped Y", brief, players)
        assertTrue(TweetFactChecker.findUnsupportedClaims("Week 1 move.", evidence).isEmpty())
        assertTrue(TweetFactChecker.findUnsupportedClaims("Week 22 move.", evidence).isNotEmpty())
    }

    @Test
    fun `evidence includes the resolved player descriptions the post is told to use`() {
        val evidence = SchefterPrompt.evidence("Team added X and dropped Y", brief, players)
        assertTrue(evidence.contains("Saints running back"))
    }
}
