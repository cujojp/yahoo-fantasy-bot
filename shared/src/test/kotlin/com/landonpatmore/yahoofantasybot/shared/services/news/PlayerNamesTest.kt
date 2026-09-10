package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerNamesTest {

    @Test
    fun `strips suffixes so Yahoo and ESPN spellings match`() {
        assertEquals(PlayerNames.normalize("Marvin Harrison Jr."), PlayerNames.normalize("Marvin Harrison"))
        assertEquals(PlayerNames.normalize("Michael Pittman Jr"), PlayerNames.normalize("Michael Pittman"))
        assertEquals(PlayerNames.normalize("Deebo Samuel Sr."), PlayerNames.normalize("Deebo Samuel"))
    }

    @Test
    fun `ignores punctuation and case`() {
        assertEquals(PlayerNames.normalize("T.J. Hockenson"), PlayerNames.normalize("TJ HOCKENSON"))
        assertEquals(PlayerNames.normalize("Ja'Marr Chase"), PlayerNames.normalize("JaMarr Chase"))
    }

    @Test
    fun `keeps distinct players distinct`() {
        assert(PlayerNames.normalize("Josh Allen") != PlayerNames.normalize("Keenan Allen"))
    }
}
