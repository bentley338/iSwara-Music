package id.iswara.music

import id.iswara.music.cloud.*
import id.iswara.music.model.Track
import org.junit.Assert.*
import org.junit.Test

class TogetherRulesTest {
    private val song=Track("abcdefghijk","Fixture",duration=180,url="https://www.youtube.com/watch?v=abcdefghijk")
    private val room=ListeningRoom("id","ABCDEF123456","Room","host",listOf(song),0,10_000,true,5,100_000,100_000,1_000_000,false,2)
    @Test fun projectedPositionUsesServerClockClampsAtTrackEndAndStopsWhenPaused() {
        assertEquals(12_500L,TogetherRules.position(room,102_500))
        assertEquals(10_000L,TogetherRules.position(room.copy(playing=false),102_500))
        assertEquals(10_000L,TogetherRules.position(room,98_000))
        assertEquals(180_000L,TogetherRules.position(room,999_999))
        assertFalse(TogetherRules.hostMissing(room,130_000));assertTrue(TogetherRules.hostMissing(room,130_001))
    }
    @Test fun invitationsAcceptFormattedCodesAndOnlyRemoteTracksCanBeShared() {
        assertEquals("ABCDEF123456",TogetherRules.code("iswara://room/ABCD-EF12-3456?join=1"))
        assertTrue(TogetherRules.valid(song));assertFalse(TogetherRules.valid(song.copy(local=true)))
        assertFalse(TogetherRules.valid(song.copy(id="file:1")))
    }
}
