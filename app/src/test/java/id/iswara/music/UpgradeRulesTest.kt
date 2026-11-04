package id.iswara.music

import id.iswara.music.data.*
import id.iswara.music.model.Track
import id.iswara.music.playback.QueueRules
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

class UpgradeRulesTest {
    private fun track(id: String, duration: Long = 180) = Track(id, "Lagu $id", "Artis $id", duration = duration, url = "https://www.youtube.com/watch?v=$id")
    @Test fun radioExcludesQueueRecentDuplicatesAndLongMixes() {
        val current = track("a")
        val next = QueueRules.radio(listOf(current), listOf(current, track("b"), track("b"), track("c"), track("long", 5000), track("local").copy(local = true)), listOf("c"))
        assertEquals(listOf("b"), next.map { it.id })
    }
    @Test fun radioHonorsLimitAndManualQueueRemainsUnchanged() {
        val queue = listOf(track("a"), track("manual"))
        val next = QueueRules.radio(queue, List(30) { track("related-$it") }, limit = 5)
        assertEquals(5, next.size); assertEquals("manual", queue.last().id)
        assertEquals(listOf("new"), QueueRules.append(queue, listOf(track("a"), track("new"), track("new")), false).map { it.id })
    }
    @Test fun listeningSummaryUsesActualSecondsAndCalendarPeriod() {
        val summary = summarizeListening(listOf(ListeningDay("2026-11-01", track("a"), 40), ListeningDay("2026-11-02", track("a"), 35), ListeningDay("2026-11-02", track("b"), 20), ListeningDay("2026-10-31", track("a"), 100)), "2026-11")
        assertEquals(95L, summary.seconds); assertEquals(75L, summary.tracks.first().second)
        assertEquals("Artis a", summary.artists.first().first)
    }
    @Test fun chartsCombineAlternateUploadsButKeepRemixesSeparate() {
        val original = track("original").copy(title = "Hujan", artist = "Nara")
        val duplicate = original.copy(id = "alternate", title = "Hujan (Official Audio)", artist = "Nara - Topic")
        val remix = original.copy(id = "remix", title = "Hujan (Remix)")
        val entries = listOf(
            ListeningDay("2026-11-01", original, 100),
            ListeningDay("2026-11-02", duplicate, 80),
            ListeningDay("2026-11-03", remix, 90),
            ListeningDay("2026-11-04", track("zero"), 0),
        )
        val summary = summarizeListening(entries, "2026-11")
        assertEquals(listOf("Nara" to 270L), summary.artists)
        assertEquals(listOf(180L, 90L), summary.tracks.map { it.second })
    }
    @Test fun offlineAudioAuthenticatesCiphertextAndSeparatesOwners() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val plain = ByteArray(100_000) { (it % 251).toByte() }
        val encrypted = cipher.iv + cipher.doFinal(plain)
        assertArrayEquals(plain, decryptOffline(encrypted, key))
        encrypted[30] = (encrypted[30].toInt() xor 1).toByte()
        assertTrue(runCatching { decryptOffline(encrypted, key) }.isFailure)
        assertNotEquals(offlineFileName("guest", "song"), offlineFileName("user-a", "song"))
        assertNotEquals(offlineFileName("user-a", "song"), offlineFileName("user-b", "song"))
    }
}
