package id.iswara.music

import id.iswara.music.cloud.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AdminToolsTest {
    private val row = buildJsonObject {
        put("id","original"); put("created_by","owner"); put("title","Judul " + "a".repeat(100))
        put("body","Isi pengumuman"); put("url","https://music.youtube.com/playlist?list=fixture")
        put("artwork","https://example.com/cover.jpg"); put("published",true);put("position",20)
    }
    @Test fun duplicationIsAlwaysANewDraftAndCannotCopyIdentityOrCreator() {
        val notice = AdminTools.draft(false,row)
        assertFalse(notice.flag("published")); assertFalse("id" in notice); assertFalse("created_by" in notice)
        assertEquals(80,notice.text("title").length); assertEquals(row.text("body"),notice.text("body"))
        val featured = AdminTools.draft(true,row)
        assertEquals(100,featured.text("title").length); assertEquals(row.text("url"),featured.text("url"))
        assertFalse(featured.flag("published")); assertFalse("body" in featured)
        assertTrue(AdminTools.matches(row,"isi PENGUMUMAN","published"))
        assertFalse(AdminTools.matches(row,"isi","draft"))
    }
    @Test fun auditExportQuotesCellsNeutralizesSpreadsheetFormulasAndHandlesEmptyPages() {
        val entry = buildJsonObject {put("action","  =IMPORTXML(\"bad\")");put("entity","line1\nline2");put("detail",buildJsonObject {put("result","success")})}
        val csv = AdminTools.auditCsv(listOf(entry))
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"'  =IMPORTXML(\"\"bad\"\")\""))
        assertTrue(csv.contains("\"line1\nline2\"")); assertTrue(csv.contains("\"success\""))
        assertEquals(1,AdminTools.auditCsv(emptyList()).split("\r\n").filter(String::isNotEmpty).size)
    }
}
