package dev.rnett.gradle.mcp.dependencies.gradle.docs

import dev.rnett.gradle.mcp.DocsKind
import dev.rnett.gradle.mcp.SnapshotHarness
import org.junit.jupiter.api.Test
import java.io.File

class HtmlConverterTest {

    private val markdownService = DefaultMarkdownService()
    private val htmlConverter = HtmlConverter(markdownService)
    private val testResourcesDir = File("src/test/resources/docs-html-samples")
    private val expectedDir = File("src/test/resources/docs-md-expected")
    private val harness = SnapshotHarness(testResourcesDir, expectedDir)

    private fun readSample(path: String): String {
        return File(testResourcesDir, path).readText()
    }

    fun `test snapshot`(relPath: String, kind: DocsKind) {
        val html = readSample(relPath)
        val actual = htmlConverter.convert(html, kind)
        harness.assertSnapshot(relPath.replace(".html", ".md.txt"), actual)
    }

    @Test
    fun `userguide snapshot`() {
        `test snapshot`("userguide/command_line_interface.html", DocsKind.USERGUIDE)
    }

    @Test
    fun `dsl snapshot`() {
        `test snapshot`("dsl/org.gradle.api.Project.html", DocsKind.DSL)
    }

    @Test
    fun `kotlin dsl snapshot`() {
        `test snapshot`("kotlin-dsl/gradle/org.gradle/-build-adapter/index.html", DocsKind.KOTLIN_DSL)
    }

    @Test
    fun `javadoc snapshot`() {
        `test snapshot`("javadoc/org/gradle/api/Project.html", DocsKind.JAVADOC)
    }

    @Test
    fun `javadoc index snapshot`() {
        `test snapshot`("javadoc/allclasses-index.html", DocsKind.JAVADOC)
    }

    @Test
    fun `sample snapshot`() {
        `test snapshot`("samples/sample_building_java_applications.html", DocsKind.SAMPLES)
    }

    @Test
    fun `release notes snapshot`() {
        `test snapshot`("release-notes.html", DocsKind.RELEASE_NOTES)
    }
}
