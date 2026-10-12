package io.github.probably_oxy.drift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomSoundTest {

    // ── Trim ───────────────────────────────────────────────────────────────

    @Test
    fun dbToGain_knownValues() {
        assertEquals(1f, dbToGain(0f), 1e-6f)
        assertEquals(1.995f, dbToGain(6f), 1e-3f)
        assertEquals(0.501f, dbToGain(-6f), 1e-3f)
        assertEquals(1.413f, dbToGain(3f), 1e-3f) // Mars Wind's catalogue boost
    }

    @Test
    fun trimSteps_landExactlyOnBothLimits() {
        // The stepper relies on repeated ± steps hitting the limits exactly.
        val range = CustomSound.TRIM_MAX_DB - CustomSound.TRIM_MIN_DB
        assertEquals(0f, range % CustomSound.TRIM_STEP_DB, 0f)
        assertEquals(0f, CustomSound.TRIM_MAX_DB % CustomSound.TRIM_STEP_DB, 0f)
    }

    @Test
    fun toSound_carriesTrimAsGain() {
        val sound = custom(trimDb = -6f).toSound()
        assertEquals(dbToGain(-6f), sound.gainTrim, 1e-6f)
        assertEquals(License.USER, sound.license.license)
    }

    // ── Auto-split ─────────────────────────────────────────────────────────

    @Test
    fun split_shortOrUnknownOrDisabled_staysWhole() {
        val whole = listOf(CustomSegment("a.mp3"))
        assertEquals(whole, CustomSound.split("a.mp3", 89_999L, autoSplit = true))
        assertEquals(whole, CustomSound.split("a.mp3", null, autoSplit = true))
        assertEquals(whole, CustomSound.split("a.mp3", 600_000L, autoSplit = false))
    }

    @Test
    fun split_atThreshold_givesTwoHalves() {
        val segs = CustomSound.split("a.mp3", 90_000L, autoSplit = true)
        assertEquals(
            listOf(CustomSegment("a.mp3", 0L, 45_000L), CustomSegment("a.mp3", 45_000L, null)),
            segs,
        )
    }

    @Test
    fun split_longFile_windowsAreContiguousAndCoverTheFile() {
        val duration = 600_123L
        val segs = CustomSound.split("a.mp3", duration, autoSplit = true)
        assertEquals(13, segs.size) // 600s / 45s = 13 windows of ~46s
        assertEquals(0L, segs.first().startMs)
        assertNull(segs.last().endMs) // last plays to the real end of the file
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        segs.dropLast(1).forEach { s ->
            val len = s.endMs!! - s.startMs
            assert(len in 45_000L..60_000L) { "window $len ms out of range" }
        }
    }

    private fun custom(trimDb: Float) = CustomSound(
        id = CustomSound.ID_PREFIX + "x",
        name = "x",
        folder = "x",
        segments = listOf(CustomSegment("a.mp3")),
        trimDb = trimDb,
    )
}
