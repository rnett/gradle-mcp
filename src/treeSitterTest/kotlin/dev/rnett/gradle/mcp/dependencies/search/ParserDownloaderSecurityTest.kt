package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.GradleMcpEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ParserDownloaderSecurityTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        mockkObject(TreeSitterUtils)
        every { TreeSitterUtils.platformKey() } returns "linux-x86_64"
        every { TreeSitterUtils.getCSymbol(any()) } answers { it.invocation.args[0] as String }
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(TreeSitterUtils)
    }

    private fun getLibName(name: String): String {
        val osName = System.getProperty("os.name").lowercase()
        val prefix = if (osName.contains("win")) "" else "lib"
        val ext = when {
            osName.contains("mac") -> "dylib"
            osName.contains("win") -> "dll"
            else -> "so"
        }
        return "${prefix}tree_sitter_$name.$ext"
    }

    @Test
    fun `test corrupted cache triggers re-download`() = runTest(timeout = 10.minutes) {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        val version = "1.0.0"

        val correctContent = "correct library content"
        val correctHash = MessageDigest.getInstance("SHA-256")
            .digest(correctContent.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val manifestJson = """
            {
                "version": "$version",
                "platforms": {
                    "linux-x86_64": {
                        "url": "http://example.com/bundle.tar.zst",
                        "sha256": "any-bundle-hash",
                        "size": 100
                    }
                },
                "languages": {
                    "java": {
                        "group": "java",
                        "sha256": "$correctHash",
                        "size": 50
                    }
                },
                "groups": {}
            }
        """.trimIndent()

        var downloadAttempted = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("parsers.json")) {
                respond(manifestJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            } else if (request.url.encodedPath.endsWith("bundle.tar.zst")) {
                downloadAttempted = true
                respond(ByteArray(0), HttpStatusCode.OK)
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine)
        val downloader = ParserDownloader(httpClient, version, cacheDirOverride = tempDir.resolve("mcp/cache/tree-sitter-language-pack/v$version/libs"))

        // 1. Setup corrupted file
        val libsDir = tempDir.resolve("mcp/cache/tree-sitter-language-pack/v$version/libs")
        libsDir.createDirectories()
        val libPath = libsDir.resolve(getLibName("java"))
        libPath.writeText("corrupted/malicious content")

        // 2. Try to ensure language. It should detect hash mismatch and try to download.
        assertFailsWith<Exception> {
            downloader.ensureLanguage("java")
        }

        assertTrue(downloadAttempted, "Downloader should have attempted to download the bundle after detecting corrupted cache")
    }

    @Test
    fun `test valid cache returns immediately`() = runTest(timeout = 10.minutes) {
        val env = GradleMcpEnvironment(tempDir.resolve("mcp"))
        val version = "1.0.0"

        val content = "valid library content"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val manifestJson = """
            {
                "version": "$version",
                "platforms": {
                    "linux-x86_64": {
                        "url": "http://example.com/bundle.tar.zst",
                        "sha256": "any-bundle-hash",
                        "size": 100
                    }
                },
                "languages": {
                    "java": {
                        "group": "java",
                        "sha256": "$hash",
                        "size": 50
                    }
                },
                "groups": {}
            }
        """.trimIndent()

        var bundleDownloadAttempted = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("parsers.json")) {
                respond(manifestJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            } else if (request.url.encodedPath.endsWith("bundle.tar.zst")) {
                bundleDownloadAttempted = true
                respond(ByteArray(0), HttpStatusCode.OK)
            } else {
                respond("", HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine)
        val downloader = ParserDownloader(httpClient, version, cacheDirOverride = tempDir.resolve("mcp/cache/tree-sitter-language-pack/v$version/libs"))

        val libsDir = tempDir.resolve("mcp/cache/tree-sitter-language-pack/v$version/libs")
        libsDir.createDirectories()
        val libPath = libsDir.resolve(getLibName("java"))
        libPath.writeText(content)

        val result = downloader.ensureLanguage("java")

        assertTrue(result.exists())
        assertTrue(!bundleDownloadAttempted, "Should NOT have attempted to download the bundle for valid cache")
    }
}
