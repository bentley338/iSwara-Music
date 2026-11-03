package id.iswara.music.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

val LocalLyricsStyle=staticCompositionLocalOf {"focus"}
val LocalLyricsMotion=staticCompositionLocalOf {true}
object LyricsVisuals {
    val styles=listOf("focus" to "Fokus","slide" to "Geser","glow" to "Sorot","rise" to "Mengambang","zoom" to "Panggung","minimal" to "Tenang","spotlight" to "Spotlight")
    val themes=listOf("cover" to "Ikuti sampul","auto" to "Beda tiap lagu","stage" to "Panggung malam","ocean" to "Samudra","forest" to "Hutan","paper" to "Kertas","mono" to "Monokrom")
    fun scheme(theme:String,base:ColorScheme,id:String):ColorScheme {
        val selected=if(theme=="auto")listOf("stage","ocean","forest","paper","mono")[id.hashCode().ushr(1)%5]else theme
        return when(selected){"stage"->iswaraColors("midnight","gold",true);"ocean"->iswaraColors("midnight","ocean",true);"forest"->iswaraColors("forest","mint",true);"paper"->iswaraColors("dawn","rose",false);"mono"->iswaraColors("dark","orange",true).let {it.copy(primary=it.onSurface,onPrimary=it.surface)};else->base}
    }
}
