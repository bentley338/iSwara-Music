package id.iswara.music

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import id.iswara.music.cloud.*
import id.iswara.music.ui.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AdminContentUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun previewNeverPublishesAndAllContentActionsHaveIndependentHandlers() {
        var preview by mutableStateOf(false)
        var published by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var edits=0; var deletes=0; var clones=0; var publishes=0
        ui.setContent {IswaraTheme("dark","id") {
            val row = buildJsonObject {put("title","Pengumuman pengujian");put("body","Isi pratinjau");put("position",0);put("published",published)}
            Surface {Column(Modifier.fillMaxWidth()) {AdminContentRow(row,enabled,{edits++},{deletes++},{preview=true},{published=!published;publishes++},{assertFalse(AdminTools.draft(false,row).flag("published"));clones++})}}
            if(preview) AdminPreview(false,row) {preview=false}
        }}
        ui.onNodeWithText("Pratinjau").performClick()
        ui.onNodeWithText("Pratinjau Beranda").assertExists()
        assertEquals(0,publishes)
        ui.onNodeWithText("Tutup pratinjau").performClick()
        ui.onNodeWithText("Edit").performClick()
        ui.onNodeWithText("Duplikasi ke draf").performClick()
        ui.onNodeWithText("Terbitkan").performClick()
        ui.onNodeWithText("Sembunyikan").assertExists()
        ui.onNodeWithText("Hapus").performClick()
        assertEquals(1,edits);assertEquals(1,clones);assertEquals(1,publishes);assertEquals(1,deletes)
        ui.runOnUiThread {enabled=false}
        listOf("Edit","Hapus","Pratinjau","Sembunyikan","Duplikasi ke draf").forEach {ui.onNodeWithText(it).assertIsNotEnabled()}
    }
}
