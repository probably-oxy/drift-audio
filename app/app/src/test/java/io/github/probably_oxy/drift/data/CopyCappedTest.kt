package io.github.probably_oxy.drift.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CopyCappedTest {

    @Test
    fun `copies everything when under the limit`() {
        val data = ByteArray(20_000) { it.toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(20_000L, copyCapped(ByteArrayInputStream(data), out, limit = 50_000))
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun `exactly the limit is allowed`() {
        val out = ByteArrayOutputStream()
        assertEquals(10_000L, copyCapped(ByteArrayInputStream(ByteArray(10_000)), out, limit = 10_000))
    }

    @Test
    fun `stops early once past the limit`() {
        val endless = object : InputStream() {
            var served = 0L
            override fun read(): Int = 0.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { served += it }
        }
        val out = ByteArrayOutputStream()
        assertNull(copyCapped(endless, out, limit = 100_000))
        // Read only about one buffer past the limit, and never wrote past it.
        assertTrue(endless.served < 100_000 + 2 * DEFAULT_BUFFER_SIZE)
        assertTrue(out.size() <= 100_000)
    }

    @Test
    fun `empty input copies nothing`() {
        assertEquals(0L, copyCapped(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), limit = 10))
    }
}
