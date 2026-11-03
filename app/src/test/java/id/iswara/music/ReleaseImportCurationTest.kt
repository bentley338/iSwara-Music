package id.iswara.music

import id.iswara.music.data.*
import id.iswara.music.model.*
import id.iswara.music.playback.*
import id.iswara.music.source.*
import id.iswara.music.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import androidx.compose.ui.graphics.luminance
import kotlinx.serialization.encodeToString

class ReleaseImportCurationTest {
    private val release=AppRelease(12,"0.10.0","https://github.com/owner/iswara/releases/download/v0.10.0/iSwara.apk","a".repeat(64),174000000)
    @Test fun updateLinksEnforceReleasePathAndTrustedRedirects() {
        assertTrue(UpdateRules.valid(release))
        for(url in listOf("http://github.com/owner/iswara/releases/download/x/app.apk","https://github.com.evil.example/owner/iswara/releases/download/x/app.apk","https://owner@github.com/owner/iswara/releases/download/x/app.apk","https://github.com/owner/iswara/blob/main/app.apk","https://github.com/owner/iswara/releases/download/x/app.apk?token=anything"))assertFalse(url,UpdateRules.valid(release.copy(url=url)))
        assertTrue(UpdateRules.redirect("https://release-assets.githubusercontent.com/github-production-release-asset/123?sig=test"))
        assertFalse(UpdateRules.redirect("https://127.0.0.1/payload"));assertFalse(UpdateRules.redirect("https://evil.githubusercontent.com/payload"));assertFalse(UpdateRules.valid(release.copy(bytes=600000000)))
    }
    @Test fun checksumAndWorkerPayloadAreBounded() {
        val file=File.createTempFile("update-integrity",".apk")
        try{file.writeText("abc");assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdateRules.digest(file));file.appendText("x");assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdateRules.digest(file))}finally{file.delete()}
        val notes=release.copy(notes="a".repeat(12000));assertTrue(UpdateRules.valid(notes));assertTrue(AppJson.encodeToString(notes.copy(notes="")).toByteArray().size<10000)
    }
    @Test fun csvHandlesExportifyQuotingBomNewlinesAndMilliseconds() {
        val csv="\uFEFFTrack Name,Artist Name(s),Duration (ms)\r\n\"Song, one\",Artist,180000\r\n\"Two \"\"words\"\"\nline\",Singer,120500"
        val list=ImportRules.parse(csv)
        assertEquals(2,list.songs.size);assertEquals("Song, one",list.songs[0].title);assertEquals(180L,list.songs[0].duration);assertEquals("Two \"words\"\nline",list.songs[1].title)
        assertEquals(2,ImportRules.parse("title;artist\nA;B\nC;D").songs.size)
        assertTrue(runCatching {ImportRules.csv("\"unfinished")}.isFailure)
    }
    @Test fun textJsonAndM3uKeepMetadataWithoutReadingLocalPaths() {
        assertEquals(ImportSong("Judul lagu","Artis"),ImportRules.parse("Artis - Judul lagu").songs.single())
        assertEquals(ImportSong("Song","Singer",200),ImportRules.parse("""{"items":[{"track":{"name":"Song","artists":[{"name":"Singer"}],"duration_ms":200000}}]}""").songs.single())
        val m3u="#EXTM3U\n#EXTINF:123,Artist - Track\nC:/private/audio.mp3\nhttps://unknown.example/private/audio"
        assertEquals(listOf(ImportSong("Track","Artist")),ImportRules.parse(m3u).songs)
    }
    @Test fun importerDoesNotAutomaticallyAcceptDifferentArtistOrCover() {
        val song=ImportSong("Lagu","Penyanyi",180);val exact=Track("a","Lagu (Official Audio)","Penyanyi - Topic",duration=181,url="https://www.youtube.com/watch?v=a")
        assertTrue(ImportRules.score(song,exact)>.98)
        assertEquals(0.0,ImportRules.score(song,exact.copy(artist="Orang lain")),0.0)
        assertEquals(0.0,ImportRules.score(song,exact.copy(title="Lagu cover")),0.0)
        assertEquals(0.0,ImportRules.score(song,exact.copy(duration=250)),0.0)
        assertTrue(ImportRules.score(song.copy(artist=""),exact)<.88)
    }
    @Test fun smartQueueVariesArtistsExcludesRecordingDuplicatesAndPreservesInputOrder() {
        fun track(id:String,title:String,artist:String)=Track(id,title,artist,duration=180,url="https://www.youtube.com/watch?v=$id")
        val seed=track("seed","Seed","Artist A")
        val manual=listOf(seed,track("manual","Manual pick","Artist B"))
        val related=listOf(track("a","First","Artist B"),track("b","Second","Artist C"),track("c","Third","Artist B"),track("d","Fourth","Artist D"),track("old","Recent","Artist E"),track("copy","Manual pick (Official Audio)","Artist B"))
        val snapshot=manual.toList();val result=QueueRules.smart(seed,manual,related,listOf("old"))
        assertEquals(snapshot,manual);assertFalse(result.any {it.id in setOf("old","copy","seed","manual")})
        assertNotEquals("Artist B",result.first().artist);result.zipWithNext().forEach {(a,b)->assertNotEquals(a.artist,b.artist)}
        assertEquals(0,QueueRules.smart(seed,manual,related,limit=0).size)
    }
    @Test fun addedRemixPresetsHaveDistinctRealParametersAndRoomsKeepOriginalClock() {
        assertTrue(RemixRules.presets.size>=8)
        for(preset in RemixRules.presets){val p=RemixRules.parameters(Settings(remix=preset.id),false);assertTrue(p.speed in .75f..2f);assertTrue(p.pitch in .4f..2.5f);assertEquals(1f,RemixRules.parameters(Settings(remix=preset.id),true).speed,0f)}
        assertEquals(1f,RemixRules.parameters(Settings(remix="practice"),false).pitch,0f)
        assertTrue(RemixRules.hall(Settings(remix="concert"),false));assertFalse(RemixRules.hall(Settings(remix="concert"),true))
    }
    @Test fun languagesAndLyricThemesStaySelectableWithoutNetwork() {
        assertTrue(TranslationLanguages.codes.size>100);assertTrue(TranslationLanguages.valid("mni-Mtei"));assertFalse(TranslationLanguages.valid("id&tl=en"))
        assertTrue(TranslationLanguages.label("id").isNotBlank());assertTrue(LyricsVisuals.styles.size>=7)
        val base=iswaraColors("dark","orange",true)
        LyricsVisuals.themes.forEach {(theme,_)->val scheme=LyricsVisuals.scheme(theme,base,"track");val a=scheme.onSurface.luminance();val b=scheme.surface.luminance();assertTrue((maxOf(a,b)+.05)/(minOf(a,b)+.05)>=4.5)}
    }
}
