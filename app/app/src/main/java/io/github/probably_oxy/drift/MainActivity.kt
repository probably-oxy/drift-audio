package io.github.probably_oxy.drift

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.probably_oxy.drift.data.ThemeAuto
import io.github.probably_oxy.drift.ui.theme.isDaytime
import io.github.probably_oxy.drift.ui.theme.minutesUntilNextSwitch
import io.github.probably_oxy.drift.ui.theme.resolveTheme
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.runtime.snapshotFlow
import io.github.probably_oxy.drift.data.LayerMemoryStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.probably_oxy.drift.audio.AudioFiles
import io.github.probably_oxy.drift.audio.PlaybackService
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalUriHandler
import io.github.probably_oxy.drift.data.Catalogue
import io.github.probably_oxy.drift.data.CustomSound
import io.github.probably_oxy.drift.data.OutputMode
import io.github.probably_oxy.drift.data.SoundLibrary
import io.github.probably_oxy.drift.ui.SuggestSound
import io.github.probably_oxy.drift.data.Preset
import io.github.probably_oxy.drift.data.PresetLayer
import io.github.probably_oxy.drift.data.PresetLibrary
import io.github.probably_oxy.drift.data.PresetStore
import io.github.probably_oxy.drift.data.SettingsStore
import io.github.probably_oxy.drift.data.Sound
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import io.github.probably_oxy.drift.data.LocalDriftAnim
import io.github.probably_oxy.drift.ui.cockpit.LayerCard
import io.github.probably_oxy.drift.ui.cockpit.MenuDrawer
import io.github.probably_oxy.drift.ui.cockpit.ScanlineOverlay
import io.github.probably_oxy.drift.ui.cockpit.entrance
import io.github.probably_oxy.drift.ui.theme.DriftTheme
import io.github.probably_oxy.drift.ui.theme.LocalDriftColors
import io.github.probably_oxy.drift.ui.theme.ThemePreset
import io.github.probably_oxy.drift.ui.mixer.DeleteSoundDialog
import io.github.probably_oxy.drift.ui.mixer.Header
import io.github.probably_oxy.drift.ui.mixer.ImportDialog
import io.github.probably_oxy.drift.ui.mixer.NameDialog
import io.github.probably_oxy.drift.ui.mixer.OutputPanel
import io.github.probably_oxy.drift.ui.mixer.PresetChip
import io.github.probably_oxy.drift.ui.mixer.PresetsSection
import io.github.probably_oxy.drift.ui.mixer.SectionLabel
import io.github.probably_oxy.drift.ui.mixer.SleepPanel
import io.github.probably_oxy.drift.ui.mixer.StatusPanel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent bars with light icons to start with — enableEdgeToEdge()'s
        // bare/"auto" form picks scrim darkness off the system theme, which was
        // rendering an opaque-looking bar over the app's black background. The
        // icon colour then follows the active palette (see the SideEffect below),
        // since the light DAYLIGHT theme needs dark icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        hideStatusBar()
        SoundLibrary.init(this)
        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsStore(context) }
            // The picked theme, and the one actually shown (they differ while
            // automatic light/dark switching is on — see resolveTheme).
            val chosen = ThemePreset.entries.firstOrNull { it.name == settings.themeName }
                ?: ThemePreset.PHOSPHOR
            val nowMin by rememberMinuteOfDay(
                enabled = settings.themeAuto == ThemeAuto.SCHEDULE,
                lifecycle = lifecycle,
            )
            val systemDark = isSystemInDarkTheme()
            val isDay = isDaytime(
                auto = settings.themeAuto,
                systemDark = systemDark,
                nowMin = nowMin,
                dayStartMin = settings.dayStartMin,
                nightStartMin = settings.nightStartMin,
            )
            // A tapped theme overrides AUTO until the next switch. Drop the
            // override once the phase it was set in is over, so it can't revive.
            LaunchedEffect(isDay) {
                if (settings.themeFlipIsDay != null && settings.themeFlipIsDay != isDay) settings.clearThemeFlip()
            }
            val flipped = isDay != null && settings.themeFlipIsDay == isDay &&
                System.currentTimeMillis() < settings.themeFlipUntil
            val preset = resolveTheme(
                chosen = chosen,
                auto = settings.themeAuto,
                systemDark = systemDark,
                nowMin = nowMin,
                dayStartMin = settings.dayStartMin,
                nightStartMin = settings.nightStartMin,
                flipped = flipped,
            )
            val pickTheme: (ThemePreset) -> Unit = { picked ->
                // Under AUTO a dark pick is also the night theme; DAYLIGHT never is.
                if (isDay == null || !picked.colors.isLight) settings.updateTheme(picked.name)
                if (isDay == null || picked.colors.isLight == isDay) settings.clearThemeFlip()
                else settings.setThemeFlip(isDay, flipUntil(settings))
            }
            // Bar icons follow the palette: enableEdgeToEdge above fixes them light
            // (for the dark themes), which would vanish against DAYLIGHT's paper bg.
            val isLight = preset.colors.isLight
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightNavigationBars = isLight
                    isAppearanceLightStatusBars = isLight
                }
            }
            DriftTheme(colors = preset.colors) {
                CompositionLocalProvider(LocalDriftAnim provides settings.anim) {
                    RequestNotificationPermission()
                    Scaffold(
                        containerColor = LocalDriftColors.current.bg,
                        // The status bar is hidden (immersive) and the nav bar is
                        // transparent — reserving safeDrawing's default padding here
                        // left a large dead gap up top. Insets are instead applied
                        // only where content actually needs clearance (nav-bar-safe
                        // bottom padding on the scrolling list, see MixerScreen).
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxSize(),
                    ) { innerPadding ->
                        MixerScreen(
                            modifier = Modifier.padding(innerPadding),
                            settings = settings,
                            currentPreset = chosen,
                            shownPreset = preset,
                            onPickTheme = pickTheme,
                        )
                    }
                }
            }
        }
    }

    // Immersive cockpit: no status bar. Swiping from the top edge reveals it
    // transiently (for the clock/battery/notifications) then it re-hides —
    // re-applied on focus regain since transient reveals and system dialogs
    // can otherwise leave it stuck visible.
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }
}

