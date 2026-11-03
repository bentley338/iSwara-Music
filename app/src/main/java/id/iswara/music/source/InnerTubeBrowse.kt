package id.iswara.music.source

import id.iswara.music.model.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Browse-only adapter; search and stream extraction remain with NewPipe. */
class InnerTubeBrowse {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    @Volatile private var version: String? = null
    private fun clientVersion(): String {
        version?.let { return it }
        val html = client.newCall(Request.Builder().url("https://music.youtube.com/").build()).execute().use { it.body?.string().orEmpty() }
        val value = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: error("Format katalog musik berubah. Perbarui aplikasi.")
        version = value
        return value
    }
    fun browse(id: String): CatalogPage {
        require(id.matches(Regex("[A-Za-z0-9_-]{5,200}")))
        val body = buildJsonObject {
            putJsonObject("context") { putJsonObject("client") { put("clientName", "WEB_REMIX"); put("clientVersion", clientVersion()); put("hl", "en"); put("gl", "ID") } }
            put("browseId", id)
        }
        val root = client.newCall(Request.Builder().url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false").header("Origin", "https://music.youtube.com").post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
            check(r.isSuccessful) { "Halaman album/artis belum tersedia (${r.code})" }
            AppJson.parseToJsonElement(r.body!!.string())
        }
        val items = mutableListOf<CatalogItem>()
        fun walk(element: JsonElement, depth: Int = 0) {
            if (depth > 40) return
            when (element) {
                is JsonObject -> {
                    val renderer = (element["musicResponsiveListItemRenderer"] ?: element["musicTwoRowItemRenderer"]) as? JsonObject
                    if (renderer != null) parse(renderer)?.let(items::add)
                    element.values.forEach { walk(it, depth + 1) }
                }
                is JsonArray -> element.forEach { walk(it, depth + 1) }
                else -> Unit
            }
        }
        walk(root)
        return CatalogPage(items.distinctBy { it.id })
    }
    private fun parse(r: JsonObject): CatalogItem? {
        fun find(node: JsonElement?, key: String): JsonElement? {
            if (node is JsonObject) { node[key]?.let { return it }; node.values.forEach { find(it, key)?.let { result -> return result } } }
            if (node is JsonArray) node.forEach { find(it, key)?.let { result -> return result } }
            return null
        }
        fun text(node: JsonElement?): String = when (node) {
            is JsonObject -> node["text"]?.jsonPrimitive?.contentOrNull ?: node["runs"]?.let(::text) ?: node.values.joinToString("") { text(it) }
            is JsonArray -> node.joinToString("") { text(it) }
            else -> ""
        }
        val columns = r["flexColumns"] as? JsonArray
        val title = text(r["title"] ?: columns?.getOrNull(0)).takeIf { it.isNotBlank() } ?: return null
        val artist = text(r["subtitle"] ?: columns?.getOrNull(1))
        val thumbnail = (find(r["thumbnailRenderer"] ?: r["thumbnail"], "thumbnails") as? JsonArray)?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content.orEmpty()
        val video = find(r["playlistItemData"], "videoId")?.jsonPrimitive?.contentOrNull ?: find(r["navigationEndpoint"], "videoId")?.jsonPrimitive?.contentOrNull
        if (video != null) {
            val track = Track(video, title, artist, thumbnail, url = "https://www.youtube.com/watch?v=$video")
            return CatalogItem(video, title, artist, thumbnail, track.url, "song", track)
        }
        val browse = find(r["navigationEndpoint"], "browseId")?.jsonPrimitive?.contentOrNull ?: return null
        return CatalogItem(browse, title, artist, thumbnail, "https://music.youtube.com/browse/$browse", if (browse.startsWith("UC")) "artist" else "album")
    }
}
