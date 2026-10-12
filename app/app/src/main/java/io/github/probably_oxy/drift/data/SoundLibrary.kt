package io.github.probably_oxy.drift.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

/**
 * Every sound the app can play: the built-in [Catalogue] plus the user's own
 * imports. Imports live in filesDir/custom/<folder>/ with their metadata in
 * custom_sounds.json. A process-wide singleton because the Activity and the
 * PlaybackService (same process) must see the same list — both call [init].
 *
 * All mutation happens on the main thread; file copying runs on [scope], which
 * outlives the Activity so a rotation mid-import can't leave a half-copied sound.
 */
object SoundLibrary {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _custom = MutableStateFlow<List<CustomSound>>(emptyList())

    /** The user's imports, in import order. */
    val custom: StateFlow<List<CustomSound>> = _custom.asStateFlow()

    private val _trims = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** The user's trim (dB) for built-in sounds, by id; absent = 0 dB. Imports
     *  keep theirs on [CustomSound.trimDb] instead. */
    val trims: StateFlow<Map<String, Float>> = _trims.asStateFlow()

    private val rootDir: File get() = File(appContext.filesDir, "custom")
    private val indexFile: File get() = File(appContext.filesDir, "custom_sounds.json")
    private val trimsFile: File get() = File(appContext.filesDir, "sound_trims.json")

