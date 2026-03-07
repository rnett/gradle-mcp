package dev.rnett.gradle.mcp.logging

import ch.qos.logback.core.status.OnPrintStreamStatusListenerBase
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream

class FileStatusListener : OnPrintStreamStatusListenerBase() {

    var filename: String? = null
    private var ps: PrintStream? = null

    override fun start() {
        if (filename == null) {
            val logDir = context?.getProperty("GRADLE_MCP_LOG_DIR")
                ?: System.getProperty("GRADLE_MCP_LOG_DIR")
                ?: System.getenv("GRADLE_MCP_LOG_DIR")
                ?: "${System.getProperty("user.home")}/.gradle-mcp/logs"
            filename = "$logDir/logback-status.log"
        }
        super.start()
    }

    override fun stop() {
        ps?.close()
        ps = null
        super.stop()
    }

    override fun getPrintStream(): PrintStream {
        return ps ?: synchronized(this) {
            ps ?: run {
                val path = filename!!
                val logFile = File(path)
                try {
                    logFile.parentFile?.mkdirs()
                    PrintStream(FileOutputStream(logFile, true), true).also { ps = it }
                } catch (e: Exception) {
                    addError("Failed to open status log file [$path] for writing: ${e.message}", e)
                    System.err
                }
            }
        }
    }
}
