package dev.rnett.gradle.mcp.repl

import kotlin.reflect.KClass

object Logger {
    fun log(level: ReplResponse.Logging.Level, cls: KClass<*>, message: String) {
        ReplWorker.sendResponse(ReplResponse.Logging(cls.qualifiedName!!, level, message))
    }

    fun error(cls: KClass<*>, message: String) {
        log(ReplResponse.Logging.Level.ERROR, cls, message)
    }

    fun error(cls: KClass<*>, message: String, exception: Throwable) {
        log(ReplResponse.Logging.Level.ERROR, cls, message + "\n" + exception.stackTraceToString())
    }

    fun warning(cls: KClass<*>, message: String) {
        log(ReplResponse.Logging.Level.WARN, cls, message)
    }

    fun warning(cls: KClass<*>, message: String, exception: Throwable) {
        log(ReplResponse.Logging.Level.WARN, cls, message + "\n" + exception.stackTraceToString())
    }

    fun info(cls: KClass<*>, message: String) {
        log(ReplResponse.Logging.Level.INFO, cls, message)
    }

    fun debug(cls: KClass<*>, message: String) {
        log(ReplResponse.Logging.Level.DEBUG, cls, message)
    }

    fun debug(cls: KClass<*>, message: String, e: Throwable) {
        log(ReplResponse.Logging.Level.DEBUG, cls, message + "\n" + e.stackTraceToString())
    }

}