package com.landonpatmore.yahoofantasybot.shared.services.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostStyleTest {

    @Test
    fun `drops a trailing second emoji`() {
        assertEquals(
            "🏈 My Nix in a Box adds Jaxson Dart, who feels \"so much better\" in Nagy's scheme.",
            PostStyle.limitToOneEmoji(
                "🏈 My Nix in a Box adds Jaxson Dart, who feels \"so much better\" in Nagy's scheme. 🏈"
            )
        )
    }

    @Test
    fun `keeps a single emoji exactly where it was`() {
        val post = "🚨 Kamara is questionable with a knee issue."
        assertEquals(post, PostStyle.limitToOneEmoji(post))
    }

    @Test
    fun `leaves a post with no emoji alone`() {
        val post = "Samuel Lamar Jackson adds Kayshon Boutte and drops Jahan Dotson."
        assertEquals(post, PostStyle.limitToOneEmoji(post))
    }
}
