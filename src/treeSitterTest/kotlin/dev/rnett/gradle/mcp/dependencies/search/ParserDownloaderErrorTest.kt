package dev.rnett.gradle.mcp.dependencies.search

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
import kotlin.test.assertFailsWith

class ParserDownloaderErrorTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        mockkObject(TreeSitterUtils)
        every { TreeSitterUtils.platformKey() } returns "linux-x86_64"
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(TreeSitterUtils)
    }

    @Test
    fun `test manifest fetch failure`() = runTest {
        val mockEngine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val downloader = ParserDownloader(HttpClient(mockEngine), "1.0.0", cacheDirOverride = tempDir)
        assertFailsWith<Exception> {
            downloader.ensureLanguage("java")
        }
    }

    @Test
    fun `test unknown language`() = runTest {
        val manifestJson = """{"version": "1.0.0", "platforms": {}, "languages": {}, "groups": {}}"""
        val mockEngine = MockEngine { respond(manifestJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
        val downloader = ParserDownloader(HttpClient(mockEngine), "1.0.0", cacheDirOverride = tempDir)
        assertFailsWith<IllegalStateException> {
            downloader.ensureLanguage("unknown")
        }
    }

    @Test
    fun `test missing platform bundle`() = runTest {
        val manifestJson = """
            {
                "version": "1.0.0",
                "platforms": {},
                "languages": {
                    "java": {"group": "java", "sha256": "h", "size": 1}
                },
                "groups": {}
            }
        """.trimIndent()
        val mockEngine = MockEngine { respond(manifestJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
        val downloader = ParserDownloader(HttpClient(mockEngine), "1.0.0", cacheDirOverride = tempDir)
        assertFailsWith<IllegalStateException> {
            downloader.ensureLanguage("java")
        }
    }

    @Test
    fun `test checksum mismatch`() = runTest {
        val manifestJson = """
            {
                "version": "1.0.0",
                "platforms": {
                    "linux-x86_64": {"url": "http://e.com/b.tar.zst", "sha256": "wrong-hash", "size": 1}
                },
                "languages": {
                    "java": {"group": "java", "sha256": "h", "size": 1}
                },
                "groups": {}
            }
        """.trimIndent()
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("parsers.json")) {
                respond(manifestJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            } else {
                respond(ByteArray(10), HttpStatusCode.OK)
            }
        }
        val downloader = ParserDownloader(HttpClient(mockEngine), "1.0.0", cacheDirOverride = tempDir)
        assertFailsWith<Exception> {
            downloader.ensureLanguage("java")
        }
    }
}
