package dev.rnett.gradle.mcp

private val println = ProgressReporter { progress, total, message ->
    val prefix = if (total == null) String.format("%.2f", progress) else ((100 * progress / total).toInt().toString() + "%")
    println("$prefix $message")
}
val ProgressReporter.Companion.PRINTLN: ProgressReporter get() = println