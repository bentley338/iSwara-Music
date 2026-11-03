package id.iswara.music

import id.iswara.music.cloud.withAbsoluteExpiry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionExpiryTest {
    @Test fun restSessionGetsStableAbsoluteExpiry() {
        val session = buildJsonObject { put("expires_in", 3600); put("access_token", "test") }
        val stored = session.withAbsoluteExpiry(1000)
        assertEquals(4600, stored["expires_at"]!!.jsonPrimitive.long)
        assertEquals(stored, stored.withAbsoluteExpiry(1500))
        assertEquals("test", stored["access_token"]!!.jsonPrimitive.content)
    }
    @Test fun existingExpiryIsPreservedAndMissingExpiryIsNotInvented() {
        val session = buildJsonObject { put("expires_at", 500); put("expires_in", 3600) }
        assertEquals(session, session.withAbsoluteExpiry(1000))
        val empty = buildJsonObject { put("access_token", "test") }
        assertEquals(empty, empty.withAbsoluteExpiry(1000))
    }
}
