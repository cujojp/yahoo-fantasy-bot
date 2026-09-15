package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostStyleTest {

    @Test
    fun `leaves a sold post loud`() {
        val post = "🚨 BREAKING: Kamara is questionable with a knee issue. " +
                "My Nix in a Box got out first! #Waivers"
        assertEquals(post, PostStyle.enforce(post))
    }

    @Test
    fun `caps the emoji at one and keeps it where it stood`() {
        assertEquals(
            "🏈 My Nix in a Box adds Jaxson Dart and drops Alvin Kamara.",
            PostStyle.enforce("🏈 My Nix in a Box adds Jaxson Dart and drops Alvin Kamara. 🏈")
        )
    }

    @Test
    fun `caps the hashtags at one`() {
        assertEquals(
            "Boutte caught four passes Sunday. #FantasyFootball",
            PostStyle.enforce("Boutte caught four passes Sunday. #FantasyFootball #RosterMove")
        )
    }

    @Test
    fun `keeps BREAKING, which is a voice, and drops TRANSACTION, which is plumbing`() {
        assertEquals(
            "BREAKING: Dick Chubb dropped Jakobi Meyers.",
            PostStyle.enforce("BREAKING: Dick Chubb dropped Jakobi Meyers.")
        )
        assertEquals(
            "Dick Chubb dropped Jakobi Meyers.",
            PostStyle.enforce("TRANSACTION: Dick Chubb dropped Jakobi Meyers.")
        )
    }

    @Test
    fun `strips a plumbing label without taking the emoji with it`() {
        assertEquals(
            "🏈 Samuel Lamar Jackson adds Kayshon Boutte.",
            PostStyle.enforce("🏈 TRANSACTION: Samuel Lamar Jackson adds Kayshon Boutte.")
        )
    }

    @Test
    fun `calms down a run of exclamation points`() {
        assertEquals(
            "The drop button has been pressed!",
            PostStyle.enforce("The drop button has been pressed!!!")
        )
    }

    @Test
    fun `leaves a deadpan post alone`() {
        val post = "Jaguars running back Chris Rodriguez Jr. rushed six times for 23 yards " +
                "in Sunday's win over the Browns. Danimals has seen enough."
        assertEquals(post, PostStyle.enforce(post))
    }

    @Test
    fun `does not mistake a mid-sentence word for a leading label`() {
        val post = "The transaction: Danimals dropped Chris Rodriguez Jr."
        assertEquals(post, PostStyle.enforce(post))
    }
}
