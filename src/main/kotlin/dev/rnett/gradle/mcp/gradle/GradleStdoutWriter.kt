package dev.rnett.gradle.mcp.gradle

abstract class GradleStdoutWriter(lineLogger: (String) -> Unit) : LineEmittingWriter(lineLogger) {
    companion object {
        private const val SCAN_MARKER = "[MCP-BUILD-SCAN] "
    }

    abstract fun onScanPublication(url: String)

    override fun onLine(line: String) {
        if (line.startsWith(SCAN_MARKER)) {
            val url = line.removePrefix(SCAN_MARKER).trim()
            if (url.isNotEmpty()) {
                onScanPublication(url)
            }
        }
    }

    override fun onLineOrFlush(current: String) {
        // Nothing needed here anymore
    }
}
