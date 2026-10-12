package io.github.probably_oxy.drift.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Saved presets and layer memory must keep loading across app versions. */
class PresetJsonTest {

    private val json = PresetStore.DriftJson

    @Test
    fun roundTrip() {
        val presets = listOf(
            Preset("Mine", "blurb", listOf(PresetLayer("rain", "below", 0.5f), PresetLayer("user:abc", null, 1f))),
        )
        assertEquals(presets, json.decodeFromString<List<Preset>>(json.encodeToString(presets)))
    }

    @Test
    fun olderJson_withMissingOptionalFields_loads() {
        val old = """[{"name":"Old","layers":[{"soundId":"wind","volume":0.3}]}]"""
        val p = json.decodeFromString<List<Preset>>(old).single()
        assertEquals("", p.blurb)
        assertNull(p.layers.single().variantId)
    }

    @Test
    fun newerJson_withUnknownFields_stillLoads() {
        val newer = """[{"name":"New","future":1,"layers":[{"soundId":"wind","volume":0.3,"trimDb":2}]}]"""
        assertEquals("wind", json.decodeFromString<List<Preset>>(newer).single().layers.single().soundId)
    }

    @Test
    fun builtInTrims_roundTrip() {
        // SoundLibrary persists built-in trims as a plain id → dB map.
        val trims = mapOf("marswind" to 4.5f, "fire" to -3f)
        assertEquals(trims, json.decodeFromString<Map<String, Float>>(json.encodeToString(trims)))
    }
}
