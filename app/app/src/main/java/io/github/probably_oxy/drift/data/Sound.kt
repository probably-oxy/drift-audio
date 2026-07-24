package io.github.probably_oxy.drift.data

enum class SoundType { REC, SYN }

enum class License { CC0, PUBLIC_DOMAIN, CC_BY_3_0, CC_BY_4_0, ORIGINAL }

data class LicenseInfo(
    val license: License,
    val author: String? = null,
    val url: String? = null,
    /** Exact credit string to show verbatim; null = no attribution required. */
    val attribution: String? = null,
)

/**
 * What a [Variant] changes when selected.
 * - [MODE]: a different character/location for the sound (e.g. Rain's "openair" vs
 *   "below") — not yet wired to playback, reserved for later.
 * - [FREQUENCY]: how often the sound plays vs. falls silent between segments.
 *   [Variant.minGapMs]/[Variant.maxGapMs] are only meaningful for this kind; a
 *   silent gap of that random length is inserted between segments instead of an
 *   immediate crossfade. Zero/zero means continuous (today's default behaviour).
 */
enum class VariantKind { MODE, FREQUENCY }

/** One selectable mode of a sound, e.g. Rain's ("openair", "AIR"). */
data class Variant(
    val id: String,
    val label: String = id.uppercase(),
    val kind: VariantKind = VariantKind.MODE,
    val minGapMs: Long = 0L,
    val maxGapMs: Long = 0L,
)

/**
 * Placeholder for the future 3-axis tag system (decided, not yet designed).
 * Empty on every sound until the tagging phase.
 */
data class SoundTags(
    val texture: List<String> = emptyList(),
    val mood: List<String> = emptyList(),
    val environment: List<String> = emptyList(),
)

/**
 * One catalogue entry. Pure metadata: no playback state lives here.
 * Deliberately has no category field — grouping comes from tags later.
 */
data class Sound(
    val id: String,
    val name: String,
    val description: String,
    val type: SoundType,
    val license: LicenseInfo,
    /** Number of audio files backing this sound; 0 for SYN sounds (no files yet). */
    val segmentCount: Int,
    val variants: List<Variant> = emptyList(),
    val defaultVariantId: String? = null,
    val tags: SoundTags = SoundTags(),
    /**
     * Linear gain multiplier applied on top of the user's layer volume, to
     * align this sound's base loudness with the rest of the catalogue (files
     * were normalised at encode time but single-pass loudnorm undershoots
     * unpredictably on high-dynamic-range sources — see Catalogue.kt for the
     * measured values this is derived from). 1.0 = no change.
     */
    val gainTrim: Float = 1f,
)
