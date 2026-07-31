package io.github.probably_oxy.drift.audio

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.probably_oxy.drift.data.Catalogue
import io.github.probably_oxy.drift.data.OutputMode
import io.github.probably_oxy.drift.data.Preset
import io.github.probably_oxy.drift.data.PresetStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * The MediaSessionService hosts a [PlaybackEngine] (many layers) fronted by a
 * [MixerPlayer] (one transport for the system). Standard play/pause/stop flow
 * through the MixerPlayer; everything that isn't a standard Player operation —
 * toggling a layer, setting a layer's volume, the sleep timer — goes through
 * custom session commands.
 *
 * The sleep timer lives HERE, in the always-alive service, not in the Activity:
 * it must keep counting with the screen off and the app backgrounded (the whole
 * point of a sleep timer). Remaining time is broadcast to the UI via the
 * session extras; at zero the mix fades out gently rather than hard-stopping.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var engine: PlaybackEngine
    private var sleepTimer: CountDownTimer? = null

    private var timerRemainingMs = TIMER_INACTIVE
    private var outputMode = OutputMode.SPEAKER

    private val prefs by lazy { getSharedPreferences("drift_settings", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        engine = PlaybackEngine(this)
        val player = MixerPlayer(engine)
        // MixerPlayer's init wired engine.onStateChanged to invalidate its own
        // state. Chain a re-publish of the session extras so UI-facing state the
        // engine changes on its own (notably the mute flag auto-resetting when the
        // mix empties) reaches the Activity, not just notification/transport state.
        val notifyPlayer = engine.onStateChanged
        engine.onStateChanged = {
            notifyPlayer?.invoke()
            publishExtras()
        }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MixerCallback())
            .build()

        // Restore the saved output mode (global setting, not part of a preset).
        outputMode = OutputMode.fromName(prefs.getString(PREF_OUTPUT_MODE, null))
        engine.setOutputMode(outputMode)
        publishExtras()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        sleepTimer?.cancel()
        sleepTimer = null
        mediaSession?.run {
            player.release() // MixerPlayer.handleRelease() releases the engine
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // ── Sleep timer ──────────────────────────────────────────────────────────

    private fun startSleepTimer(durationMs: Long) {
        sleepTimer?.cancel()
        publishTimer(durationMs)
        sleepTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(msUntilFinished: Long) = publishTimer(msUntilFinished)
            override fun onFinish() {
                sleepTimer = null
                publishTimer(TIMER_INACTIVE)
                engine.fadeOutAndStop(TIMER_FADE_MS)
            }
        }.start()
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        publishTimer(TIMER_INACTIVE)
    }

    // ── Presets ────────────────────────────────────────────────────────────────

    /** Set the live mix to exactly [preset]'s layers (skipping any without audio). */
    private fun applyPreset(preset: Preset) {
        val wanted = preset.layers.associateBy { it.soundId }
        engine.activeLayerIds()
            .filter { it !in wanted }
            .forEach { engine.removeLayer(it) }
        preset.layers.forEach { layer ->
            val sound = Catalogue.byId(layer.soundId) ?: return@forEach
            val source = AudioFiles.sourceFor(sound) ?: return@forEach // SYN: no audio yet
            source.volume = layer.volume
            // A saved preset may predate a variant being renamed/removed; fall
            // back to the sound's default rather than throwing.
            if (layer.variantId == null || sound.variants.any { it.id == layer.variantId }) {
                source.variantId = layer.variantId
            }
            if (engine.isLayerActive(layer.soundId)) {
                engine.setVolume(layer.soundId, layer.volume)
                if (layer.variantId == null || sound.variants.any { it.id == layer.variantId }) {
                    engine.setVariant(layer.soundId, layer.variantId)
                }
            } else {
                engine.addLayer(source)
            }
        }
    }

    private fun publishTimer(remainingMs: Long) {
        timerRemainingMs = remainingMs
        publishExtras()
    }

    /** Broadcast current UI-facing state (timer + output mode + mute + active
     *  layers) to all controllers. */
    private fun publishExtras() {
        mediaSession?.sessionExtras = Bundle().apply {
            putLong(EXTRA_TIMER_REMAINING_MS, timerRemainingMs)
            putString(EXTRA_OUTPUT_MODE, outputMode.name)
            putBoolean(EXTRA_MUTED, engine.isMuted)
            putString(EXTRA_ACTIVE_LAYERS, PresetStore.DriftJson.encodeToString(engine.activeLayersSnapshot()))
        }
    }

    private fun setOutputMode(mode: OutputMode) {
        outputMode = mode
        prefs.edit().putString(PREF_OUTPUT_MODE, mode.name).apply()
        engine.setOutputMode(mode)
        publishExtras()
    }

    /** Grants and handles Drift's custom session commands. */
    private inner class MixerCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(ACTION_TOGGLE_LAYER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_VOLUME, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CANCEL_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_APPLY_PRESET, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_OUTPUT_MODE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_MUTE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_VARIANT, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LAYER -> {
                    val id = args.getString(KEY_SOUND_ID)
                    val source = id?.let { Catalogue.byId(it) }?.let { AudioFiles.sourceFor(it) }
                    if (source != null) {
                        if (args.containsKey(KEY_VOLUME)) {
                            source.volume = args.getFloat(KEY_VOLUME)
                        }
                        val variantId = args.getString(KEY_VARIANT_ID)
                        if (variantId == null || source.variants.any { it.id == variantId }) {
                            source.variantId = variantId
                        }
                        engine.toggleLayer(source)
                        return success()
                    }
                }
                ACTION_SET_VOLUME -> {
                    val id = args.getString(KEY_SOUND_ID)
                    if (id != null && args.containsKey(KEY_VOLUME)) {
                        engine.setVolume(id, args.getFloat(KEY_VOLUME))
                        return success()
                    }
                }
                ACTION_SET_VARIANT -> {
                    val id = args.getString(KEY_SOUND_ID)
                    if (id != null) {
                        engine.setVariant(id, args.getString(KEY_VARIANT_ID))
                        return success()
                    }
                }
                ACTION_SET_TIMER -> {
                    val ms = args.getLong(KEY_TIMER_MS, 0L)
                    if (ms > 0L) {
                        startSleepTimer(ms)
                        return success()
                    }
                }
                ACTION_CANCEL_TIMER -> {
                    cancelSleepTimer()
                    return success()
                }
                ACTION_APPLY_PRESET -> {
                    val json = args.getString(KEY_PRESET_JSON)
                    val preset = json?.let {
                        runCatching { PresetStore.DriftJson.decodeFromString<Preset>(it) }.getOrNull()
                    }
                    if (preset != null) {
                        applyPreset(preset)
                        return success()
                    }
                }
                ACTION_SET_OUTPUT_MODE -> {
                    setOutputMode(OutputMode.fromName(args.getString(KEY_OUTPUT_MODE)))
                    return success()
                }
                ACTION_SET_MUTE -> {
                    // engine.setMuted fires onStateChanged → publishExtras, so the
                    // new mute flag reaches the UI without an explicit publish here.
                    engine.setMuted(args.getBoolean(KEY_MUTED))
                    return success()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        private fun success() =
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    companion object {
        const val ACTION_TOGGLE_LAYER = "io.github.probably_oxy.drift.TOGGLE_LAYER"
        const val ACTION_SET_VOLUME = "io.github.probably_oxy.drift.SET_VOLUME"
        const val ACTION_SET_TIMER = "io.github.probably_oxy.drift.SET_TIMER"
        const val ACTION_CANCEL_TIMER = "io.github.probably_oxy.drift.CANCEL_TIMER"
        const val ACTION_APPLY_PRESET = "io.github.probably_oxy.drift.APPLY_PRESET"
        const val ACTION_SET_OUTPUT_MODE = "io.github.probably_oxy.drift.SET_OUTPUT_MODE"
        const val ACTION_SET_MUTE = "io.github.probably_oxy.drift.SET_MUTE"
        const val ACTION_SET_VARIANT = "io.github.probably_oxy.drift.SET_VARIANT"
        const val KEY_SOUND_ID = "soundId"
        const val KEY_VOLUME = "volume"
        const val KEY_VARIANT_ID = "variantId"
        const val KEY_TIMER_MS = "timerMs"
        const val KEY_PRESET_JSON = "presetJson"
        const val KEY_OUTPUT_MODE = "outputMode"
        const val KEY_MUTED = "muted"

        /** Session-extras keys the UI reads. [TIMER_INACTIVE] = timer off. */
        const val EXTRA_TIMER_REMAINING_MS = "timerRemainingMs"
        const val EXTRA_OUTPUT_MODE = "outputMode"
        const val EXTRA_MUTED = "muted"
        const val EXTRA_ACTIVE_LAYERS = "activeLayers"
        const val TIMER_INACTIVE = -1L

        private const val TIMER_FADE_MS = 4000L
        private const val PREF_OUTPUT_MODE = "outputMode"
    }
}
