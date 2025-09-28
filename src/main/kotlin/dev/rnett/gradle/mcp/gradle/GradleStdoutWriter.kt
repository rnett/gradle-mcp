package dev.rnett.gradle.mcp.gradle

abstract class GradleStdoutWriter(val checkForTosRequest: Boolean, lineLogger: (String) -> Unit) : LineEmittingWriter(lineLogger) {
    companion object {
        val TOS_ACCEPT_REGEX = Regex("Publishing a Build Scan to (.+) requires accepting the Gradle Terms of Use defined at (.+). Do you accept these terms\\?", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val SCAN_LINES = setOf(
            "Publishing build scan...",
            "Publishing Build Scan to Develocity..."
        )
    }

    abstract fun onScansTosRequest(tosAcceptRequest: GradleScanTosAcceptRequest)
    abstract fun onScanPublication(url: String)

    private var lastLineWasPublishNotification: Boolean = false

    override fun onLine(line: String) {
        if (lastLineWasPublishNotification) {
            onScanPublication(line.trim())
            lastLineWasPublishNotification = false
        }
        if (line in SCAN_LINES) {
            lastLineWasPublishNotification = true
        }
    }

    override fun onLineOrFlush(current: String) {
        if (checkForTosRequest) {
            TOS_ACCEPT_REGEX.find(current)?.let {
                onScansTosRequest(GradleScanTosAcceptRequest(it.groupValues[0], it.groupValues[1], it.groupValues[2]))
            }
        }
    }
}