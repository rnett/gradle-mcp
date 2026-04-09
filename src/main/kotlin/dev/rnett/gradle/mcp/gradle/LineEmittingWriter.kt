package dev.rnett.gradle.mcp.gradle

import java.io.Writer

/**
 * A [Writer] that buffers characters and emits complete lines (delimited by `\n`, `\r`, or `\r\n`)
 * via the [lineLogger] callback.
 *
 * **Thread-confinement contract:** Each instance is used by exactly one thread — the Gradle Tooling
 * API delivers stdout and stderr on dedicated, per-stream threads, and [flush]/[close] are only
 * called from the build's `finally` block after the blocking Tooling API call returns (so no
 * concurrent [write] is possible). No internal synchronization is needed.
 */
open class LineEmittingWriter(val lineLogger: (String) -> Unit) : Writer() {
    private val buf = StringBuilder()
    private var lastWasCR = false

    protected fun current(): String {
        return buf.toString()
    }

    open fun onLine(line: String) {

    }

    open fun onLineOrFlush(current: String) {

    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        for (i in off..<off + len) {
            val c = cbuf[i]

            if (lastWasCR) {
                lastWasCR = false
                if (c == '\n') {
                    emitInternal()
                    continue
                } else {
                    emitInternal()
                }
            }

            if (c == '\r') {
                lastWasCR = true
            } else if (c == '\n') {
                emitInternal()
            } else {
                buf.append(c)
            }
        }
    }

    override fun flush() {
        onLineOrFlush(buf.toString())
    }

    override fun close() {
        emitInternal()
    }

    private fun emitInternal() {
        val str = buf.toString()
        if (str.isNotEmpty()) {
            onLine(str)
            onLineOrFlush(str)
            lineLogger(str)
        }
        buf.setLength(0)
    }
}
