package dev.rnett.gradle.mcp.repl

import java.util.*

@Suppress("unused")
class Responder(val send: (ReplResponse) -> Unit, classLoader: ClassLoader) {
    val resultRenderer = ResultRenderer(classLoader)

    fun render(value: Any?, mime: String? = null) {
        Logger.info(Responder::class, "Responding with $value with mime $mime")
        val data = resultRenderer.renderResult(value, mime)
        send(data)
    }

    fun markdown(md: String) {
        Logger.info(Responder::class, "Responding with markdown: $md")
        send(ReplResponse.Data(md, "text/markdown"))
    }

    fun html(fragment: String) {
        Logger.info(Responder::class, "Responding with html: $fragment")
        send(ReplResponse.Data(fragment, "text/html"))
    }

    fun image(bytes: ByteArray, mime: String = "image/png") {
        Logger.info(Responder::class, "Responding with image with mime $mime")
        val base64 = Base64.getEncoder().encodeToString(bytes)
        send(ReplResponse.Data(base64, mime))
    }
}
