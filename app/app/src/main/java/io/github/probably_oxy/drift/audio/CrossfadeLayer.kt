package io.github.probably_oxy.drift.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.probably_oxy.drift.data.VariantKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One sound's playback as a file-independent crossfade between its segments.
 *
 * Two ExoPlayers ("decks") alternate: one is audible, the other stands by. Every
 * segment file is an independent seamless loop, so a deck loops its segment with
 * REPEAT_MODE_ONE as a safety net. Before a segment would reach its loop seam, we
 * pick the next (random, non-repeating) segment, start it on the standby deck at
 * volume 0, and equal-power crossfade across [CROSSFADE_MS]. The seam is always
 * buried inside the overlap, so it is never heard — regardless of how cleanly the
 * files loop or join. New segments fade in (no pop on a loud start); removal fades
 * out (no hard cut on a loud moment).
 *
 * The crossfade is *triggered by real playback position* (an ExoPlayer
 * [androidx.media3.exoplayer.PlayerMessage] at duration − [CROSSFADE_MS]), not a
 * wall-clock timer, so the WebView crossfade-timer drift bug cannot recur. The
 * volume ramp itself is a short [Handler] loop, which is fine: it runs in a
 * foreground service holding a wake lock, and a late ramp tick is inaudible —
 * worst case a deck simply keeps looping seamlessly, never silence.
 */
