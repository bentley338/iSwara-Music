package id.iswara.music.source

import java.util.Locale

object TranslationLanguages {
    val codes="id en ja ko zh-CN zh-TW ar de es fr it pt ru nl tr vi th ms jw su ban min hi bn ta te mr ur fa he uk pl sv da no fi el cs sk hu ro bg sr hr sl sq mk lt lv et ka hy az kk ky uz tg mn ne si my km lo fil ceb sw af am ha ig yo zu xh st sn so om ps sd pa gu kn ml or as bo ug tk tt ba be bs ca eu gl cy ga gd is mt lb la eo ht haw sm mi fj mg ny rw rn ti ts tn ve nso ach ak bm ee fon gom ilo kl kri ku ckb lg ln mai mfe mni-Mtei mtq pap qu sa sat seh scn tet tpi tum yue yi zgh".split(' ').distinct()
    fun valid(code:String)=code.matches(Regex("[a-z]{2,4}(?:-[A-Za-z]{2,8})?")) && code!="auto"
    fun label(code:String,english:Boolean=false):String = when(code){"auto"->if(english)"Auto detect"else "Deteksi otomatis";"jw"->"Jawa";"su"->"Sunda";"ban"->"Bali";"min"->"Minang";"zh-CN"->if(english)"Chinese (Simplified)"else "Mandarin (Sederhana)";"zh-TW"->if(english)"Chinese (Traditional)"else "Mandarin (Tradisional)";else->Locale.forLanguageTag(code).getDisplayLanguage(if(english)Locale.ENGLISH else Locale.forLanguageTag("id")).replaceFirstChar {it.titlecase()}.takeUnless {it.equals(code,true)} ?: code}
}