/** When a theme override expires: the next scheduled switch, or for SYSTEM
 *  (whose switch can't be predicted) a day at most — it's cleared sooner, as
 *  soon as the system mode changes while the app is open. */
private fun flipUntil(settings: SettingsStore): Long {
    val now = System.currentTimeMillis()
    if (settings.themeAuto != ThemeAuto.SCHEDULE) return now + 24 * 60 * 60_000L
    val minutes = minutesUntilNextSwitch(minuteOfDayNow(), settings.dayStartMin, settings.nightStartMin)
    return now - now % 60_000L + minutes * 60_000L
}

/**
 * The current minute of the day (0–1439), refreshed every 30s while [enabled] —
 * and immediately each time the app returns to the foreground, since a delay
 * that started before the phone slept can fire hours late.
 */
@Composable
private fun rememberMinuteOfDay(enabled: Boolean, lifecycle: Lifecycle): State<Int> =
    produceState(initialValue = minuteOfDayNow(), enabled, lifecycle) {
        if (!enabled) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = minuteOfDayNow()
                delay(30_000)
            }
        }
    }

private fun minuteOfDayNow(): Int =
    Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/**
 * On Android 13+ the media notification only shows if the user grants
 * POST_NOTIFICATIONS. Playback works regardless; without it you just lose
 * the lock-screen controls. We ask once on first composition.
 */
@Composable
fun RequestNotificationPermission() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored: the notification simply won't appear if denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * The cockpit mixer screen. Each catalogue sound is a Telemetry layer card; tapping a
 * playable card toggles it into the mix via a custom session command. Active state is
 * signalled on multiple channels at once (border, amber title, VU, spinner) so it
 * never depends on colour alone.
 */
