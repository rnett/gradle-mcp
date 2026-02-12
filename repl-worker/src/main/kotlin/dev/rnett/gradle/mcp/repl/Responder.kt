package dev.rnett.gradle.mcp.repl

import org.slf4j.LoggerFactory
import java.util.Base64

class Responder(val send: (ReplResponse) -> Unit, val classLoader: ClassLoader) {
    val resultRenderer = ResultRenderer(classLoader)

    companion object {
        @PublishedApi
        internal val LOGGER by lazy { LoggerFactory.getLogger(Responder::class.java) }
    }

    fun render(value: Any?, mime: String? = null) {
        LOGGER.info("Responding with {} with mime {}", value, mime)
        val data = resultRenderer.renderResult(value, mime)
        send(data)
    }

    fun markdown(md: String) {
        LOGGER.info("Responding with markdown: {}", md)
        send(ReplResponse.Data(md, "text/markdown"))
    }

    fun html(fragment: String) {
        LOGGER.info("Responding with html: {}", fragment)
        send(ReplResponse.Data(fragment, "text/html"))
    }

    fun image(bytes: ByteArray, mime: String = "image/png") {
        LOGGER.info("Responding with image with mime {}", mime)
        val base64 = Base64.getEncoder().encodeToString(bytes)
        send(ReplResponse.Data(base64, mime))
    }
}
