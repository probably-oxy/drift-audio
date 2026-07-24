package io.github.probably_oxy.drift.audio

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Installs a single [VoicingAudioProcessor] into an ExoPlayer's audio pipeline
 * so gain trim and SPEAKER voicing run on the decoded samples themselves,
 * rather than via platform [android.media.audiofx] effects on the shared
 * session (see [VoicingAudioProcessor] for why).
 */
class VoicingRenderersFactory(
    context: Context,
    private val voicingProcessor: VoicingAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(voicingProcessor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}