@OptIn(FlowPreview::class) // Flow.debounce, for the layer-memory save
@Composable
fun MixerScreen(
    modifier: Modifier = Modifier,
    settings: SettingsStore,
    currentPreset: ThemePreset,
    shownPreset: ThemePreset,
    onPickTheme: (ThemePreset) -> Unit,
) {
    val context = LocalContext.current
    val sounds = remember { Catalogue.sounds }
    val presetStore = remember { PresetStore(context) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var timerRemainingMs by remember { mutableStateOf(PlaybackService.TIMER_INACTIVE) }
    var outputMode by remember { mutableStateOf(OutputMode.SPEAKER) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Which layer card (if any) is showing its terminal readout — at most one.
    var flippedLayerId by remember { mutableStateOf<String?>(null) }
    val activeIds = remember { mutableStateListOf<String>() }
    // Sounds switched on by dragging their meter, awaiting the service's echo —
    // further drag events must adjust volume, not send a second toggle (which
    // would switch the sound straight back off).
    val pendingOn = remember { mutableStateListOf<String>() }
    // Per-sound volume/variant memory: covers every sound the user has touched,
    // not just the active ones, and is saved to disk (layerMemory) so a sound
    // comes back the way it was left — after a toggle, Stop All or a restart.
    val layerMemory = remember { LayerMemoryStore(context) }
    val volumes = remember { mutableStateMapOf<String, Float>() }
    val variantIds = remember { mutableStateMapOf<String, String?>() }
    val userPresets = remember { mutableStateListOf<Preset>() }

    // User imports (MY SOUNDS). Picked files wait in pendingImport while the
    // name/auto-split dialog is open; the copy itself runs in SoundLibrary.
    val customSounds by SoundLibrary.custom.collectAsState()
    val builtInTrims by SoundLibrary.trims.collectAsState()
    val uriHandler = LocalUriHandler.current
    var pendingImport by remember { mutableStateOf<List<Uri>?>(null) }
    var importing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CustomSound?>(null) }
    var deleting by remember { mutableStateOf<CustomSound?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) pendingImport = uris }

    LaunchedEffect(Unit) { userPresets.addAll(presetStore.load()) }

    // Load the memory once, then save it whenever it settles (debounced, so a
    // slider drag writes once at the end rather than on every frame). Live-mix
    // values from the service (applyActiveLayers) win over the loaded ones.
    LaunchedEffect(Unit) {
        layerMemory.load().forEach { layer ->
            if (layer.soundId !in volumes) volumes[layer.soundId] = layer.volume
            if (layer.soundId !in variantIds) variantIds[layer.soundId] = layer.variantId
        }
        snapshotFlow { volumes.toMap() to variantIds.toMap() }
            .debounce(500)
            .collect { (vols, variants) ->
                layerMemory.save(vols.map { (id, v) -> PresetLayer(id, variants[id], v) })
            }
    }

    // Re-derive activeIds (and the active layers' volume/variant) from the
    // service's own record of the live mix rather than trusting local Compose
    // state to have survived — it doesn't survive an Activity recreate (e.g.
    // rotation), while the mix itself keeps playing in the always-alive
    // PlaybackService. Called on every extras publish (every layer change, and
    // every second while the sleep timer runs), so local optimistic mutation in
    // toggle()/setVolume()/setVariant() below is immediately confirmed rather
    // than fought. Inactive sounds' remembered values are deliberately left
    // alone — clearing them here is what used to reset volumes to 100%.
    fun applyActiveLayers(extras: Bundle) {
        val json = extras.getString(PlaybackService.EXTRA_ACTIVE_LAYERS) ?: return
        val layers = runCatching {
            PresetStore.DriftJson.decodeFromString<List<PresetLayer>>(json)
        }.getOrNull() ?: return
        activeIds.clear()
        layers.forEach { layer ->
            pendingOn.remove(layer.soundId)
            activeIds.add(layer.soundId)
            volumes[layer.soundId] = layer.volume
            variantIds[layer.soundId] = layer.variantId
        }
    }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            // The service broadcasts the sleep-timer countdown via session extras.
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    timerRemainingMs = extras.getLong(
                        PlaybackService.EXTRA_TIMER_REMAINING_MS,
                        PlaybackService.TIMER_INACTIVE,
                    )
                    outputMode = OutputMode.fromName(extras.getString(PlaybackService.EXTRA_OUTPUT_MODE))
                    muted = extras.getBoolean(PlaybackService.EXTRA_MUTED, false)
                    applyActiveLayers(extras)
                }
            })
            .buildAsync()
        var listener: Player.Listener? = null
        future.addListener(
            {
                val c =
                    try {
                        future.get()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e("Drift", "MediaController connection interrupted", e)
                        return@addListener
                    } catch (e: java.util.concurrent.ExecutionException) {
                        Log.e("Drift", "MediaController connection failed", e.cause ?: e)
                        return@addListener
                    }
                controller = c
                isPlaying = c.isPlaying
                timerRemainingMs = c.sessionExtras.getLong(
                    PlaybackService.EXTRA_TIMER_REMAINING_MS,
                    PlaybackService.TIMER_INACTIVE,
                )
                outputMode = OutputMode.fromName(
                    c.sessionExtras.getString(PlaybackService.EXTRA_OUTPUT_MODE),
                )
                muted = c.sessionExtras.getBoolean(PlaybackService.EXTRA_MUTED, false)
                applyActiveLayers(c.sessionExtras)
                listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                }.also(c::addListener)
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            listener?.let { controller?.removeListener(it) }
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    fun toggle(sound: Sound) {
        val c = controller ?: return
        // Reuse the last volume/variant the user set for this sound, defaulting
        // only the first time it's ever activated.
        val volume = volumes.getOrPut(sound.id) { LayerMemoryStore.DEFAULT_VOLUME }
        val variantId = if (sound.id in variantIds) variantIds[sound.id] else sound.defaultVariantId
        c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_TOGGLE_LAYER, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.KEY_SOUND_ID, sound.id)
                putFloat(PlaybackService.KEY_VOLUME, volume)
                putString(PlaybackService.KEY_VARIANT_ID, variantId)
            },
        )
        // activeIds is no longer mutated here: the session's extras echo
        // (applyActiveLayers, above) is the single writer for on/off state now.
        // sendCustomCommand can dispatch onCustomCommand/publishExtras/
        // onExtrasChanged synchronously (same-process session) before this call
        // returns, so a manual toggle here could read already-updated state and
        // flip it right back — exactly the bug this replaces.
    }

    fun setVolume(sound: Sound, volume: Float) {
        volumes[sound.id] = volume
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_VOLUME, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.KEY_SOUND_ID, sound.id)
                putFloat(PlaybackService.KEY_VOLUME, volume)
            },
        )
    }

    /** Meter drag/tap. On an inactive card it also switches the sound on at
     *  the dragged volume, so the slider alone is enough to start a sound. */
    fun adjustVolume(sound: Sound, volume: Float) {
        if (sound.id in activeIds || sound.id in pendingOn) {
            setVolume(sound, volume)
        } else {
            volumes[sound.id] = volume
            pendingOn.add(sound.id)
            toggle(sound)
        }
    }

    fun setVariant(sound: Sound, variantId: String?) {
        variantIds[sound.id] = variantId
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_VARIANT, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.KEY_SOUND_ID, sound.id)
                putString(PlaybackService.KEY_VARIANT_ID, variantId)
            },
        )
    }

    fun setTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_TIMER, Bundle.EMPTY),
            Bundle().apply { putLong(PlaybackService.KEY_TIMER_MS, minutes * 60_000L) },
        )
    }

    fun cancelTimer() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_CANCEL_TIMER, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun setOutput(mode: OutputMode) {
        outputMode = mode // optimistic; the service echoes it back via extras
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_OUTPUT_MODE, Bundle.EMPTY),
            Bundle().apply { putString(PlaybackService.KEY_OUTPUT_MODE, mode.name) },
        )
    }

    // Mute: silence the mix (effective volume 0) while every layer keeps running
    // and stays highlighted — a volume mute, not a pause. Stop All: tear the whole
    // mix down to nothing.
    fun toggleMute() {
        val c = controller ?: return
        val next = !muted
        muted = next // optimistic; the service echoes it back via extras
        c.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_MUTE, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.KEY_MUTED, next) },
        )
    }

    fun stopAll() {
        controller?.stop()
        muted = false
        activeIds.clear() // volumes/variants stay remembered
        pendingOn.clear()
    }

    fun applyPreset(preset: Preset) {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_APPLY_PRESET, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.KEY_PRESET_JSON, PresetStore.DriftJson.encodeToString(preset))
            },
        )
        // Mirror the new mix locally; only playable layers actually sound. The
        // preset's values also become those sounds' remembered values.
        activeIds.clear()
        preset.layers.forEach { layer ->
            val sound = SoundLibrary.byId(layer.soundId) ?: return@forEach
            if (AudioFiles.isPlayable(sound)) {
                activeIds.add(layer.soundId)
                volumes[layer.soundId] = layer.volume
                variantIds[layer.soundId] = layer.variantId
            }
        }
    }

    fun saveCurrentMix(name: String) {
        val layers = activeIds.map {
            PresetLayer(it, variantId = variantIds[it], volume = volumes[it] ?: 1f)
        }
        if (layers.isEmpty()) return
        val label = name.trim().ifBlank { "Mix ${userPresets.size + 1}" }
        userPresets.add(Preset(name = label, layers = layers))
        presetStore.save(userPresets.toList())
    }

    fun deletePreset(preset: Preset) {
        userPresets.remove(preset)
        presetStore.save(userPresets.toList())
    }

    // ── MY SOUNDS ────────────────────────────────────────────────────────────

    fun startImport() {
        menuOpen = false
        // application/ogg too: some pickers don't class .ogg as audio/*.
        runCatching { importLauncher.launch(arrayOf("audio/*", "application/ogg")) }
    }

    fun runImport(uris: List<Uri>, name: String, autoSplit: Boolean) {
        importing = true
        SoundLibrary.import(uris, name, autoSplit) { result ->
            importing = false
            val message = when (result) {
                is SoundLibrary.ImportResult.Success -> "Imported \"${result.sound.name}\""
                is SoundLibrary.ImportResult.Failure -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /** Persist the new trim, then push it to the live layer (if playing). */
    fun setTrim(id: String, trimDb: Float) {
        SoundLibrary.setTrim(id, trimDb)
        // Full gain = catalogue correction × user trim (see SoundLibrary.byId).
        val gain = SoundLibrary.byId(id)?.gainTrim ?: return
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_SET_TRIM, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackService.KEY_SOUND_ID, id)
                putFloat(PlaybackService.KEY_GAIN, gain)
            },
        )
    }

    fun deleteCustom(sound: CustomSound) {
        if (sound.id in activeIds) toggle(sound.toSound()) // fades it out of the mix
        SoundLibrary.delete(sound.id)
        volumes.remove(sound.id)
        variantIds.remove(sound.id)
        if (flippedLayerId == sound.id) flippedLayerId = null
    }

    fun suggest(soundName: String? = null) {
        runCatching { uriHandler.openUri(SuggestSound.url(soundName)) }
            .onFailure { Toast.makeText(context, "No browser available to open GitHub", Toast.LENGTH_LONG).show() }
    }

    // active = layer on AND the transport is running. Mute no longer pauses, so
    // muted layers stay "playing" here (highlighted, spinner running) — just silent.
    val playingNow = isPlaying && activeIds.isNotEmpty()

    /** One mixer card. Every playable sound gets a trim row on its readout;
     *  [custom] non-null = a user import (also adds SUGGEST). */
    @Composable
    fun SoundCard(sound: Sound, channel: Int, entranceDelay: Int, custom: CustomSound? = null) {
        val playable = AudioFiles.isPlayable(sound)
        val on = sound.id in activeIds
        LayerCard(
            sound = sound,
            channel = channel,
            on = on,
            volume = volumes[sound.id] ?: LayerMemoryStore.DEFAULT_VOLUME,
            active = on && playingNow,
            showInfo = flippedLayerId == sound.id,
            variantId = if (sound.id in variantIds) variantIds[sound.id] else sound.defaultVariantId,
            onToggle = { if (playable) toggle(sound) },
            onVolume = { if (playable) adjustVolume(sound, it) },
            onVariant = { if (playable) setVariant(sound, it) },
            onInfo = { flippedLayerId = if (flippedLayerId == sound.id) null else sound.id },
            // Entrance stagger; SYN sounds (no audio yet) shown dimmed and inert.
            modifier = Modifier
                .entrance(entranceDelay)
                .alpha(if (playable) 1f else 0.5f),
            trimDb = if (playable) custom?.trimDb ?: builtInTrims[sound.id] ?: 0f else null,
            onTrim = { db -> setTrim(sound.id, db) },
            onSuggest = custom?.let { c -> { suggest(c.name) } },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Header(onMenu = { menuOpen = true })
        Spacer(Modifier.height(8.dp))
        StatusPanel(
            layerCount = activeIds.size,
            playing = playingNow,
            muted = muted,
            enabled = controller != null && activeIds.isNotEmpty(),
            onMute = { toggleMute() },
            onStop = { stopAll() },
            modifier = Modifier.entrance(0),
        )
        Spacer(Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            // The nav bar is transparent/overlaid, not reserved — give only the
            // scrolling content enough bottom clearance to not end up under it.
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            item {
                SleepPanel(
                    remainingMs = timerRemainingMs,
                    enabled = controller != null && (activeIds.isNotEmpty() || timerRemainingMs > 0),
                    onPick = { setTimer(it) },
                    onCancel = { cancelTimer() },
                    modifier = Modifier.entrance(45),
                )
            }
            item {
                OutputPanel(
                    selected = outputMode,
                    onSelect = { setOutput(it) },
                    modifier = Modifier.entrance(90),
                )
            }
            item { SectionLabel("SOUNDSCAPE", modifier = Modifier.entrance(120)) }
            itemsIndexed(sounds, key = { _, s -> s.id }) { index, sound ->
                SoundCard(sound = sound, channel = index + 1, entranceDelay = 150 + minOf(index, 8) * 35)
            }
            item { SectionLabel("MY SOUNDS", modifier = Modifier.entrance(155)) }
            itemsIndexed(customSounds, key = { _, s -> s.id }) { index, custom ->
                SoundCard(
                    sound = custom.toSound(),
                    channel = sounds.size + index + 1,
                    entranceDelay = 155,
                    custom = custom,
                )
            }
            item {
                PresetChip(
                    label = if (importing) "IMPORTING…" else "+ IMPORT YOUR OWN SOUND",
                    enabled = !importing,
                    onClick = { startImport() },
                    modifier = Modifier.fillMaxWidth().entrance(158),
                    accent = true,
                )
            }
            item { SectionLabel("PRESETS", modifier = Modifier.entrance(160)) }
            item {
                PresetsSection(
                    builtIn = PresetLibrary.builtIn,
                    userPresets = userPresets,
                    enabled = controller != null,
                    canSave = controller != null && activeIds.isNotEmpty(),
                    onApply = { applyPreset(it) },
                    onSaveClick = { showSaveDialog = true },
                    onDelete = { deletePreset(it) },
                    modifier = Modifier.entrance(185),
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
        }

        Spacer(Modifier.height(8.dp))
      }

      // Subtle CRT scanlines + sweep, above content but below the drawer.
      ScanlineOverlay()

      // Left slide-in menu drawer (theme / about / credits / settings).
      MenuDrawer(
          open = menuOpen,
          currentPreset = currentPreset,
          shownPreset = shownPreset,
          settings = settings,
          onSelectPreset = onPickTheme,
          onClose = { menuOpen = false },
          customSounds = customSounds,
          onImport = { startImport() },
          onTrim = { sound, db -> setTrim(sound.id, db) },
          onRename = { renaming = it },
          onDelete = { deleting = it },
          onSuggest = { suggest() },
      )
    }

    if (showSaveDialog) {
        NameDialog(
            title = "SAVE MIX",
            placeholder = "Preset name",
            confirmLabel = "SAVE",
            onConfirm = { name ->
                saveCurrentMix(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    pendingImport?.let { uris ->
        ImportDialog(
            defaultName = remember(uris) {
                SoundLibrary.displayNameOf(uris.first())?.substringBeforeLast('.').orEmpty()
            },
            fileCount = uris.size,
            onConfirm = { name, autoSplit ->
                pendingImport = null
                runImport(uris, name, autoSplit)
            },
            onDismiss = { pendingImport = null },
        )
    }

    renaming?.let { sound ->
        NameDialog(
            title = "RENAME SOUND",
            placeholder = "Sound name",
            confirmLabel = "RENAME",
            initial = sound.name,
            onConfirm = { name ->
                SoundLibrary.rename(sound.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { sound ->
        DeleteSoundDialog(
            name = sound.name,
            onConfirm = {
                deleteCustom(sound)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
