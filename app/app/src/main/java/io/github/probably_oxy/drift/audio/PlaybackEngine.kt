package io.github.probably_oxy.drift.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import io.github.probably_oxy.drift.data.OutputMode

/**
 * The multi-layer mixer. Owns one [CrossfadeLayer] per active sound and mixes
 * them through the system audio output. Each layer hides its segment seams with
 * an equal-power crossfade (see [CrossfadeLayer]); this class is responsible for
 * the *set* of layers, global play/pause, volume, and audio focus.
 *
 * Audio focus is handled ONCE here for the whole app, not per player. With one
 * player per layer (two, with crossfade), letting each request focus would make
 * them steal it from one another (a same-app focus request sends LOSS to the
 * previous owner), so every layer player is built with handleAudioFocus = false
 * and this class owns a single [AudioFocusRequest] for the group. Layers are
 * audible only when the user wants playback ([masterPlaying]) AND we hold focus.
 *
 * [onStateChanged] fires after any change so the [MixerPlayer] can refresh what
 * the MediaSession/notification shows.
 */
class PlaybackEngine(private val context: Context) {

    private val layers = LinkedHashMap<String, CrossfadeLayer>()

    /** User intent to play the mix. Actual sound also requires [hasFocus]. */
    private var masterPlaying = false
    private var hasFocus = false

    /** 1.0 normally; lowered while another app transiently ducks us. */
    private var duckFactor = 1f

    /** When true the mix is silenced (effective volume 0) but every layer keeps
     *  running and the transport stays "playing" — a volume mute, not a pause. */
    private var muted = false

    /** Called on the app main thread after any state change. */
    var onStateChanged: (() -> Unit)? = null

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Shared audio session id across every layer (harmless bookkeeping for any
    // system-level tools that key off it; no longer load-bearing for voicing,
    // which runs per-deck via VoicingAudioProcessor — see CrossfadeLayer).
    private val audioSessionId = audioManager.generateAudioSessionId()
    private var outputMode = OutputMode.SPEAKER

    /** SPEAKER applies the bass-lift/treble-tame voicing; STEREO/PHONES are flat. */
    private val eqEnabled: Boolean get() = outputMode == OutputMode.SPEAKER

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                PlatformAudioAttributes.Builder()
                    .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                    .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onFocusChange)
            .build()

    // ── Public state the MixerPlayer reads ──────────────────────────────────

    /** True when the mix is audibly playing right now. */
    val isPlaying: Boolean get() = masterPlaying && hasFocus && layers.isNotEmpty()

    /** True while the mix is silenced by the mute toggle (layers still running). */
    val isMuted: Boolean get() = muted

    /** True when at least one layer is selected (mix is non-empty). */
    val hasLayers: Boolean get() = layers.isNotEmpty()

    fun isLayerActive(id: String): Boolean = layers.containsKey(id)

    fun activeLayerIds(): Set<String> = layers.keys.toSet()

    // ── Layer management ────────────────────────────────────────────────────

    /** Add the layer if absent, remove it if present. Returns now-active?. */
    fun toggleLayer(source: FileSoundSource): Boolean {
        return if (layers.containsKey(source.id)) {
            removeLayer(source.id)
            false
        } else {
            addLayer(source)
            true
        }
    }

    fun addLayer(source: FileSoundSource) {
        if (layers.containsKey(source.id)) return

        // First active layer turns playback intent on (tap-to-play UX).
        if (layers.isEmpty()) masterPlaying = true

        val layer = CrossfadeLayer(context, source, audioSessionId, eqEnabled)

        // Insert and acquire focus BEFORE starting: isPlaying depends on the
        // layer count and focus, so both must be settled before the layer fades in.
        layers[source.id] = layer
        ensureFocus()
        layer.start(play = isPlaying, effectiveVolume = effectiveVolume(source.volume))
        onStateChanged?.invoke()
    }

