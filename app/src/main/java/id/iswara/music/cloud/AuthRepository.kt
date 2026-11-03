package id.iswara.music.cloud

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class Account(val id: String, val email: String)
class AccountServiceException(message: String, val status: Int) : java.io.IOException(message)

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    val configured = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    val account = MutableStateFlow<Account?>(null)
    private var session: JsonObject? = read()
    init { session?.let { account.value = user(it) } }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("iswara.session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("iswara.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JsonObject? = runCatching {
        json.parseToJsonElement(decrypt(preferences.getString("session", null) ?: return null)).jsonObject
    }.getOrNull()
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun pkce(recovery: Boolean): String {
        val verifier = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        preferences.edit().putString("pkce", encrypt(verifier)).putLong("pkceCreated", System.currentTimeMillis()).putBoolean("pkceRecovery", recovery).commit()
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    private fun user(value: JsonObject): Account? = value["user"]?.jsonObject?.let { u -> Account(u["id"]!!.jsonPrimitive.content, u["email"]?.jsonPrimitive?.content.orEmpty()) }
    private fun save(value: JsonObject?) {
        // GoTrue REST responses commonly supply expires_in rather than expires_at.
        // Persist an absolute expiry to avoid refreshing on every sync operation.
        val stored = value?.withAbsoluteExpiry(System.currentTimeMillis() / 1000)
        session = stored
        account.value = value?.let(::user)
        if (value == null) preferences.edit().clear().commit() else {
            preferences.edit().putString("session", encrypt(stored.toString())).commit()
        }
    }
    suspend fun request(path: String, body: JsonElement? = null, token: String? = null, method: String = "POST", prefer: String? = null): JsonElement = withContext(Dispatchers.IO) {
        check(configured) { "Layanan akun belum tersedia. Musik tetap bisa digunakan sebagai tamu." }
        val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
            .apply { prefer?.let { header("Prefer",it) } }
            .method(method, if (method == "GET" || method == "DELETE") null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val result = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
            if (!r.isSuccessful) {
                val obj = result as? JsonObject
                val message = obj?.get("msg") ?: obj?.get("message") ?: obj?.get("error_description")
                throw AccountServiceException(message?.jsonPrimitive?.content ?: "Layanan akun gagal (${r.code})", r.code)
            }
            result
        }
    }
    suspend fun login(email: String, password: String) = lock.withLock {
        save(request("/auth/v1/token?grant_type=password", buildJsonObject { put("email", email.trim()); put("password", password) }).jsonObject)
    }
    suspend fun register(email: String, password: String) = lock.withLock {
        require(password.length >= 8) { "Password minimal 8 karakter" }
        val challenge = pkce(false)
        val result = request("/auth/v1/signup?redirect_to=iswara%3A%2F%2Fauth", buildJsonObject { put("email", email.trim()); put("password", password); put("code_challenge", challenge); put("code_challenge_method", "s256") }).jsonObject
        if (result["access_token"] != null) save(result)
    }
    suspend fun reset(email: String) = lock.withLock { val challenge = pkce(true); request("/auth/v1/recover?redirect_to=iswara%3A%2F%2Fauth", buildJsonObject { put("email", email.trim()); put("code_challenge", challenge); put("code_challenge_method", "s256") }); Unit }
    suspend fun token(): String = lock.withLock {
        val current = session ?: error("Masuk untuk menyinkronkan koleksi")
        val expires = current["expires_at"]?.jsonPrimitive?.longOrNull ?: 0
        if (expires > System.currentTimeMillis() / 1000 + 60) return@withLock current["access_token"]!!.jsonPrimitive.content
        val refreshed = try { request("/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", current["refresh_token"]!!.jsonPrimitive.content) }).jsonObject }
        catch (e: AccountServiceException) { if (e.status == 400 || e.status == 401) save(null); throw e }
        save(refreshed)
        refreshed["access_token"]!!.jsonPrimitive.content
    }
    suspend fun logout() { val t = runCatching { token() }.getOrNull(); if (t != null) runCatching { request("/auth/v1/logout?scope=local", token = t) }; lock.withLock { save(null) } }
    suspend fun delete() { request("/functions/v1/delete-account", token = token()); lock.withLock { save(null) } }
    suspend fun updatePassword(password: String) { require(password.length >= 8); request("/auth/v1/user", buildJsonObject { put("password", password) }, token(), "PUT") }
    suspend fun callback(uri: Uri): Boolean = lock.withLock {
        require(uri.scheme == "iswara" && uri.host == "auth")
        uri.getQueryParameter("error_description")?.let { error(it) }
        val code = uri.getQueryParameter("code") ?: error("Tautan akun tidak valid atau sudah kedaluwarsa")
        val encryptedVerifier = preferences.getString("pkce", null) ?: error("Buka tautan pada perangkat yang meminta email verifikasi atau reset.")
        check(System.currentTimeMillis() - preferences.getLong("pkceCreated", 0) < 30 * 60_000) { "Tautan kedaluwarsa. Minta email baru." }
        val recovery = preferences.getBoolean("pkceRecovery", false)
        val value = request("/auth/v1/token?grant_type=pkce", buildJsonObject { put("auth_code", code); put("code_verifier", decrypt(encryptedVerifier)) }).jsonObject
        save(value)
        preferences.edit().remove("pkce").remove("pkceCreated").remove("pkceRecovery").commit()
        recovery
    }
}
