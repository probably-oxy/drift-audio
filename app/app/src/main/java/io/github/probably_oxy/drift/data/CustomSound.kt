package io.github.probably_oxy.drift.data

import kotlinx.serialization.Serializable
import kotlin.math.pow

/** One playable window of an imported file (the whole file when not split). */
@Serializable
data class CustomSegment(
    /** File name inside this sound's folder (see [SoundLibrary.fileFor]). */
    val file: String,
    val startMs: Long = 0L,
    /** null = to the end of the file. */
    val endMs: Long? = null,
)

/**
 * A sound the user imported from their own files. Persisted by [SoundLibrary];
 * exposed to the rest of the app as an ordinary [Sound] via [toSound], so the
 * mixer, presets and engine treat it exactly like a built-in.
 */
@Serializable
data class CustomSound(
    /** Always prefixed [ID_PREFIX], so it can never collide with a catalogue id. */
    val id: String,
    val name: String,
    /** Folder name under filesDir/custom/. */
    val folder: String,
    val segments: List<CustomSegment>,
    /** Loudness trim in dB, adjustable any time after import. */
    val trimDb: Float = 0f,
) {
    val gainLinear: Float get() = dbToGain(trimDb)

    fun toSound(): Sound = Sound(
        id = id,
        name = name,
        description = "Imported by you · ${segments.size} segment${if (segments.size == 1) "" else "s"}",
        type = SoundType.REC,
        license = LicenseInfo(License.USER),
        segmentCount = segments.size,
        gainTrim = gainLinear,
    )

    companion object {
        const val ID_PREFIX = "user:"

        /** Trim stepper range/step. Boost is capped at +6 dB: beyond that the
         *  soft limiter would be saturating continuously, not trimming. */
        const val TRIM_MIN_DB = -18f
        const val TRIM_MAX_DB = 6f
        const val TRIM_STEP_DB = 1.5f
    }
}

/** Decibels to a linear gain multiplier: 0 dB = 1.0, +6 dB ≈ 2.0, −6 dB ≈ 0.5. */
fun dbToGain(db: Float): Float = 10f.pow(db / 20f)
