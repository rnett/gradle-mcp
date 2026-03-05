package dev.rnett.gradle.mcp

import java.io.File
import kotlin.test.Test

class HtmlConverterTest {

    private val markdownService = DefaultMarkdownService()
    private val htmlConverter = HtmlConverter(markdownService)
    private val testResourcesDir = File("src/test/resources/docs-html-samples")
    private val expectedDir = File("src/test/resources/docs-md-expected")
    private val harness = SnapshotHarness(testResourcesDir, expectedDir)

    private fun readSample(path: String): String {
        return File(testResourcesDir, path).readText()
    }

    private fun testSnapshot(relPath: String, kind: DocsKind) {
        val html = readSample(relPath)
        val actual = htmlConverter.convert(html, kind)
        harness.assertSnapshot(relPath.replace(".html", ".md.txt"), actual)
    }

    @Test
    fun `userguideSnapshot`() {
        testSnapshot("userguide/command_line_interface.html", DocsKind.USERGUIDE)
    }

    @Test
    fun `dslSnapshot`() {
        testSnapshot("dsl/org.gradle.api.Project.html", DocsKind.DSL)
    }

    @Test
    fun `kotlinDslSnapshot`() {
        testSnapshot("kotlin-dsl/gradle/org.gradle/-build-adapter/index.html", DocsKind.KOTLIN_DSL)
    }

    @Test
    fun `javadocSnapshot`() {
        testSnapshot("javadoc/org/gradle/api/Project.html", DocsKind.JAVADOC)
    }

    @Test
    fun `javadocIndexSnapshot`() {
        testSnapshot("javadoc/allclasses-index.html", DocsKind.JAVADOC)
    }

    @Test
    fun `sampleSnapshot`() {
        testSnapshot("samples/sample_building_java_applications.html", DocsKind.SAMPLES)
    }

    @Test
    fun `releaseNotesSnapshot`() {
        testSnapshot("release-notes.html", DocsKind.RELEASE_NOTES)
    }
}
