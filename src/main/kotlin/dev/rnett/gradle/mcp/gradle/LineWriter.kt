package dev.rnett.gradle.mcp.gradle

import java.io.Writer

class LineEmittingWriter(private val onLine: (String) -> Unit) : Writer() {
    private val buf = StringBuilder()
    private var lastWasCR = false

    @Synchronized
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        for (i in off..<off + len) {
            val c = cbuf[i]

            if (lastWasCR) {
                if (c == '\n') {
                    emit()
                    lastWasCR = false
                    continue
                } else {
                    emit()
                    lastWasCR = false
                }
            }

            if (c == '\r') {
                lastWasCR = true
            } else if (c == '\n') {
                emit()
            } else {
                buf.append(c)
            }
        }
    }

    @Synchronized
    override fun flush() {
        // no-op
    }

    @Synchronized
    override fun close() {
        emit()
    }

    private fun emit() {
        val str = buf.toString()
        if (str.isNotEmpty()) {
            onLine(str)
        }
        buf.setLength(0)
    }
}