package dev.rnett.gradle.mcp.repl

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplOutputStreamTest {

    @Test
    fun `test single byte write`() {
        val lines = mutableListOf<String>()
        val out = ReplOutputStream { lines.add(it) }
        out.write('a'.code)
        out.write('b'.code)
        out.write('\n'.code)
        assertEquals(listOf("ab"), lines)
    }

    @Test
    fun `test byte array write`() {
        val lines = mutableListOf<String>()
        val out = ReplOutputStream { lines.add(it) }
        out.write("hello\nworld\n".toByteArray())
        assertEquals(listOf("hello", "world"), lines)
    }

    @Test
    fun `test byte array write without trailing newline`() {
        val lines = mutableListOf<String>()
        val out = ReplOutputStream { lines.add(it) }
        out.write("hello\nworld".toByteArray())
        assertEquals(listOf("hello"), lines)
        out.flush()
        assertEquals(listOf("hello", "world"), lines)
    }

    @Test
    fun `test carriage return handling`() {
        val lines = mutableListOf<String>()
        val out = ReplOutputStream { lines.add(it) }
        out.write("hello\r\nworld\r\n".toByteArray())
        assertEquals(listOf("hello", "world"), lines)
    }

    @Test
    fun `test split multi-byte character`() {
        val lines = mutableListOf<String>()
        val out = ReplOutputStream { lines.add(it) }
        val emoji = "😊" // UTF-8: F0 9F 98 8A
        val bytes = emoji.toByteArray()

        out.write(bytes, 0, 2)
        out.write(bytes, 2, 2)
        out.write('\n'.code)

        assertEquals(listOf("😊"), lines)
    }
}
