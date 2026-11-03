package id.iswara.music.cloud

import kotlinx.serialization.json.*

internal fun JsonObject.withAbsoluteExpiry(nowSeconds: Long): JsonObject {
    if (this["expires_at"]?.jsonPrimitive?.longOrNull != null) return this
    val ttl = this["expires_in"]?.jsonPrimitive?.longOrNull ?: return this
    return JsonObject(this + ("expires_at" to JsonPrimitive(nowSeconds + ttl.coerceAtLeast(0))))
}
