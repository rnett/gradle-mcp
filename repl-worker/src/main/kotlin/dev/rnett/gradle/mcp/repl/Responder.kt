package dev.rnett.gradle.mcp.repl

interface Responder {
    fun respond(value: Any?, mime: String? = null)
    fun markdown(md: String)
    fun html(fragment: String)
    fun image(bytes: ByteArray, mime: String = "image/png")
}
