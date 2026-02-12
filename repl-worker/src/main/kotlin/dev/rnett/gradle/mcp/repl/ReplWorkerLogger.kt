package dev.rnett.gradle.mcp.repl

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.ConcurrentHashMap

class ReplWorkerServiceProvider : SLF4JServiceProvider {
    private val loggerFactory: ILoggerFactory = ReplWorkerLoggerFactory()
    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
    private val mdcAdapter: MDCAdapter = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory

    override fun getMarkerFactory(): IMarkerFactory = markerFactory

    override fun getMDCAdapter(): MDCAdapter = mdcAdapter

    override fun getRequestedApiVersion(): String = "2.0.99"

    override fun initialize() {
    }
}

class ReplWorkerLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, ReplWorkerLogger>()

    override fun getLogger(name: String): ReplWorkerLogger {
        return loggers.getOrPut(name) { ReplWorkerLogger(name) }
    }
}

class ReplWorkerLogger(private val name: String) : LegacyAbstractLogger() {

    override fun getName(): String = name

    override fun isTraceEnabled(): Boolean = true
    override fun isDebugEnabled(): Boolean = true
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true

    private fun log(level: String, msg: String, t: Throwable?) {
        ReplWorker.sendResponse(
            ReplResponse.Log(
                level = level,
                logger = name,
                message = msg,
                throwable = t?.stackTraceToString()
            )
        )
    }

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: org.slf4j.event.Level,
        marker: org.slf4j.Marker?,
        msg: String,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        val message = if (arguments == null || arguments.isEmpty()) {
            msg
        } else {
            org.slf4j.helpers.MessageFormatter.arrayFormat(msg, arguments).message
        }
        log(level.toString(), message, throwable)
    }
}
