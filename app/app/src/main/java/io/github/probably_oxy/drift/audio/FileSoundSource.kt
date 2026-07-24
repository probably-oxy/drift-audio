package io.github.probably_oxy.drift.audio

import io.github.probably_oxy.drift.data.Sound

/**
 * A [SoundSource] backed by looping audio files in res/raw — the only v1
 * implementation. [segmentResIds] are R.raw resource references (plain Ints),
 * passed in by the engine so this class stays free of Android dependencies.
 */
class FileSoundSource(
    private val sound: Sound,
    val segmentResIds: List<Int>,
) : SoundSource {

    init {
        require(segmentResIds.size == sound.segmentCount) {
            "Sound '${sound.id}' expects ${sound.segmentCount} segments, got ${segmentResIds.size}"
        }
    }

    override val id = sound.id
    override val displayName = sound.name
    override val variants = sound.variants

    /** See [Sound.gainTrim]. */
    val gainTrim = sound.gainTrim

    override var variantId: String? = sound.defaultVariantId
        set(value) {
            require(value == null || variants.any { it.id == value }) {
                "Unknown variant '$value' for sound '$id'"
            }
            field = value
        }

    override var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }
}