    /** Idempotent; call from every entry point (Activity and service). */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _custom.value = load()
        _trims.value = loadTrims()
    }

    /** A built-in's [Sound.gainTrim] already includes the user's trim on top of
     *  the catalogue's own loudness correction, so playback needs no extra step. */
    fun byId(id: String): Sound? =
        Catalogue.byId(id)?.let { it.copy(gainTrim = it.gainTrim * dbToGain(trimDbOf(id))) }
            ?: customById(id)?.toSound()

    /** The user's trim for any sound, built-in or imported; 0 = as shipped. */
    fun trimDbOf(id: String): Float = customById(id)?.trimDb ?: _trims.value[id] ?: 0f

    fun customById(id: String): CustomSound? = _custom.value.find { it.id == id }

    fun fileFor(sound: CustomSound, fileName: String): File = File(File(rootDir, sound.folder), fileName)

    // ── Editing ─────────────────────────────────────────────────────────────

    fun rename(id: String, name: String) =
        update(id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun setTrim(id: String, trimDb: Float) {
        val db = trimDb.coerceIn(CustomSound.TRIM_MIN_DB, CustomSound.TRIM_MAX_DB)
        when {
            customById(id) != null -> update(id) { it.copy(trimDb = db) }
            Catalogue.byId(id) != null -> {
                _trims.value = if (db == 0f) _trims.value - id else _trims.value + (id to db)
                runCatching { trimsFile.writeText(PresetStore.DriftJson.encodeToString(_trims.value)) }
            }
        }
    }

    fun delete(id: String) {
        val sound = customById(id) ?: return
        _custom.value = _custom.value - sound
        save()
        // A playing layer may still be reading the file; on Linux an unlinked
        // file stays readable until closed, so deleting under it is safe.
        scope.launch(Dispatchers.IO) { File(rootDir, sound.folder).deleteRecursively() }
    }

    private fun update(id: String, change: (CustomSound) -> CustomSound) {
        _custom.value = _custom.value.map { if (it.id == id) change(it) else it }
        save()
    }

    // ── Import ──────────────────────────────────────────────────────────────

    /** Outcome reported back to the UI once an import finishes. */
    sealed interface ImportResult {
        data class Success(val sound: CustomSound) : ImportResult
        data class Failure(val message: String) : ImportResult
    }

    /** Best-effort file name for a picked document, e.g. "rain.ogg". */
    fun displayNameOf(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

    /**
     * Copy [uris] into private storage as one new sound named [name]. Each file
     * is one segment, or — when [autoSplit] is on and the file is long enough —
     * several windows of it, so the layer crossfades between parts of a long
     * recording instead of looping the whole thing with an audible seam.
     */
    fun import(uris: List<Uri>, name: String, autoSplit: Boolean, onDone: (ImportResult) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { copyAndAnalyse(uris, name, autoSplit) }
            if (result is ImportResult.Success) {
                _custom.value = _custom.value + result.sound
                save()
            }
            onDone(result)
        }
    }

    private fun copyAndAnalyse(uris: List<Uri>, name: String, autoSplit: Boolean): ImportResult {
        val folder = UUID.randomUUID().toString()
        val dir = File(rootDir, folder)
        try {
            dir.mkdirs()
            val segments = mutableListOf<CustomSegment>()
            uris.forEachIndexed { i, uri ->
                val fileName = "src_$i${extensionOf(uri)}"
                val dest = File(dir, fileName)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: return fail(dir, "Couldn't open file ${i + 1}")
                val bytes = input.use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
                if (bytes > MAX_FILE_BYTES) return fail(dir, "File ${i + 1} is over 200 MB")
                val probe = probe(dest) ?: return fail(dir, "File ${i + 1} isn't a playable audio file")
                segments += split(fileName, probe.durationMs, autoSplit)
            }
            if (segments.isEmpty()) return fail(dir, "No files selected")
            val sound = CustomSound(
                id = CustomSound.ID_PREFIX + folder,
                name = name.trim().ifBlank { "My sound" },
                folder = folder,
                segments = segments,
            )
            return ImportResult.Success(sound)
        } catch (e: Exception) {
            Log.e("Drift", "Import failed", e)
            return fail(dir, "Import failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(dir: File, message: String): ImportResult {
        dir.deleteRecursively()
        return ImportResult.Failure(message)
    }

    private class Probe(val durationMs: Long?)

    /**
     * Null = not audio. Lenient on purpose: the platform retriever knows fewer
     * formats than ExoPlayer, so a file is only rejected when it reports neither
     * an audio track nor a duration. A null duration means "playable, don't split".
     */
    private fun probe(file: File): Probe? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (!hasAudio && duration == null) return null
            Probe(duration)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Cut a long file into equal windows of roughly [SPLIT_WINDOW_MS]. Files
     * shorter than [SPLIT_MIN_MS], or of unknown length, stay whole.
     */
    private fun split(fileName: String, durationMs: Long?, autoSplit: Boolean): List<CustomSegment> {
        if (!autoSplit || durationMs == null || durationMs < SPLIT_MIN_MS) return listOf(CustomSegment(fileName))
        val count = (durationMs / SPLIT_WINDOW_MS).toInt().coerceAtLeast(2)
        val window = durationMs / count
        return (0 until count).map { i ->
            CustomSegment(
                file = fileName,
                startMs = i * window,
                endMs = if (i == count - 1) null else (i + 1) * window,
            )
        }
    }

    /** ".ogg" etc. from the picked file's name (ExoPlayer sniffs content anyway). */
    private fun extensionOf(uri: Uri): String {
        val ext = displayNameOf(uri)?.substringAfterLast('.', "").orEmpty()
        return if (ext.isBlank() || ext.length > 5 || !ext.all(Char::isLetterOrDigit)) "" else ".${ext.lowercase()}"
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /** Drops entries whose files are gone (e.g. metadata restored from a backup
     *  that excluded the audio itself) rather than offering dead cards. */
    private fun load(): List<CustomSound> {
        if (!indexFile.exists()) return emptyList()
        val all = runCatching {
            PresetStore.DriftJson.decodeFromString<List<CustomSound>>(indexFile.readText())
        }.getOrDefault(emptyList())
        val alive = all.filter { s -> s.segments.isNotEmpty() && s.segments.all { fileFor(s, it.file).exists() } }
        if (alive.size != all.size) {
            runCatching { indexFile.writeText(PresetStore.DriftJson.encodeToString(alive)) }
        }
        return alive
    }

    private fun loadTrims(): Map<String, Float> =
        if (trimsFile.exists()) {
            runCatching { PresetStore.DriftJson.decodeFromString<Map<String, Float>>(trimsFile.readText()) }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

    private fun save() {
        val snapshot = _custom.value
        runCatching { indexFile.writeText(PresetStore.DriftJson.encodeToString(snapshot)) }
    }

    private const val MAX_FILE_BYTES = 200L * 1024 * 1024
    private const val SPLIT_MIN_MS = 90_000L
    private const val SPLIT_WINDOW_MS = 45_000L
}
