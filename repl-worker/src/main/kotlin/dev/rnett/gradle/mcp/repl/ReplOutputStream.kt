package dev.rnett.gradle.mcp.repl

import java.io.ByteArrayOutputStream
import java.io.OutputStream

class ReplOutputStream(
    private val onLine: (String) -> Unit
) : OutputStream() {
    private val byteBuffer = ByteArrayOutputStream()

    override fun write(b: Int) {
        if (b == '\n'.code) {
            flushLine()
        } else if (b != '\r'.code) {
            byteBuffer.write(b)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var currentOff = off
        val end = off + len
        for (i in off until end) {
            if (b[i] == '\n'.code.toByte()) {
                byteBuffer.write(b, currentOff, i - currentOff)
                flushLine()
                currentOff = i + 1
            } else if (b[i] == '\r'.code.toByte()) {
                byteBuffer.write(b, currentOff, i - currentOff)
                currentOff = i + 1
            }
        }
        if (currentOff < end) {
            byteBuffer.write(b, currentOff, end - currentOff)
        }
    }

    private fun flushLine() {
        if (byteBuffer.size() > 0) {
            onLine(byteBuffer.toString("UTF-8"))
            byteBuffer.reset()
        }
    }

    override fun flush() {
        flushLine()
    }

    override fun close() {
        flushLine()
    }
}
