package dev.rnett.gradle.mcp.gradle

import java.io.Writer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
open class LineEmittingWriter(val lineLogger: (String) -> Unit) : Writer() {
    private val buf = StringBuffer()
    private val lastWasCR = AtomicBoolean(false)

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

            if (lastWasCR.compareAndExchange(true, false)) {
                if (c == '\n') {
                    emitInternal()
                    continue
                } else {
                    emitInternal()
                }
            }

            if (c == '\r') {
                lastWasCR.store(true)
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
