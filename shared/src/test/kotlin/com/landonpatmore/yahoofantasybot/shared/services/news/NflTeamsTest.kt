package com.landonpatmore.yahoofantasybot.shared.services.news

import com.landonpatmore.yahoofantasybot.shared.services.PlayerInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NflTeamsTest {

    @Test
    fun `describes a player the way a reporter introduces one`() {
        assertEquals(
            "Jaguars running back Chris Rodriguez Jr.",
            NflTeams.describe(PlayerInfo("Chris Rodriguez Jr.", "Jax", "RB"))
        )
        assertEquals(
            "Patriots wide receiver A.J. Brown",
            NflTeams.describe(PlayerInfo("A.J. Brown", "NE", "WR"))
        )
    }

    @Test
    fun `reads Yahoo's mixed case abbreviations`() {
        assertEquals("Chiefs", NflTeams.nickname("KC"))
        assertEquals("Chiefs", NflTeams.nickname("kc"))
        assertEquals("Saints", NflTeams.nickname("NO"))
        assertEquals("49ers", NflTeams.nickname("SF"))
    }

    @Test
    fun `accepts the abbreviations the other feeds use`() {
        assertEquals("Jaguars", NflTeams.nickname("JAC"))
        assertEquals("Commanders", NflTeams.nickname("WSH"))
        assertEquals("Raiders", NflTeams.nickname("OAK"))
    }

    @Test
    fun `a team defense is a unit, not a person`() {
        assertEquals("Eagles defense", NflTeams.describe(PlayerInfo("Eagles", "Phi", "DEF")))
        assertEquals("Jaguars defense", NflTeams.describe(PlayerInfo("Jaguars", "Jax", "DEF")))
    }

    @Test
    fun `takes the first of Yahoo's multi-position lists`() {
        assertEquals("wide receiver", NflTeams.position("WR,TE"))
    }

    @Test
    fun `drops what it cannot look up rather than guessing`() {
        assertNull(NflTeams.nickname("XYZ"))
        assertEquals(
            "quarterback Some Rookie",
            NflTeams.describe(PlayerInfo("Some Rookie", "FA", "QB"))
        )
        assertEquals(
            "Bears Some Rookie",
            NflTeams.describe(PlayerInfo("Some Rookie", "Chi", "LS"))
        )
    }
}
