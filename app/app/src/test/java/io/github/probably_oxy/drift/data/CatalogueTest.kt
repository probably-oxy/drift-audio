package io.github.probably_oxy.drift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against a sound rename or removal silently breaking the built-in data. */
class CatalogueTest {

    @Test
    fun soundIdsAreUnique() {
        val ids = Catalogue.sounds.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun noBuiltInUsesTheImportPrefix() {
        Catalogue.sounds.forEach { assertTrue(it.id, !it.id.startsWith(CustomSound.ID_PREFIX)) }
    }

    @Test
    fun recSoundsHaveAudioAndSaneGain() {
        Catalogue.sounds.filter { it.type == SoundType.REC }.forEach {
            assertTrue("${it.id} has no segments", it.segmentCount > 0)
            assertTrue("${it.id} gainTrim ${it.gainTrim}", it.gainTrim in 0.25f..2f)
        }
    }

    @Test
    fun defaultVariantsExist() {
        Catalogue.sounds.forEach { sound ->
            sound.defaultVariantId?.let { def ->
                assertTrue("${sound.id} default '$def'", sound.variants.any { it.id == def })
            }
        }
    }

    @Test
    fun builtInPresetsReferencePlayableSoundsAndRealVariants() {
        PresetLibrary.builtIn.forEach { preset ->
            assertTrue("${preset.name} is empty", preset.layers.isNotEmpty())
            preset.layers.forEach { layer ->
                val sound = Catalogue.byId(layer.soundId)
                assertNotNull("${preset.name}: unknown sound '${layer.soundId}'", sound)
                assertTrue("${preset.name}: ${layer.soundId} has no audio", sound!!.segmentCount > 0)
                layer.variantId?.let { v ->
                    assertTrue("${preset.name}: ${layer.soundId} variant '$v'", sound.variants.any { it.id == v })
                }
                assertTrue("${preset.name}: volume ${layer.volume}", layer.volume in 0f..1f)
            }
        }
    }
}
