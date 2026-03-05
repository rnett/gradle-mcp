package dev.rnett.gradle.mcp

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentExtractorServiceTest {

    @Test
    fun `ensureProcessed extracts and converts docs`() = runTest {
        val tempDir = Files.createTempDirectory("gradle-mcp-test-extract")
        try {
            val environment = GradleMcpEnvironment(tempDir)

            val zipPath = tempDir.resolve("test-docs.zip")
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                // Userguide
                zos.putNextEntry(ZipEntry("docs/userguide/index.html"))
                zos.write("<h1>Userguide</h1>".toByteArray())
                zos.closeEntry()

                // DSL
                zos.putNextEntry(ZipEntry("docs/dsl/Project.html"))
                zos.write("<h1>Project</h1>".toByteArray())
                zos.closeEntry()

                // Kotlin DSL
                zos.putNextEntry(ZipEntry("docs/kotlin-dsl/Project.html"))
                zos.write("<h1>Kotlin Project</h1>".toByteArray())
                zos.closeEntry()

                // Release notes
                zos.putNextEntry(ZipEntry("docs/release-notes.html"))
                zos.write("<h1>Release Notes</h1>".toByteArray())
                zos.closeEntry()

                // Sample
                zos.putNextEntry(ZipEntry("docs/samples/sample_foo.html"))
                zos.write("<h1>Sample Foo</h1>".toByteArray())
                zos.closeEntry()

                // Nested sample zip
                val nestedBaos = ByteArrayOutputStream()
                ZipOutputStream(nestedBaos).use { nzos ->
                    nzos.putNextEntry(ZipEntry("build.gradle"))
                    nzos.write("println 'hello'".toByteArray())
                    nzos.closeEntry()
                }
                zos.putNextEntry(ZipEntry("docs/samples/zips/sample_foo-kotlin-dsl.zip"))
                zos.write(nestedBaos.toByteArray())
                zos.closeEntry()

                // Binary (should be included now)
                zos.putNextEntry(ZipEntry("docs/userguide/image.png"))
                zos.write(byteArrayOf(1, 2, 3))
                zos.closeEntry()
            }
            Files.write(zipPath, baos.toByteArray())

            val downloader = mockk<DistributionDownloaderService>()
            coEvery { downloader.downloadDocs("9.4.0") } returns zipPath

            val markdownService = DefaultMarkdownService()
            val htmlConverter = HtmlConverter(markdownService)
            val service = DefaultContentExtractorService(downloader, htmlConverter, environment)

            service.ensureProcessed("9.4.0")

            val convertedDir = tempDir.resolve("cache/gradle-docs/9.4.0/converted")
            assertTrue(convertedDir.resolve(".done").exists(), "Done marker should exist")

            // Check extractions
            assertEquals("# Userguide", convertedDir.resolve("userguide/index.md").readText().trim())
            assertEquals("# Project", convertedDir.resolve("dsl/Project.md").readText().trim())
            assertEquals("# Kotlin Project", convertedDir.resolve("kotlin-dsl/Project.md").readText().trim())
            assertEquals("# Release Notes", convertedDir.resolve("release-notes.md").readText().trim())

            // Sample description
            assertEquals("# Sample Foo", convertedDir.resolve("samples/sample_foo/README.md").readText().trim())

            // Nested sample zip
            assertEquals("println 'hello'", convertedDir.resolve("samples/sample_foo/kotlin-dsl/build.gradle").readText().trim())

            // Binary included
            assertTrue(convertedDir.resolve("userguide/image.png").exists(), "Image should be extracted")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
