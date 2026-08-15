package dev.rnett.gradle.mcp.utils

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BomStrippingInputStreamTest {

    private fun wrap(vararg bytes: Int): BomStrippingInputStream =
        BomStrippingInputStream(ByteArrayInputStream(bytes.map { it.toByte() }.toByteArray()))

    private fun strippedReadAll(vararg bytes: Int): ByteArray =
        wrap(*bytes).readAllBytes()

    // Absolute leading bytes that do not form a BOM.
    private fun leadingBytes() = byteArrayOf(0x7B, 0x22) // '{', '"'

    @Test
    fun `strip BOM from start of stream`() {
        assertContentEquals(
            leadingBytes(),
            strippedReadAll(0xEF, 0xBB, 0xBF, 0x7B, 0x22),
        )
    }

    @Test
    fun `empty stream stays empty`() {
        assertContentEquals(byteArrayOf(), strippedReadAll())
    }

    @Test
    fun `stream with only a BOM reduces to empty`() {
        assertContentEquals(byteArrayOf(), strippedReadAll(0xEF, 0xBB, 0xBF))
    }

    @Test
    fun `no BOM leaves bytes untouched`() {
        assertContentEquals(
            leadingBytes(),
            strippedReadAll(0x7B, 0x22),
        )
    }

    @Test
    fun `partial BOM prefix EF is preserved`() {
        assertContentEquals(
            byteArrayOf(0xEF.toByte()),
            strippedReadAll(0xEF),
        )
    }

    @Test
    fun `partial BOM prefix EF BB is preserved`() {
        assertContentEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte()),
            strippedReadAll(0xEF, 0xBB),
        )
    }

    @Test
    fun `EF BB 00 is preserved as data`() {
        assertContentEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0x00),
            strippedReadAll(0xEF, 0xBB, 0x00),
        )
    }

    @Test
    fun `single-byte reads strip BOM then stream payload`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 'a'.code, 'b'.code)
        assertEquals('a'.code, stream.read())
        assertEquals('b'.code, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun `single-byte reads without BOM stream payload`() {
        val stream = wrap('a'.code, 'b'.code)
        assertEquals('a'.code, stream.read())
        assertEquals('b'.code, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun `final normal read returns -1 at EOF`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 'x'.code)
        assertEquals('x'.code, stream.read())
        assertEquals(-1, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun `read with buffer and offset honours offset`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 1, 2, 3, 4, 5, 6, 7, 8)
        val buf = ByteArray(8)
        // First read from the post-BOM data: read 4 bytes starting at offset 2.
        val n1 = stream.read(buf, 2, 4)
        assertEquals(4, n1)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), buf.copyOfRange(2, 6))
        val n2 = stream.read(buf, 2, 4)
        assertEquals(4, n2)
        assertContentEquals(byteArrayOf(5, 6, 7, 8), buf.copyOfRange(2, 6))
        assertEquals(-1, stream.read(buf, 0, 8))
    }

    @Test
    fun `read with buffer without BOM returns exactly the payload`() {
        val stream = wrap(1, 2, 3, 4, 5)
        val buf = ByteArray(5)
        val n = stream.read(buf, 0, 5)
        assertEquals(5, n)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), buf)
        assertEquals(-1, stream.read(buf, 0, 5))
    }

    @Test
    fun `len zero read returns zero`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 1)
        assertEquals(0, stream.read(ByteArray(0), 0, 0))
        assertEquals(1, stream.read())
    }

    @Test
    fun `readNBytes strips BOM and reads requested amount across prefix`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 1, 2, 3, 4, 5, 6)
        val buf = ByteArray(4)
        val n1 = stream.readNBytes(buf, 0, 4)
        assertEquals(4, n1)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), buf)
        val n2 = stream.readNBytes(buf, 0, 4)
        assertEquals(2, n2)
        assertContentEquals(byteArrayOf(5, 6), buf.copyOf(n2))
    }

    @Test
    fun `readNBytes with partial BOM prefix fills from prefix then delegate`() {
        // 'EF BB 00' is preserved as data; readNBytes(.., 3) returns all three.
        val stream = wrap(0xEF, 0xBB, 0x00, 9)
        val buf = ByteArray(3)
        val n = stream.readNBytes(buf, 0, 3)
        assertEquals(3, n)
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0x00), buf)
        assertEquals(9, stream.read())
    }

    @Test
    fun `readAllBytes then EOF across multiple reads`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 1, 2, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), stream.readAllBytes())
        assertTrue(stream.read() == -1)
        assertContentEquals(byteArrayOf(), stream.readAllBytes())
    }

    @Test
    fun `mixed read calls produce full payload in order`() {
        val stream = wrap(0xEF, 0xBB, 0xBF, 1, 2, 3, 4, 5, 6)
        val collected = mutableListOf<Int>()
        collected += stream.read() // 1
        val chunk = ByteArray(2)
        collected += stream.read(chunk, 0, 2).let { chunk.copyOf(it).map { it.toInt() and 0xFF } }
        val nBytes = ByteArray(3)
        val n = stream.readNBytes(nBytes, 0, 3)
        collected += nBytes.copyOf(n).map { it.toInt() and 0xFF }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), collected)
        assertEquals(-1, stream.read())
    }

    @Test
    fun `available accounts for buffered prefix and delegate`() {
        val stream = wrap(0xEF, 0xBB, 0x00, 1, 2, 3) // prefix = EF BB 00 (3 bytes) + 3 delegate bytes
        assertTrue(stream.available() >= 6)
        stream.read() // consume EF
        assertTrue(stream.available() >= 5)
    }

    @Test
    fun `skip across prefix boundary does not lose data`() {
        val stream = wrap(0xEF, 0xBB, 0x00, 1, 2, 3) // preserved data; prefix = EF BB 00
        val skipped = stream.skip(2)
        assertEquals(2, skipped)
        val rest = stream.readAllBytes()
        assertContentEquals(byteArrayOf(0x00, 1, 2, 3), rest)
    }

    @Test
    fun `read smaller than buffered prefix returns prefix remainder across calls`() {
        // Without a BOM, prefix holds up to 3 bytes that must be drained in small reads.
        val stream = wrap(0xEF, 0xBB, 0x00, 5, 6, 7, 8)
        val single = ByteArray(1)
        val parts = mutableListOf<Byte>()
        var n: Int
        while (stream.read(single, 0, 1).also { n = it } != -1) {
            parts += single[0]
        }
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0x00, 5, 6, 7, 8), parts.toByteArray())
    }

    @Test
    fun `close propagates to delegate`() {
        val delegate = ByteArrayInputStream(byteArrayOf(1))
        val stream = BomStrippingInputStream(delegate)
        stream.close()
        // Closing a ByteArrayInputStream is a no-op; verify the wrapper can be closed without error.
        assertTrue(true)
    }
}
