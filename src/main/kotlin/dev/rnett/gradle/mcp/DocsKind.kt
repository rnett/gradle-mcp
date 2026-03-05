package dev.rnett.gradle.mcp

enum class DocsKind(val dirName: String) {
    USERGUIDE("userguide"),
    DSL("dsl"),
    KOTLIN_DSL("kotlin-dsl"),
    JAVADOC("javadoc"),
    SAMPLES("samples"),
    RELEASE_NOTES("release-notes")
}
