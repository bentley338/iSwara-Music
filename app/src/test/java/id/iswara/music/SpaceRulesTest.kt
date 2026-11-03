package id.iswara.music

import id.iswara.music.data.DownloadedTrack
import id.iswara.music.model.Track
import id.iswara.music.ui.*
import org.junit.Assert.*
import org.junit.Test

class SpaceRulesTest {
    @Test fun corruptAndObsoletePinsCannotExposeAdminOrDuplicateActions() {
        assertEquals(listOf(SpaceFeature.STUDIO, SpaceFeature.OFFLINE), SpaceRules.pins("studio,admin,removed,studio,offline", false))
        assertTrue(SpaceRules.pins("", false).isEmpty())
        assertTrue(SpaceRules.pins("admin", true).contains(SpaceFeature.ADMIN))
        assertFalse(SpaceRules.matches(SpaceFeature.ADMIN, "admin", 0, false))
        val full = SpaceFeature.entries.take(6)
        val changed = SpaceRules.toggle(full, SpaceFeature.WIDGETS)
        assertEquals(6, changed.size)
        assertFalse(changed.contains(full.first()))
        assertFalse(SpaceRules.toggle(changed, SpaceFeature.WIDGETS).contains(SpaceFeature.WIDGETS))
    }
    @Test fun localizedFeatureSearchRespectsCategoryAndWhitespace() {
        assertTrue(SpaceRules.matches(SpaceFeature.STUDIO, "  karaoke  ", 2, false))
        assertTrue(SpaceRules.matches(SpaceFeature.TOGETHER, "listen together", 3, false))
        assertFalse(SpaceRules.matches(SpaceFeature.TOGETHER, "listen", 2, false))
        assertFalse(SpaceRules.matches(SpaceFeature.EQ, "backup", 0, false))
    }
    @Test fun offlineSearchAndSortKeepOriginalEntriesAndActualSize() {
        fun entry(id: String, title: String, artist: String, size: Long) = DownloadedTrack(Track(id, title, artist, url = "https://example.org/$id"), id, size)
        val input = listOf(entry("a", "Hujan", "Sore", 500), entry("b", "Angin", "Pagi", 3000), entry("c", "Laut", "Sore", 2000))
        assertEquals(listOf("c", "a"), OfflineRules.filter(input, "  sOrE ", "size").map { it.track.id })
        assertEquals(listOf("b", "a", "c"), OfflineRules.filter(input, "", "title").map { it.track.id })
        assertEquals(listOf("c", "b", "a"), OfflineRules.filter(input, "", "recent").map { it.track.id })
        assertEquals("a", input.first().track.id)
        assertEquals("1.5 MB", OfflineRules.megabytes(1_500_000))
        assertTrue(OfflineRules.filter(input, "missing", "size").isEmpty())
    }
}
