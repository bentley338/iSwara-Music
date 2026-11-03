package id.iswara.music.cloud

import kotlinx.serialization.json.*

object AdminTools {
    fun draft(featured: Boolean, row: JsonObject): JsonObject = buildJsonObject {
        val suffix = " (salinan)"
        val limit = if (featured) 100 else 80
        put("title", row.text("title").take(limit-suffix.length) + suffix)
        put(if(featured) "subtitle" else "body",row.text(if(featured) "subtitle" else "body"))
        if (featured) { put("url",row.text("url")); put("artwork",row.text("artwork")) }
        put("position",row.text("position").toIntOrNull()?.coerceIn(0,1000) ?: 0)
        put("published",false)
    }
    fun matches(row: JsonObject, query: String, status: String): Boolean {
        if (status == "published" && !row.flag("published")) return false
        if (status == "draft" && row.flag("published")) return false
        val text = "${row.text("title")} ${row.text("body")} ${row.text("subtitle")}".lowercase(java.util.Locale.ROOT)
        return query.trim().lowercase(java.util.Locale.ROOT).split(Regex("\\s+")).all { it in text }
    }
    private fun csv(value: String): String {
        val unsafe = value.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')
        return "\"" + (if(unsafe) "'" else "") + value.replace("\"","\"\"") + "\""
    }
    fun auditCsv(rows: List<JsonObject>): String = "\uFEFF" +
        listOf(listOf("waktu","tindakan","jenis","id_konten","pelaku","target","hasil").joinToString(",",transform=::csv))
            .plus(rows.map { row -> listOf(row.text("created_at"),row.text("action"),row.text("entity"),row.text("entity_id"),row.text("actor"),row.text("target_user"),(row["detail"] as? JsonObject)?.text("result").orEmpty()).joinToString(",",transform=::csv) })
            .joinToString("\r\n") + "\r\n"
}
