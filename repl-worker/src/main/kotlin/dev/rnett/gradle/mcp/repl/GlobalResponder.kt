package dev.rnett.gradle.mcp.repl

object GlobalResponder {
    private var instance: Any? = null

    @JvmStatic
    fun setInstance(value: Any?) {
        instance = value
    }

    @JvmStatic
    val responder: Responder
        get() = instance as Responder
}
