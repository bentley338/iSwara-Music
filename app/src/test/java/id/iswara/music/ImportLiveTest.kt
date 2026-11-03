package id.iswara.music

import id.iswara.music.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class)
class ImportLiveTest {
    @Test fun publicSpotifyMetadataAndRealMatchingCreateOnePlaylistAndClearAcrossOwners()=runBlocking {
        assumeTrue(System.getenv("ISWARA_LIVE_TEST")=="1")
        val app=RuntimeEnvironment.getApplication() as IswaraApp
        val importer=PlaylistImporter(YoutubeMusicSource(),app.library)
        val public=importer.loadLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",3)
        assertTrue(public.songs.size in 1..3);assertTrue(public.songs.all {it.artist.isNotBlank() && it.title.isNotBlank() && it.duration>0});assertTrue(public.warning.isNotBlank())
        delay(100)
        importer.preview("Tulus - Hati Hati di Jalan",false,1)
        withTimeout(45_000){while(importer.state.value.loading || importer.state.value.matching){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();delay(50)}}
        val state=importer.state.value;assertNull(state.error);assertEquals(1,state.rows.size);assertTrue(state.rows.single().candidates.isNotEmpty())
        val chosen=state.rows.single().candidates.first();importer.choose(0,chosen)
        importer.save("Playlist impor pengujian")
        val library=app.library.snapshot(app.library.scope.value);assertEquals(listOf(chosen.id),library.playlists.single {it.title=="Playlist impor pengujian"}.tracks.map {it.id})
        assertTrue(runCatching {importer.save("Duplicate")}.isFailure)
        app.library.scope.value="another-test-owner"
        withTimeout(5000){while(importer.state.value.owner!="another-test-owner"){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();delay(20)}}
        assertTrue(importer.state.value.rows.isEmpty());assertNull(importer.state.value.saved)
    }
}
