package id.iswara.music.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

data class ArtworkPalette(val dominant: Int, val companion: Int)
data class PlayerPalette(val background: Int, val glow: Int, val accent: Int, val onAccent: Int)

object ArtworkColors {
    fun extract(pixels: IntArray): ArtworkPalette? {
        if (pixels.isEmpty()) return null
        data class Bucket(var count: Int = 0, var r: Long = 0, var g: Long = 0, var b: Long = 0)
        val buckets = mutableMapOf<Int, Bucket>()
        val hsl = FloatArray(3)
        pixels.forEach { pixel ->
            if (Color.alpha(pixel) < 128) return@forEach
            ColorUtils.colorToHSL(pixel, hsl)
            // Border blacks, paper whites and tiny bright details should not set the whole player color.
            if (hsl[2] < .07f || hsl[2] > .94f) return@forEach
            val key = (Color.red(pixel) shr 5 shl 6) or (Color.green(pixel) shr 5 shl 3) or (Color.blue(pixel) shr 5)
            buckets.getOrPut(key) { Bucket() }.apply { count++; r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel) }
        }
        val colors = buckets.values.map { b ->
            val color = Color.rgb((b.r / b.count).toInt(), (b.g / b.count).toInt(), (b.b / b.count).toInt())
            ColorUtils.colorToHSL(color, hsl)
            color to b.count * (.35f + hsl[1]) * (1f - abs(hsl[2] - .48f) * .6f)
        }.sortedByDescending { it.second }
        val fallback = pixels.firstOrNull { Color.alpha(it) >= 128 }?.let { ColorUtils.setAlphaComponent(it, 255) } ?: return null
        val dominant = colors.firstOrNull()?.first ?: return ArtworkPalette(fallback, fallback)
        val mainHsl = FloatArray(3); ColorUtils.colorToHSL(dominant, mainHsl)
        val companion = colors.firstOrNull { (color, score) ->
            ColorUtils.colorToHSL(color, hsl)
            val distance = abs(hsl[0] - mainHsl[0]).let { minOf(it, 360f - it) }
            score >= colors.first().second * .15f && distance in 25f..150f
        }?.first ?: dominant
        return ArtworkPalette(dominant, companion)
    }

    fun player(palette: ArtworkPalette?, surface: Int, fallback: Int): PlayerPalette {
        val dark = ColorUtils.calculateLuminance(surface) < .4
        val background = ColorUtils.blendARGB(surface, palette?.dominant ?: surface, if (dark) .18f else .08f)
        val glow = ColorUtils.blendARGB(surface, palette?.companion ?: surface, if (dark) .30f else .13f)
        var accent = palette?.dominant ?: fallback
        val target = if (dark) Color.WHITE else Color.BLACK
        repeat(24) {
            if (minOf(ColorUtils.calculateContrast(accent, background), ColorUtils.calculateContrast(accent, glow)) < 4.5)
                accent = ColorUtils.blendARGB(accent, target, .13f)
        }
        val onAccent = if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 4.5) Color.WHITE else Color.BLACK
        return PlayerPalette(background, glow, accent, onAccent)
    }
}
