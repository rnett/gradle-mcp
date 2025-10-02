package dev.rnett.gradle.mcp

import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

object UpdateTools {
    private val START = "[//]: # (<<TOOLS_LIST_START>>)\n"
    private val END = "[//]: # (<<TOOLS_LIST_END>>)\n"

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun StringBuilder.appendDetails(summary: String, block: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("<details>")
        appendLine()
        appendLine("<summary>$summary</summary>")
        appendLine()
        block()
        appendLine()
        appendLine("</details>")
    }

    private inline fun <reified T> StringBuilder.appendJson(value: T) {
        appendLine()
        appendLine("```json")
        appendLine(json.encodeToString(value))
        appendLine("```")
        appendLine()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val file = args.getOrNull(0)
        val verify = args.contains("--verify")

        val server = Application(args).createServer()

        val text = buildString {
            server.tools.forEach {
                appendLine("### ${it.key}")
                if (it.value.tool.title != null) {
                    appendLine(it.value.tool.title)
                    appendLine()
                }
                appendLine()
                appendLine(it.value.tool.description)
                appendDetails("Input schema") {
                    appendJson(it.value.tool.inputSchema)
                }
                appendLine()
                if (it.value.tool.outputSchema != null) {
                    appendDetails("Output schema") {
                        appendJson(it.value.tool.outputSchema)
                    }
                }
                appendLine()
            }
        }

        if (file != null) {
            val path = Path(file)

            val existing = path.readText()
            val before = existing.substringBefore(START)
            val after = existing.substringAfter(END, "")

            val newText = "$before$START\n$text\n$END$after"
            if (verify) {
                if (newText != existing) {
                    throw IllegalStateException("Existing tools description did not match, update tools description")
                }
            } else {
                path.writeText(newText)
            }
        } else {
            println(text)
        }

    }
}