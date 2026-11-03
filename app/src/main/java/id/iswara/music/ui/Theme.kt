package id.iswara.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import android.animation.ValueAnimator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import id.iswara.music.R

val LocalEnglish = staticCompositionLocalOf { false }
val LocalAppMotion = staticCompositionLocalOf { true }
@Composable fun tr(id: String, en: String): String = if (LocalEnglish.current) en else id
val Orange = Color(0xFFFF8954)
private val Jakarta = FontFamily(Font(R.font.plus_jakarta_sans))
private val Dark = darkColorScheme(primary = Orange, onPrimary = Color(0xFF271307), primaryContainer = Color(0xFF4B2A1A), onPrimaryContainer = Color(0xFFFFDCC5), secondary = Color(0xFFE8C1A7), onSecondary = Color(0xFF3D281B), secondaryContainer = Color(0xFF3A2C24), onSecondaryContainer = Color(0xFFFFE0D0), background = Color(0xFF151619), onBackground = Color(0xFFFFF8F2), surface = Color(0xFF151619), onSurface = Color(0xFFFFF8F2), surfaceContainerLowest = Color(0xFF101114), surfaceContainerLow = Color(0xFF1D1F24), surfaceContainer = Color(0xFF23252B), surfaceContainerHigh = Color(0xFF2B2D35), surfaceContainerHighest = Color(0xFF353741), surfaceVariant = Color(0xFF353741), onSurfaceVariant = Color(0xFFD3CDC6), outline = Color(0xFF9E9690), outlineVariant = Color(0xFF514A45), surfaceTint = Orange, inverseSurface = Color(0xFFFFF8F2), inverseOnSurface = Color(0xFF25201C), inversePrimary = Color(0xFF993F15), error = Color(0xFFFFB4AB))
private val Light = lightColorScheme(primary = Color(0xFF993F15), onPrimary = Color.White, primaryContainer = Color(0xFFFFE4D3), onPrimaryContainer = Color(0xFF331708), secondary = Color(0xFF75533C), onSecondary = Color.White, secondaryContainer = Color(0xFFEFE4DB), onSecondaryContainer = Color(0xFF3D3028), background = Color(0xFFFAF7F2), onBackground = Color(0xFF25201C), surface = Color(0xFFFAF7F2), onSurface = Color(0xFF25201C), surfaceContainerLowest = Color(0xFFFFFCF7), surfaceContainerLow = Color(0xFFF6F0E8), surfaceContainer = Color(0xFFF0E9DF), surfaceContainerHigh = Color(0xFFE9E1D7), surfaceContainerHighest = Color(0xFFE2D8CC), surfaceVariant = Color(0xFFE2D8CC), onSurfaceVariant = Color(0xFF62584F), outline = Color(0xFF746C65), outlineVariant = Color(0xFFBDB3AA), surfaceTint = Color(0xFF993F15), inverseSurface = Color(0xFF25201C), inverseOnSurface = Color(0xFFFFF8F2), inversePrimary = Orange)
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Jakarta, fontSize = 11.sp, lineHeight = 16.sp),
)
fun isDarkTheme(theme:String,system:Boolean)=when(theme){"dark","midnight","forest"->true;"light","dawn"->false;else->system}
fun iswaraColors(theme:String,accent:String,dark:Boolean):ColorScheme {
    val base=if(dark)Dark else Light
    val surface=when(theme) {
        "midnight"->base.copy(background=Color(0xFF101827),surface=Color(0xFF101827),surfaceContainerLowest=Color(0xFF0A1120),surfaceContainerLow=Color(0xFF162133),surfaceContainer=Color(0xFF1D293D),surfaceContainerHigh=Color(0xFF26334A),surfaceContainerHighest=Color(0xFF303D55))
        "forest"->base.copy(background=Color(0xFF10201B),surface=Color(0xFF10201B),surfaceContainerLowest=Color(0xFF0B1713),surfaceContainerLow=Color(0xFF172A22),surfaceContainer=Color(0xFF1F3229),surfaceContainerHigh=Color(0xFF293C33),surfaceContainerHighest=Color(0xFF2C3F34))
        "dawn"->base.copy(background=Color(0xFFFFF3F4),surface=Color(0xFFFFF3F4),surfaceContainerLowest=Color(0xFFFFFAFA),surfaceContainerLow=Color(0xFFFBEBEE),surfaceContainer=Color(0xFFF5E3E7),surfaceContainerHigh=Color(0xFFEEDDE1),surfaceContainerHighest=Color(0xFFE7D5DB))
        else->base
    }
    val palette=when(accent) {
        "ocean"->if(dark)longArrayOf(0xFF75D7F0,0xFF062D38,0xFF103746,0xFFC5F1FF)else longArrayOf(0xFF075D79,0xFFFFFFFF,0xFFCAEDF7,0xFF042F40)
        "mint"->if(dark)longArrayOf(0xFF8DDBB8,0xFF0A3322,0xFF123B2C,0xFFC3F7DF)else longArrayOf(0xFF145D41,0xFFFFFFFF,0xFFC6EEDC,0xFF073524)
        "rose"->if(dark)longArrayOf(0xFFFFB0C6,0xFF4A1729,0xFF542538,0xFFFFDCE6)else longArrayOf(0xFF96354F,0xFFFFFFFF,0xFFFFDBE5,0xFF3F1022)
        "lavender"->if(dark)longArrayOf(0xFFCFB9FF,0xFF30204A,0xFF43305D,0xFFEFDEFF)else longArrayOf(0xFF69439E,0xFFFFFFFF,0xFFEBDCFF,0xFF2D1547)
        "gold"->if(dark)longArrayOf(0xFFE6C76F,0xFF3C2E04,0xFF47380F,0xFFFFE8AC)else longArrayOf(0xFF755600,0xFFFFFFFF,0xFFF7E6AE,0xFF352600)
        else->null
    } ?: return surface
    return surface.copy(primary=Color(palette[0]),onPrimary=Color(palette[1]),primaryContainer=Color(palette[2]),onPrimaryContainer=Color(palette[3]),secondary=Color(palette[0]),secondaryContainer=Color(palette[2]),onSecondaryContainer=Color(palette[3]),surfaceTint=Color(palette[0]))
}
@Composable fun IswaraTheme(theme: String, language: String, accent:String="orange", animations:Boolean=true, content: @Composable () -> Unit) {
    val dark = isDarkTheme(theme,isSystemInDarkTheme())
    val colors = remember(theme,accent,dark){iswaraColors(theme,accent,dark)}
    val activity = androidx.activity.compose.LocalActivity.current
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect { activity?.window?.let { window -> androidx.core.view.WindowCompat.getInsetsController(window,view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    CompositionLocalProvider(LocalEnglish provides (language == "en"),LocalAppMotion provides (animations && ValueAnimator.areAnimatorsEnabled())) { MaterialTheme(colorScheme = colors, typography = Type) { CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content) } }
}
