package com.landonpatmore.yahoofantasybot.bot.messaging

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * These payloads were previously built by interpolating the message into a JSON string
 * literal, so any double quote in the text produced malformed JSON and the webhook
 * answered 400. It stayed hidden until posts began citing their sources.
 */
class MessagePayloadTest {

    /** The post that actually got a 400 from Discord. */
    private val quotedPost =
        """**My Nix in a Box**\nAdded: **Jaxson Dart** (NYG, QB)""" +
            "\n\n" +
            """My Nix in a Box adds Jaxson Dart, who feels "so much better" in Matt Nagy's scheme."""

    @Test
    fun `a quoted post produces valid JSON for Discord`() {
        val body = Discord("https://example.invalid/hook").payload(quotedPost)
        val content = JSONObject(body).getString("content")
        assertTrue(content.contains("""feels "so much better" in Matt Nagy's scheme"""))
    }

    @Test
    fun `a quoted post produces valid JSON for Slack and GroupMe`() {
        assertTrue(
            JSONObject(Slack("https://example.invalid/hook").payload(quotedPost))
                .getString("text").contains(""""so much better"""")
        )
        assertTrue(
            JSONObject(GroupMe("bot-123").payload(quotedPost))
                .getString("text").contains(""""so much better"""")
        )
    }

    @Test
    fun `GroupMe still carries its bot id`() {
        assertEquals("bot-123", JSONObject(GroupMe("bot-123").payload("hi")).getString("bot_id"))
    }

    @Test
    fun `escaped newlines survive as real newlines`() {
        // correctMessage() hands us the two-character sequence \n, and the rendered
        // message has to come out with actual line breaks.
        val content = JSONObject(Discord("https://example.invalid/hook").payload("""one\ntwo"""))
            .getString("content")
        assertEquals("one\ntwo", content)
    }

    @Test
    fun `a stray backslash does not break the payload`() {
        val content = JSONObject(Discord("https://example.invalid/hook").payload("""back\slash"""))
            .getString("content")
        assertEquals("""back\slash""", content)
    }
}
