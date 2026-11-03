package id.iswara.music.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class AdminAccess(val owner: String = "", val allowed: Boolean = false)
data class HomeContent(val announcements: List<JsonObject> = emptyList(), val collections: List<JsonObject> = emptyList())
fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

@Singleton class AdminRepository @Inject constructor(private val auth: AuthRepository) {
    suspend fun access(): AdminAccess {
        if (!id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED) return AdminAccess()
        val owner = auth.account.value?.id ?: return AdminAccess()
        return try {
            val allowed = auth.request("/rest/v1/rpc/is_admin", token = auth.token()).jsonPrimitive.boolean
            if (auth.account.value?.id == owner) AdminAccess(owner, allowed) else AdminAccess()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { AdminAccess(owner) }
    }
    private suspend fun token(owner: String): String {
        check(id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED) { "Admin tools are unavailable in public releases." }
        check(auth.account.value?.id == owner) { "Akun berubah. Buka ulang panel admin." }
        val token = auth.token()
        check(auth.account.value?.id == owner) { "Akun berubah." }
        check(auth.request("/rest/v1/rpc/is_admin", token = token).jsonPrimitive.boolean) { "Akses admin sudah tidak tersedia." }
        return token
    }
    suspend fun home(): HomeContent {
        if (!auth.configured) return HomeContent()
        // Published content uses the public role, including while an admin previews Home.
        suspend fun rows(table: String) = auth.request("/rest/v1/$table?select=*&published=eq.true&order=position.asc,created_at.desc&limit=12", method = "GET").jsonArray.map { it.jsonObject }
        return HomeContent(rows("app_announcements"), rows("featured_collections"))
    }
    suspend fun summary(owner: String): JsonObject = auth.request("/rest/v1/rpc/admin_summary", token = token(owner)).jsonObject
    suspend fun content(owner: String, featured: Boolean): List<JsonObject> = auth.request("/rest/v1/${table(featured)}?select=*&order=position.asc,created_at.desc&limit=100", token = token(owner), method = "GET").jsonArray.map { it.jsonObject }
    private fun table(featured: Boolean) = if (featured) "featured_collections" else "app_announcements"
    suspend fun save(owner: String, featured: Boolean, id: String?, body: JsonObject, modified: String? = null) {
        val filter = if (id == null) "" else "?id=eq.${encode(id)}" + (modified?.takeIf(String::isNotBlank)?.let { "&modified_at=eq.${encode(it)}" } ?: "")
        val result = auth.request("/rest/v1/${table(featured)}$filter", body, token(owner), if (id == null) "POST" else "PATCH", prefer="return=representation")
        check((result as? JsonArray)?.isNotEmpty() == true) { "Konten sudah berubah atau tidak tersedia. Muat ulang sebelum menyimpan." }
    }
    suspend fun remove(owner: String, featured: Boolean, id: String, modified: String? = null) {
        val filter = modified?.takeIf(String::isNotBlank)?.let { "&modified_at=eq.${encode(it)}" }.orEmpty()
        val result = auth.request("/rest/v1/${table(featured)}?id=eq.${encode(id)}$filter", token = token(owner), method = "DELETE",prefer="return=representation")
        check((result as? JsonArray)?.isNotEmpty() == true) { "Konten sudah berubah atau dihapus. Muat ulang daftar." }
    }
    suspend fun users(owner: String, query: String, page: Int): List<JsonObject> = auth.request("/functions/v1/admin-users", buildJsonObject { put("action", "list"); put("query", query.take(100)); put("page", page) }, token(owner)).jsonObject["users"]!!.jsonArray.map { it.jsonObject }
    suspend fun userAction(owner: String, user: String, action: String, confirmation: String = "") {
        auth.request("/functions/v1/admin-users", buildJsonObject { put("action", action); put("user_id", user); put("confirm_email", confirmation) }, token(owner))
    }
    suspend fun audit(owner: String): List<JsonObject> = auth.request("/rest/v1/admin_audit?select=id,action,entity,entity_id,actor,target_user,created_at,detail&order=created_at.desc,id.desc&limit=100", token = token(owner), method = "GET").jsonArray.map { it.jsonObject }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
