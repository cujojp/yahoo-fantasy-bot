package com.landonpatmore.yahoofantasybot.shared.messaging

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebhookPayloadTest {

    @Test
    fun `quotes survive a round trip`() {
        val text = """Dart feels "so much better" in Nagy's scheme."""
        assertEquals(text, JSONObject(WebhookPayload.of("content" to text)).getString("content"))
    }

    @Test
    fun `backslashes newlines and tabs survive a round trip`() {
        val text = "line one\nback\\slash\ttab"
        assertEquals(text, JSONObject(WebhookPayload.of("text" to text)).getString("text"))
    }

    @Test
    fun `carries every field it is given`() {
        val body = JSONObject(WebhookPayload.of("bot_id" to "abc123", "text" to "hi"))
        assertEquals("abc123", body.getString("bot_id"))
        assertEquals("hi", body.getString("text"))
    }

    @Test
    fun `a message that is only a quote does not break the body`() {
        assertEquals("\"", JSONObject(WebhookPayload.of("content" to "\"")).getString("content"))
    }
}
