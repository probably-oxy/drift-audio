package io.github.probably_oxy.drift.audio

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A minimal [Player] that represents the whole [PlaybackEngine] mix as a single
 * transport, so the MediaSession (and therefore the system notification and
 * lock-screen controls) has one play/pause/stop to talk to. It produces no
 * audio itself — the per-layer ExoPlayers inside the engine do that. This is
 * the standard way to drive a MediaSession from non-standard playback.
 *
 * [SimpleBasePlayer] does the Player heavy lifting: we only describe current
 * state in [getState] and react to the few commands we advertise. Whenever the
 * engine changes, [invalidateState] makes the base class re-read [getState].
 */
class MixerPlayer(
    private val engine: PlaybackEngine,
    /** PNG bytes for the notification / lock-screen artwork (the Drift logo). */
    private val artwork: ByteArray?,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val availableCommands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_PREPARE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE,
        )
        .build()

    /** Second line of the notification, e.g. "3 sounds · timer 42m". Only
     *  re-publishes on an actual change, so a per-second caller costs nothing. */
    var subtitle: String = "Ambient mix"
        set(value) {
            if (value == field) return
            field = value
            invalidateState()
        }

    // A single item so the notification has something to label. It's marked
    // live (dynamic, no duration), like a radio stream: the mix has no end, so
    // the system media controls should draw no progress bar for it.
    private fun mixItem() = MediaItemData.Builder(/* uid = */ "drift-mix")
        .setMediaItem(MediaItem.Builder().setMediaId("drift-mix").build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Drift")
                .setArtist(subtitle)
                .apply { if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                .build(),
        )
        .setIsDynamic(true)
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        .build()

    init {
        // Push engine state changes into the base player's state machine.
        engine.onStateChanged = { invalidateState() }
    }

    override fun getState(): State {
        val active = engine.hasLayers
        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(if (active) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(
                engine.isPlaying,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaylist(if (active) listOf(mixItem()) else emptyList())
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        engine.setMasterPlaying(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        engine.stopAll()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        engine.release()
        return Futures.immediateVoidFuture()
    }
}
