package id.iswara.music

import id.iswara.music.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class)
class UpdateTransportTest {
    @Test fun downloadRejectsOversizeBadChecksumUntrustedRedirectAndNonApkAndCleansTemporaryFiles()=runBlocking {
        val app=RuntimeEnvironment.getApplication() as IswaraApp;val repo=AppUpdates(app,app.auth,app.preferences)
        val body="not an APK".toByteArray();val hash=MessageDigest.getInstance("SHA-256").digest(body).joinToString(""){"%02x".format(it)}
        var redirect=false
        val client=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture").code(if(redirect)302 else 200).apply {if(redirect)header("Location","https://evil.example/app.apk")}.body(body.toResponseBody("application/octet-stream".toMediaType())).build()}.build()
        repo.javaClass.getDeclaredField("client").apply {isAccessible=true}.set(repo,client)
        val release=AppRelease(BuildConfig.VERSION_CODE+1,"next","https://github.com/owner/iswara/releases/download/next/app.apk",hash,body.size.toLong())
        assertTrue(runCatching {repo.fetch(release.copy(bytes=2)) {}}.exceptionOrNull()?.message.orEmpty().contains("Ukuran unduhan"))
        assertTrue(runCatching {repo.fetch(release.copy(sha256="a".repeat(64))) {}}.exceptionOrNull()?.message.orEmpty().contains("tidak utuh"))
        assertTrue(runCatching {repo.fetch(release) {}}.exceptionOrNull()?.message.orEmpty().contains("APK"))
        redirect=true;assertTrue(runCatching {repo.fetch(release) {}}.exceptionOrNull()?.message.orEmpty().contains("Tautan unduhan"))
        assertFalse(repo.state.value.ready)
        assertTrue(File(app.filesDir,"updates").listFiles().orEmpty().none {it.extension in setOf("part","apk")})
    }
}
