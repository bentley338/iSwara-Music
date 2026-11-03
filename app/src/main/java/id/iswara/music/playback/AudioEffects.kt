package id.iswara.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import id.iswara.music.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EffectsState(val supported: Boolean = false, val frequencies: List<Int> = emptyList(), val message: String = "Mulai putar lagu untuk mengatur suara.")
@Singleton class AudioEffects @Inject constructor() {
    val state = MutableStateFlow(EffectsState())
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var reverb: PresetReverb? = null
    private var session = 0
    private var attempted = false
    val reverbId get() = runCatching { reverb?.id ?: 0 }.getOrDefault(0)
    fun attach(id: Int) {
        if (id <= 0) { release(); return }
        if (id == session) return
        release(); session = id
        state.value = EffectsState(message = "Aktifkan equalizer untuk mengatur suara.")
    }
    fun apply(settings: Settings) {
        if (!settings.equalizer) { releaseEffects(); attempted = false; return }
        if (session <= 0) return
        if (!attempted) {
            attempted = true
            eq = runCatching { Equalizer(0, session) }.getOrNull()
            bass = runCatching { BassBoost(0, session) }.getOrNull()
            state.value = runCatching { eq?.let { effect -> EffectsState(true, (0 until effect.numberOfBands).map { effect.getCenterFreq(it.toShort()) / 1000 }, "") } }.getOrNull()
                ?: EffectsState(message = "Equalizer tidak didukung keluaran audio ini.")
        }
        if (settings.reverb != "none" && reverb == null) reverb = runCatching { PresetReverb(0, 0) }.getOrNull()
        runCatching {
            eq?.let { effect ->
                val range = effect.bandLevelRange
                val values = presets[settings.eqPreset] ?: settings.eqBands
                repeat(effect.numberOfBands.toInt()) { index ->
                    val mapped = (index * (values.size - 1).coerceAtLeast(0) / (effect.numberOfBands - 1).coerceAtLeast(1)).coerceIn(values.indices)
                    effect.setBandLevel(index.toShort(), (values[mapped] * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
                effect.enabled = settings.equalizer
            }
            bass?.let { it.setStrength((settings.bass.toIntOrNull() ?: 0).coerceIn(0, 1000).toShort()); it.enabled = settings.equalizer && settings.bass != "0" }
            reverb?.let { it.preset = when (settings.reverb) { "room" -> PresetReverb.PRESET_SMALLROOM; "hall" -> PresetReverb.PRESET_LARGEHALL; else -> PresetReverb.PRESET_NONE }; it.enabled = settings.equalizer && settings.reverb != "none" }
        }.onFailure { state.value = state.value.copy(message = "Efek ini tidak tersedia untuk keluaran audio sekarang.") }
    }
    private fun releaseEffects() { runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { reverb?.release() }; eq = null; bass = null; reverb = null }
    fun release() { releaseEffects(); session = 0; attempted = false }
    companion object {
        val presets = linkedMapOf("Flat" to listOf(0,0,0,0,0), "Rock" to listOf(4,2,-1,2,4), "Pop" to listOf(-1,2,4,2,-1), "EDM" to listOf(5,3,0,2,4), "Bass Boost" to listOf(6,4,1,0,0), "Vocal" to listOf(-2,0,4,3,0))
    }
}
