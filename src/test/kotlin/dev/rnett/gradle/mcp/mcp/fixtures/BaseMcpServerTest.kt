package dev.rnett.gradle.mcp.mcp.fixtures

import dev.rnett.gradle.mcp.gradle.GradleProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseMcpServerTest {

    @TempDir
    lateinit var tempDir: Path

    lateinit var server: McpServerFixture
    protected val provider = mockk<GradleProvider>()

    @BeforeTest
    fun setup() = runTest {
        server = McpServerFixture(provider)
        server.start()
    }

    @AfterTest
    fun cleanup() = runTest {
        server.close()
    }
}