class CrossfadeLayer(
    context: Context,
    val source: FileSoundSource,
    private val audioSessionId: Int,
    eqEnabled: Boolean,
) {

    // One processor per deck — each ExoPlayer has its own audio sink and thus
    // its own filter state (see VoicingAudioProcessor's per-channel Biquad state).
    private val voicingA = VoicingAudioProcessor(source.gainTrim).apply { this.eqEnabled = eqEnabled }
    private val voicingB = VoicingAudioProcessor(source.gainTrim).apply { this.eqEnabled = eqEnabled }

    private val items: List<MediaItem> = source.segmentResIds.map { resId ->
        MediaItem.fromUri(
            Uri.parse("android.resource://${context.packageName}/$resId"),
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Effective volume the audible deck ramps up to (already includes ducking). */
    private var targetVol: Float = 1f
    private var playing = false
    private var lastSeg = -1
    private var fading = false
    private var disposed = false

    // ── Sporadic-frequency gap state ──────────────────────────────────────────
    // When the active FREQUENCY variant has a non-zero gap range, a segment-end
    // doesn't crossfade straight into the next one: the active deck fades to
    // silence but keeps looping (so it stays truly "playing" — no wall-clock
    // timer substitutes for real playback position, which would drift or stall
    // under Doze). The wait is measured entirely in playback position: how many
    // more loop restarts (AUTO_TRANSITION) the silent deck needs plus a final
    // in-loop offset, both driven by the audio engine actually running.
    private var gapping = false
    private var gapDeck: Deck? = null
    private var gapLoopsRemaining = 0
    private var gapTargetPos = 0L

    private inner class Deck(val player: ExoPlayer) {
        var crossfadeScheduled = false
        var fade: Runnable? = null

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Once the audible deck is ready (duration known), arm its
                // position-accurate crossfade trigger.
                if (state == Player.STATE_READY &&
                    active === this@Deck &&
                    !crossfadeScheduled &&
                    !disposed &&
                    items.size > 1
                ) {
                    scheduleCrossfade(this@Deck)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (disposed || reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                if (gapDeck !== this@Deck) return
                gapLoopsRemaining--
                if (gapLoopsRemaining <= 0) {
                    val target = gapTargetPos
                    gapDeck = null
                    if (target <= 0L) endGap(this@Deck) else armGapEnd(this@Deck, target)
                }
            }
        }

        init {
            player.addListener(listener)
        }

        fun loadSegment(seg: Int, startVolume: Float) {
            crossfadeScheduled = false
            player.setMediaItem(items[seg])
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = startVolume
            player.playWhenReady = playing
            player.prepare()
        }

        fun cancelFade() {
            fade?.let(handler::removeCallbacks)
            fade = null
        }

        fun release() {
            cancelFade()
            player.removeListener(listener)
            player.release()
        }
    }

    private val deckA = Deck(buildPlayer(context, voicingA))
    private val deckB = Deck(buildPlayer(context, voicingB))
    private var active = deckA

    private fun buildPlayer(context: Context, voicing: VoicingAudioProcessor): ExoPlayer =
        ExoPlayer.Builder(context, VoicingRenderersFactory(context, voicing))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false, // focus is owned by the engine
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            // Shared session id (harmless; lets system-level tools that key off
            // it still see the combined mix). No longer load-bearing for our own
            // voicing, which now runs per-deck via VoicingAudioProcessor.
            .apply { audioSessionId = this@CrossfadeLayer.audioSessionId }

    /** Toggled by the engine when [io.github.probably_oxy.drift.data.OutputMode] changes. */
    fun setEqEnabled(enabled: Boolean) {
        voicingA.eqEnabled = enabled
        voicingB.eqEnabled = enabled
    }

    // ── Engine-facing API ────────────────────────────────────────────────────

    fun start(play: Boolean, effectiveVolume: Float) {
        playing = play
        targetVol = effectiveVolume
        val seg = nextSeg()
        active.loadSegment(seg, startVolume = 0f)
        if (play) fadeIn(active)
    }

    fun setPlaying(play: Boolean) {
        if (play == playing) return
        playing = play
        deckA.player.playWhenReady = play
        deckB.player.playWhenReady = play
        // Resuming from a cold/paused start: ease the audible deck back up.
        // (Not while gapping — that silence is intentional, mid-wait.)
        if (play && !fading && !gapping && active.player.volume <= 0.001f) fadeIn(active)
    }

    fun setEffectiveVolume(volume: Float) {
        targetVol = volume
        if (!fading && !gapping) active.player.volume = volume
    }

    /** Fade both decks out over [durationMs], then release. */
    fun releaseWithFadeOut(durationMs: Long = REMOVE_FADE_MS) {
        disposed = true
        deckA.cancelFade()
        deckB.cancelFade()
        val aVol = deckA.player.volume
        val bVol = deckB.player.volume
        ramp(durationMs) { p ->
            val gain = cos(p * HALF_PI)
            deckA.player.volume = aVol * gain
            deckB.player.volume = bVol * gain
            if (p >= 1f) release()
        }
    }

    fun release() {
        disposed = true
        handler.removeCallbacksAndMessages(null)
        deckA.release()
        deckB.release()
    }

    // ── Crossfade internals ───────────────────────────────────────────────────

    private fun scheduleCrossfade(deck: Deck) {
        if (disposed) return
        val duration = deck.player.duration
        if (duration == C.TIME_UNSET) return
        deck.crossfadeScheduled = true
        val triggerAt = (duration - CROSSFADE_MS).coerceAtLeast(duration / 2)
        deck.player.createMessage { _, _ -> if (active === deck) onSegmentNearEnd(deck) }
            .setLooper(handler.looper)
            .setPosition(triggerAt)
            .setDeleteAfterDelivery(true)
            .send()
    }

    /** Decide, per the sound's active FREQUENCY variant, whether to crossfade
     * straight into the next segment or fade to a silent gap first. */
    private fun onSegmentNearEnd(deck: Deck) {
        val gap = activeFrequencyGapMs()
        if (gap == null || gap.second <= 0L) {
            crossfadeToNext()
        } else {
            fadeToSilenceThenGap(deck, Random.nextLong(gap.first, gap.second + 1))
        }
    }

    private fun activeFrequencyGapMs(): Pair<Long, Long>? =
        source.variants
            .firstOrNull { it.id == source.variantId && it.kind == VariantKind.FREQUENCY }
            ?.let { it.minGapMs to it.maxGapMs }

    /** Fade the active deck to silence (kept looping — real playback, not paused)
     * then hold for [gapMs] before waking the next segment in on the other deck. */
    private fun fadeToSilenceThenGap(deck: Deck, gapMs: Long) {
        deck.cancelFade()
        fading = true
        val startVol = deck.player.volume
        ramp(durationMs = CROSSFADE_MS) { p ->
            deck.player.volume = startVol * cos(p * HALF_PI)
            if (p >= 1f) {
                deck.player.volume = 0f
                fading = false
                gapping = true
                beginGapWait(deck, gapMs)
            }
        }
    }

    /** Arm the position-based wait for [gapMs] measured from [deck]'s current
     * position, spanning as many loop restarts as needed. */
    private fun beginGapWait(deck: Deck, gapMs: Long) {
        if (disposed) return
        val duration = deck.player.duration
        if (duration == C.TIME_UNSET || duration <= 0) {
            endGap(deck)
            return
        }
        val absoluteTarget = deck.player.currentPosition + gapMs
        val boundaries = (absoluteTarget / duration).toInt()
        val targetPos = absoluteTarget % duration
        if (boundaries <= 0) {
            armGapEnd(deck, targetPos)
        } else {
            gapDeck = deck
            gapLoopsRemaining = boundaries
            gapTargetPos = targetPos
        }
    }

    private fun armGapEnd(deck: Deck, position: Long) {
        deck.player.createMessage { _, _ -> if (!disposed) endGap(deck) }
            .setLooper(handler.looper)
            .setPosition(position)
            .setDeleteAfterDelivery(true)
            .send()
    }

    /** Gap's over: bring in a fresh segment on the other deck and park [deck]. */
    private fun endGap(deck: Deck) {
        if (disposed) return
        gapping = false
        val incoming = if (deck === deckA) deckB else deckA
        incoming.loadSegment(nextSeg(), startVolume = 0f)
        active = incoming // its READY will arm the next segment-end trigger
        deck.cancelFade()
        incoming.cancelFade()
        fading = true
        ramp(durationMs = FADE_IN_MS) { p ->
            incoming.player.volume = targetVol * sin(p * HALF_PI)
            if (p >= 1f) {
                incoming.player.volume = targetVol
                deck.player.volume = 0f
                deck.player.playWhenReady = false // park the gap deck
                fading = false
            }
        }
    }

    private fun crossfadeToNext() {
        if (disposed || items.size <= 1) return
        val outgoing = active
        val incoming = if (active === deckA) deckB else deckA

        incoming.loadSegment(nextSeg(), startVolume = 0f)
        active = incoming // its READY will arm the next crossfade

        outgoing.cancelFade()
        incoming.cancelFade()
        fading = true
        val outStart = outgoing.player.volume
        // targetVol is read live each tick (not captured once) so a volume change
        // mid-crossfade takes effect immediately instead of being silently
        // overwritten when the ramp finishes.
        ramp(durationMs = CROSSFADE_MS) { p ->
            outgoing.player.volume = outStart * cos(p * HALF_PI) // equal-power out
            incoming.player.volume = targetVol * sin(p * HALF_PI)  // equal-power in
            if (p >= 1f) {
                outgoing.player.volume = 0f
                outgoing.player.playWhenReady = false // park the standby deck
                incoming.player.volume = targetVol
                fading = false
            }
        }
    }

    private fun fadeIn(deck: Deck) {
        deck.cancelFade()
        fading = true
        // targetVol read live each tick — see note in crossfadeToNext().
        ramp(durationMs = FADE_IN_MS) { p ->
            deck.player.volume = targetVol * sin(p * HALF_PI)
            if (p >= 1f) {
                deck.player.volume = targetVol
                fading = false
            }
        }
    }

    /** Steps [onProgress] from 0f to 1f over [durationMs] on the main thread. */
    private fun ramp(durationMs: Long, onProgress: (Float) -> Unit) {
        val start = SystemClock.uptimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val p = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                onProgress(p)
                if (p < 1f) handler.postDelayed(this, STEP_MS)
            }
        }
        handler.post(runnable)
    }

    /** A random segment, never the same as the previous one. */
    private fun nextSeg(): Int {
        if (items.size <= 1) return 0
        var seg = Random.nextInt(items.size)
        if (seg == lastSeg) seg = (seg + 1) % items.size
        lastSeg = seg
        return seg
    }

    private companion object {
        const val CROSSFADE_MS = 4500L
        const val FADE_IN_MS = 1500L
        const val REMOVE_FADE_MS = 800L
        const val STEP_MS = 40L
        const val HALF_PI = (PI / 2).toFloat()
    }
}
