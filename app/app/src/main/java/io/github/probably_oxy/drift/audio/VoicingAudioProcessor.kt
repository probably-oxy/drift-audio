package io.github.probably_oxy.drift.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Per-deck PCM processing: a constant gain trim (per-sound loudness alignment,
 * see [io.github.probably_oxy.drift.data.Sound.gainTrim]) plus an optional
 * SPEAKER-mode voicing (bass lift + treble tame).
 *
 * Replaces the previous [android.media.audiofx] BassBoost/Equalizer pair, which
 * attached to the shared audio session and ran on vendor DSP — unreliable on
 * some devices when stacked with a shared session across multiple AudioTracks,
 * causing dropouts and loudness pumping (GitHub issue #9). This runs entirely
 * in-process on the decoded samples, so behaviour is identical on every device.
 *
 * One instance per ExoPlayer (each [CrossfadeLayer] deck), since the shelving
 * filters carry per-channel state (see [Biquad]) that must not be shared
 * between independently-playing tracks.
 */
class VoicingAudioProcessor(private val gainLinear: Float) : BaseAudioProcessor() {

    /** Toggled live by [CrossfadeLayer.setEqEnabled] when the output mode changes. */
    @Volatile
    var eqEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            filtersNeedReset = true
        }

    // Written from the main thread (eqEnabled setter), read/written on the
    // audio thread (queueInput) — a stale read just delays a reset by one
    // buffer (inaudible), but make the write visible promptly regardless.
    @Volatile
    private var filtersNeedReset = true
    private var lowShelf = Biquad.IDENTITY
    private var highShelf = Biquad.IDENTITY
    private var channelCount = 0
    private var lowState: Array<Biquad.State> = emptyArray()
    private var highState: Array<Biquad.State> = emptyArray()

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        lowState = Array(channelCount) { Biquad.State() }
        highState = Array(channelCount) { Biquad.State() }
        lowShelf = Biquad.lowShelf(LOW_SHELF_HZ, LOW_SHELF_GAIN_DB, inputAudioFormat.sampleRate)
        highShelf = Biquad.highShelf(HIGH_SHELF_HZ, HIGH_SHELF_GAIN_DB, inputAudioFormat.sampleRate)
        filtersNeedReset = true
        return inputAudioFormat // filtering only — format is unchanged
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val eq = eqEnabled
        if (filtersNeedReset) {
            lowState.forEach(Biquad.State::reset)
            highState.forEach(Biquad.State::reset)
            filtersNeedReset = false
        }
        var channel = 0
        while (inputBuffer.hasRemaining()) {
            var sample = inputBuffer.short.toFloat() * gainLinear
            if (eq) {
                sample = lowShelf.process(sample, lowState[channel])
                sample = highShelf.process(sample, highState[channel])
            }
            outputBuffer.putShort(softLimit(sample).toInt().toShort())
            channel = (channel + 1) % channelCount
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        filtersNeedReset = true
    }

    override fun onReset() {
        filtersNeedReset = true
    }

    /**
     * Soft-knee saturation instead of a hard clamp. The bass shelf alone can
     * push a loud, bass-heavy source (e.g. Fireplace) a few dB over 0 dBFS —
     * measured via ffmpeg astats before shipping (fire seg 3 + gainTrim + this
     * shelf hit +3 dBFS). A hard clamp there would be an audible, harsh clip —
     * i.e. this processor recreating the exact glitch it exists to fix. tanh
     * saturation rolls occasional peaks off smoothly instead; it's memoryless
     * (no lookahead/attack-release), which is fine for ambient/nature material
     * with no sharp percussive transients to pump audibly.
     */
    private fun softLimit(x: Float): Float {
        val sign = if (x < 0f) -1f else 1f
        val magnitude = abs(x)
        if (magnitude <= LIMIT_THRESHOLD) return x
        val over = magnitude - LIMIT_THRESHOLD
        return sign * (LIMIT_THRESHOLD + LIMIT_KNEE * tanh(over / LIMIT_KNEE))
    }

    private companion object {
        const val LOW_SHELF_HZ = 150f
        const val LOW_SHELF_GAIN_DB = 4f
        const val HIGH_SHELF_HZ = 6000f
        const val HIGH_SHELF_GAIN_DB = -3f

        // Soft-knee limiter: starts rolling off at ~-1.7 dBFS, asymptotes to
        // ~-2.1 dBFS max (32000/32767) — always inside 16-bit range, so no
        // separate hard clamp is needed after this.
        const val LIMIT_THRESHOLD = 27000f
        const val LIMIT_KNEE = 5000f
    }
}

/**
 * A single biquad shelving filter (RBJ Audio EQ Cookbook formulas), Direct
 * Form I. Coefficients are fixed at construction; [State] carries the
 * per-channel history so one [Biquad] instance can filter multiple channels.
 */
private class Biquad(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    class State {
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    fun process(x: Float, s: State): Float {
        val y = b0 * x + b1 * s.x1 + b2 * s.x2 - a1 * s.y1 - a2 * s.y2
        s.x2 = s.x1
        s.x1 = x
        s.y2 = s.y1
        s.y1 = y
        return y
    }

    companion object {
        /** Pass-through — used before the filter has been configured. */
        val IDENTITY = Biquad(1f, 0f, 0f, 0f, 0f)

        fun lowShelf(f0Hz: Float, gainDb: Float, sampleRate: Int): Biquad =
            shelf(f0Hz, gainDb, sampleRate, isLow = true)

        fun highShelf(f0Hz: Float, gainDb: Float, sampleRate: Int): Biquad =
            shelf(f0Hz, gainDb, sampleRate, isLow = false)

        private fun shelf(f0Hz: Float, gainDb: Float, sampleRate: Int, isLow: Boolean): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * Math.PI * f0Hz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val shelfSlope = 1.0
            val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / shelfSlope - 1.0) + 2.0)
            val sqrtA = sqrt(a)

            val b0: Double
            val b1: Double
            val b2: Double
            val a0: Double
            val a1: Double
            val a2: Double
            if (isLow) {
                b0 = a * ((a + 1) - (a - 1) * cosW0 + 2 * sqrtA * alpha)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW0)
                b2 = a * ((a + 1) - (a - 1) * cosW0 - 2 * sqrtA * alpha)
                a0 = (a + 1) + (a - 1) * cosW0 + 2 * sqrtA * alpha
                a1 = -2 * ((a - 1) + (a + 1) * cosW0)
                a2 = (a + 1) + (a - 1) * cosW0 - 2 * sqrtA * alpha
            } else {
                b0 = a * ((a + 1) + (a - 1) * cosW0 + 2 * sqrtA * alpha)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW0)
                b2 = a * ((a + 1) + (a - 1) * cosW0 - 2 * sqrtA * alpha)
                a0 = (a + 1) - (a - 1) * cosW0 + 2 * sqrtA * alpha
                a1 = 2 * ((a - 1) - (a + 1) * cosW0)
                a2 = (a + 1) - (a - 1) * cosW0 - 2 * sqrtA * alpha
            }
            return Biquad(
                (b0 / a0).toFloat(),
                (b1 / a0).toFloat(),
                (b2 / a0).toFloat(),
                (a1 / a0).toFloat(),
                (a2 / a0).toFloat(),
            )
        }
    }
}