    fun removeLayer(id: String) {
        val layer = layers.remove(id) ?: return
        layer.releaseWithFadeOut() // ease out rather than hard-cutting a loud moment
        if (layers.isEmpty()) {
            masterPlaying = false
            muted = false // an emptied mix starts fresh — never silently muted
            abandonFocus()
        }
        onStateChanged?.invoke()
    }

    fun setVolume(id: String, volume: Float) {
        val layer = layers[id] ?: return
        layer.source.volume = volume
        layer.setEffectiveVolume(effectiveVolume(volume))
        onStateChanged?.invoke()
    }

    /** Change the active layer's selected variant (e.g. a FREQUENCY variant's
     *  gap timing). Takes effect at the next segment boundary — never interrupts
     *  a running crossfade or gap. */
    fun setVariant(id: String, variantId: String?) {
        val layer = layers[id] ?: return
        layer.source.variantId = variantId
        onStateChanged?.invoke()
    }

    /** Silence (true) or restore (false) the whole mix without pausing it. Every
     *  layer keeps running, so [isPlaying] and the cards/notification stay live. */
    fun setMuted(value: Boolean) {
        if (value == muted) return
        muted = value
        applyVolume()
        onStateChanged?.invoke()
    }

    /** Global listening-context voicing; applied to every active layer. */
    fun setOutputMode(mode: OutputMode) {
        outputMode = mode
        layers.values.forEach { it.setEqEnabled(eqEnabled) }
    }

    // ── Global transport (drives the notification play/pause) ────────────────

    fun setMasterPlaying(playing: Boolean) {
        if (playing == masterPlaying) return
        masterPlaying = playing
        if (playing) {
            hasFocus = if (layers.isNotEmpty()) requestFocus() else false
        } else {
            abandonFocus()
        }
        applyPlayWhenReady()
        onStateChanged?.invoke()
    }

    /** Stop everything and release. Tears the mix down to empty. */
    fun stopAll() {
        masterPlaying = false
        muted = false
        abandonFocus()
        layers.values.forEach { it.release() }
        layers.clear()
        onStateChanged?.invoke()
    }

    /**
     * Gently fade the whole mix to silence over [durationMs], then stop — used
     * when the sleep timer expires so playback doesn't end with a hard cut.
     * Each layer fades and releases itself; the engine empties immediately so
     * state (notification, isPlaying) reflects "stopped" right away.
     */
    fun fadeOutAndStop(durationMs: Long) {
        if (layers.isEmpty()) return
        masterPlaying = false
        muted = false
        abandonFocus()
        layers.values.forEach { it.releaseWithFadeOut(durationMs) }
        layers.clear()
        onStateChanged?.invoke()
    }

    fun release() {
        layers.values.forEach { it.release() }
        layers.clear()
        abandonFocus()
        onStateChanged = null
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun applyPlayWhenReady() {
        val play = isPlaying
        layers.values.forEach { it.setPlaying(play) }
    }

    private fun applyVolume() {
        layers.values.forEach { it.setEffectiveVolume(effectiveVolume(it.source.volume)) }
    }

    /** A layer's source volume scaled by the transient duck and the mute toggle. */
    private fun effectiveVolume(vol: Float): Float =
        if (muted) 0f else vol * duckFactor

    /** Acquire focus if we want playback, have layers, and don't hold it yet. */
    private fun ensureFocus() {
        if (masterPlaying && layers.isNotEmpty() && !hasFocus) {
            hasFocus = requestFocus()
        }
    }

    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonFocus() {
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                duckFactor = 1f
                applyVolume()
                applyPlayWhenReady()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent: another app took over. Stay paused, drop intent.
                hasFocus = false
                masterPlaying = false
                applyPlayWhenReady()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary (e.g. a call): pause but keep intent to resume.
                hasFocus = false
                applyPlayWhenReady()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckFactor = 0.3f
                applyVolume()
            }
        }
        onStateChanged?.invoke()
    }
}
