package dev.rnett.gradle.mcp.repl

@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
object GlobalResponder {
    private val instance = kotlin.concurrent.atomics.AtomicReference<Any?>(null)

    @JvmStatic
    fun setInstance(value: Any?) {
        instance.store(value)
    }

    @JvmStatic
    val responder: Responder
        get() = instance.load() as Responder
}
